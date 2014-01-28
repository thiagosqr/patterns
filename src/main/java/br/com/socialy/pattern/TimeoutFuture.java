package br.com.socialy.pattern;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TimeoutFuture<V> implements Future<V>{
	
	private Future<V> future;
	
	private CircuitBraker breaker;
	
	public TimeoutFuture(Future<V> future, CircuitBraker breaker) {
		super();
		this.future = future;
		this.breaker = breaker;
	}

	public V get() throws InterruptedException, ExecutionException, CancellationException {
		
		V result = null;
		
		try {
			
			result = future.get();
			handleSuccess();
			
		}catch (InterruptedException ie) {
			handleFailure();
			throw ie;
		}catch (ExecutionException ee) {
			handleFailure();
			throw ee;
		}catch (CancellationException ce) {
			handleFailure();
			throw ce;
		}
		
		return result;

	}

	public boolean cancel(boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}

	public boolean isCancelled() {
		return future.isCancelled();
	}

	public boolean isDone() {
		return future.isDone();
	}

	public V get(long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException, CancellationException {
		
		V result = null;
		
		try {
			
			result = future.get(timeout,unit);
			handleSuccess();
			
		}catch (TimeoutException toe) {
			handleFailure();
			throw toe;
		}catch (InterruptedException ie) {
			handleFailure();
			throw ie;
		}catch (ExecutionException ee) {
			handleFailure();
			throw ee;
		}catch (CancellationException ce) {
			handleFailure();
			throw ce;
		}
		
		return result;

		
		
	}

	private void handleFailure(){
		
		future.cancel(true);
		
		if(breaker.isHalfOpen()){
			breaker.breakCircuit();
		}else{
			
			if(breaker.runningThreads.get(future) != null){
				breaker.addFailure();
			}
					
		}
		
		breaker.runningThreads.remove(future);
	}
	
	private void handleSuccess(){
	
		breaker.runningThreads.remove(future);
		
		breaker.resetFailureCount();
		
		if(breaker.isHalfOpen()){
			breaker.close();
		}

	}
	
}
