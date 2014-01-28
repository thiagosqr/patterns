package br.com.socialy.pattern;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import br.com.socialy.util.CircuitBrakerStates;
import br.com.socialy.util.CircuitBreakerMessages;
import br.com.socialy.util.CircuitBreakerOpenException;
import br.com.socialy.util.Duration;

/**
 * 
 * Fornece funcionalidade de quebra de circuito para garantir establidade</br> e
 * impedir que falhas sejam propagadas em integra��es de sistemas.</br></br>
 * 
 * Os tr�s poss�veis estados s�o:</br></br>
 * 
 * <b>Fechado</b></br> Exce��es ou timeouts de chamadas increment�o um contador
 * de falhas</br> Chamadas com sucesso reiniciam o contador de falhas</br>
 * Quando a quantidade de falhas chega a um limite o circuito e
 * quebrado</br></br>
 * 
 * <b>Aberto</b></br> Todas chamadas levantam uma exce��o do tipo
 * CircuitBreakerOpenException</br> Ap�s o tempo de reinicio, o circuito entra
 * no estado meio aberto</br></br>
 * 
 * <b>Meio Aberto</b></br> A primeira chamada � liberada para execu��o</br>
 * Todas outras chamadas falham com um exce��o CircuitBreakerOpenException</br>
 * Se a primeira chamada finalizar sem erros o circuito � fechado</br> Se a
 * primeira chamada falhar, o circuito � aberto e um novo rein�cio �
 * agendado</br>
 * 
 * @author thiago-rs - https://github.com/thiagosqr <br>
 * <br>
 * 
 * 
 */
public class CircuitBraker {

	private CircuitBrakerStates state;

	/**
	 * Configura��es do Breaker
	 */
	private int maxFalhas;
	private int contadorFalhas = 0;
	private Duration callTimeout;
	private Duration resetTimeout;

	/**
	 * Configura��es do Pool de Threads
	 */
	private ExecutorService threadPool;
	private ScheduledExecutorService agendador;
	private LinkedBlockingQueue<Runnable> fila;
	private int threadIdleCount;
	private int threadPoolSize;
	private int queueSize;
	private ScheduledFuture<?> sf;

	protected Map<Future<?>, Long> runningThreads = null;
	
	private Logger log = Logger.getLogger(CircuitBraker.class);

	/**
	 * Constr�i um novo circuito
	 * 
	 * @param maxFailures
	 *            - Quantidade m�xima de falhas
	 * @param callTimeout
	 *            - Tempo m�xmo da chamada at� ser considerada uma falha por
	 *            tempo
	 * @param resetTimeout
	 *            - Tempo para rein�cio ap�s total de falhas
	 * @param threadIdleCount
	 *            - Quantidade de Threads inativas
	 * @param threadPoolSize
	 *            - Tamanho m�ximo do pool de Threads. No caso de requisi��es
	 *            HTTP deve ser inferior ao n�mero de Threads dispon�veis no
	 *            servidor de aplica��o
	 * @param queueSize
	 *            - Tamanho m�ximo da fila. Para Threads que n�o podem ser
	 *            atendidas pelo Pool.
	 */
	public CircuitBraker(int maxFailures, Duration callTimeout,
			Duration resetTimeout, int threadIdleCount, int threadPoolSize,
			int queueSize) {

		this.maxFalhas = maxFailures;
		this.callTimeout = callTimeout;
		this.resetTimeout = resetTimeout;
		this.threadIdleCount = threadIdleCount;
		this.threadPoolSize = threadPoolSize;
		this.queueSize = queueSize;

		this.runningThreads = new HashMap<Future<?>, Long>();

		fila = new LinkedBlockingQueue<Runnable>(queueSize);
		this.agendador = Executors.newScheduledThreadPool(2);

		startThreadMonitor();
		close();

	}

	/**
	 * Dispara a opera��o em uma nova Thread. Ao chamar o m�todo
	 * {@link java.util.concurrent.Future#get()} o mesmo bloquea a Thread at�
	 * obter um resultado
	 * 
	 * @param callout
	 *            - Opera��o a ser executada
	 * @return {@link java.util.concurrent.Future}
	 * @throws CircuitBreakerOpenException
	 *             Se o circuito estiver no estado aberto, ou meio aberto </br>
	 *             com uma chamada pendente de execu��o.
	 */
	public <T> TimeoutFuture<T> call(Callable<T> callout)
			throws CircuitBreakerOpenException, RejectedExecutionException {

		TimeoutFuture<T> f = null;

		switch (state) {

			case CLOSED:
	
				f = dispatch(callout);
				break;
	
			case HALFOPEN:
	
				f = allowOneThread(callout);
				break;
	
			case OPEN:
				throw new CircuitBreakerOpenException();
		}

		return f;

	}
	
	private synchronized <T> TimeoutFuture<T> allowOneThread(Callable<T> callout) throws RejectedExecutionException {

		startNewThreadPool(CircuitBrakerStates.HALFOPEN);
		TimeoutFuture<T> f = null;
		ThreadPoolExecutor tex = (ThreadPoolExecutor) threadPool;

		if(runningThreads.size() == 0){
			startThreadMonitor();
			f = dispatch(callout);
		} else {
			log.info(CircuitBreakerMessages.MEIO_ABERTO_SOMENTE_UMA_THREAD);
			throw new CircuitBreakerOpenException();
		}

		return f;

	}

	
	
	protected synchronized void close() {
		startNewThreadPool(CircuitBrakerStates.CLOSED);
		resetFailureCount();
		startThreadMonitor();
		log.info(CircuitBreakerMessages.FECHADO_OK);
	}

	protected synchronized void breakCircuit() {

		if (CircuitBrakerStates.CLOSED == state
				|| CircuitBrakerStates.HALFOPEN == state) {

			cancelAllThreads();

			try {

				agendador.schedule(new Runnable() {
					public void run() {
						halfOpen();
					}
				}, resetTimeout.getTime(), resetTimeout.getUnit());

				fila.clear();

				ThreadPoolExecutor tex = (ThreadPoolExecutor) threadPool;
				tex.purge();
				tex.shutdownNow();
				threadPool = null;
				sf = null;
				fila = null;

			} catch (Exception e) {
				e.printStackTrace();
			} finally {	
				
				if(state == CircuitBrakerStates.CLOSED){
					log.error(CircuitBreakerMessages.FECHADO_NUM_MAX_FALHAS);
				}else if(state == CircuitBrakerStates.HALFOPEN){
					log.error(CircuitBreakerMessages.MEIO_ABERTO_ERRO_PERSISTENTE);
				}
				
				state = CircuitBrakerStates.OPEN;
			}
		}
	}

	private void cancelAllThreads() {

		for (Iterator<Future<?>> iterator = runningThreads.keySet().iterator(); iterator
				.hasNext();) {
			Future<?> f = iterator.next();
			f.cancel(true);
		}
		runningThreads.clear();
		sf.cancel(true);
	}

	private void halfOpen() {
		log.info(CircuitBreakerMessages.MEIO_ABERTO_REINICIO);
		state = CircuitBrakerStates.HALFOPEN;
	}

	protected boolean isHalfOpen() {
		return state == CircuitBrakerStates.HALFOPEN;
	}

	public void addFailure() {

		if (state == CircuitBrakerStates.CLOSED) {

			if (++contadorFalhas >= maxFalhas)
				breakCircuit();
		}

	}

	protected void resetFailureCount() {
		contadorFalhas = 0;
	}

	private <T> TimeoutFuture<T> dispatch(Callable<T> callout) throws RejectedExecutionException {
		Future<T> f = threadPool.submit(callout);
		runningThreads.put(f, System.nanoTime());
		return new TimeoutFuture<T>(f, this);
	}

	public Duration getCallTimeout() {
		return this.callTimeout;
	}

	/**
	 * Obtem o estado atual do circuito
	 * 
	 * @return {@link br.com.socialy.util.CircuitBrakerStates}
	 */
	public CircuitBrakerStates getState() {
		return state;
	}

	private void startNewThreadPool(CircuitBrakerStates state) {

		if (this.threadPool == null) {

			fila = new LinkedBlockingQueue<Runnable>(queueSize);

			this.threadPool = new ThreadPoolExecutor(threadIdleCount,
					threadPoolSize, 30, TimeUnit.SECONDS, fila);
		}

		this.state = state;
	}

	private void checkOnRunningThreads() {

		for (Iterator<Future<?>> iterator = runningThreads.keySet().iterator(); iterator
				.hasNext();) {
			Future<?> f = iterator.next();

			long nanos = callTimeout.getUnit().toNanos(callTimeout.getTime());
			long timeElapsed = System.nanoTime() - runningThreads.get(f);

			if (timeElapsed > nanos) {
				f.cancel(true);
				iterator.remove();
				
				if (this.getState() == CircuitBrakerStates.HALFOPEN) {
					breakCircuit();
				}else{
					addFailure();
				}

			}

		}

	}
	
	private void startThreadMonitor() {

		if (sf == null) {

			sf = agendador.scheduleAtFixedRate(new Runnable() {
				public void run() {
					checkOnRunningThreads();
				}
			}, 1, 1, TimeUnit.SECONDS);

		}

	}

}
