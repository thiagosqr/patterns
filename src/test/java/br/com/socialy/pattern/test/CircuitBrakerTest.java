package br.com.socialy.pattern.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import br.com.socialy.pattern.CircuitBraker;
import br.com.socialy.util.CircuitBrakerStates;
import br.com.socialy.util.CircuitBreakerOpenException;
import br.com.socialy.util.Duration;

/**
 * @author thiago-rs - https://github.com/thiagosqr
 *
 */
public class CircuitBrakerTest {
	
	private static CircuitBraker cb;
	
	static int maxFailures = 3;
	static Duration callTimeout  = Duration.create(3, TimeUnit.SECONDS);
	static Duration resetTimeout = Duration.create(3, TimeUnit.SECONDS);
	
	static{

		cb = new CircuitBraker(
				maxFailures, 
				callTimeout,
				resetTimeout,
				3,
				10,
				10);
	}
	
	@Test
	public void testEstadoFechado() {
		
		try {
			
			if(cb.getState() != CircuitBrakerStates.CLOSED){
								
				if(cb.getState() == CircuitBrakerStates.HALFOPEN)
					waitUntilCircuitIs(CircuitBrakerStates.OPEN);
				
				waitUntilCircuitIs(CircuitBrakerStates.HALFOPEN);
				
				cb.call(new Callable<Boolean>() {
				      public Boolean call() {return true;}
				}).get();
			}
			
			Future<String> f = cb.call(new Callable<String>() {
			      public String call() {
			        return shortRunningThread();
			      }
				});

			String result = f.get();
			assertNotNull(result);
			
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	
	@Test
	public void testEstadoAberto() {
		
		try {
			
			dispararChamadasComTimeout(maxFailures);
			
			waitUntilCircuitIs(CircuitBrakerStates.OPEN);
			
			cb.call(new Callable<String>() {
			      public String call() {
			        return shortRunningThread();
			      }
				});
			
			fail();
			
		} catch (CircuitBreakerOpenException cboe) {
			assertTrue(true);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testMeioAbertoSucesso() {
		
		try {

			if(cb.getState() == CircuitBrakerStates.CLOSED)
				dispararChamadasComTimeout(maxFailures);
			else if(cb.getState() == CircuitBrakerStates.HALFOPEN)
				waitUntilCircuitIs(CircuitBrakerStates.OPEN);
						
			waitUntilCircuitIs(CircuitBrakerStates.HALFOPEN);
				
			String result = cb.call(new Callable<String>() {
			      public String call() {
			        return shortRunningThread();
			      }
			}).get();

			assertNotNull(result);
						
		} catch (CircuitBreakerOpenException cboe) {
			cboe.printStackTrace();
			fail();
		}catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	
	@Test
	public void testMeioAbertoFalha() {
		
		try {

			if(cb.getState() == CircuitBrakerStates.CLOSED)
				dispararChamadasComTimeout(maxFailures);
			else if(cb.getState() == CircuitBrakerStates.HALFOPEN)
				waitUntilCircuitIs(CircuitBrakerStates.OPEN);
						
			waitUntilCircuitIs(CircuitBrakerStates.HALFOPEN);
				
			cb.call(new Callable<String>() {
			      public String call() {
			        return blockingThread();
			      }
			}).get();

			fail();
						
		} catch (CancellationException cboe) {
			
			waitUntilCircuitIs(CircuitBrakerStates.HALFOPEN);
				assertTrue(cb.getState() == CircuitBrakerStates.HALFOPEN);
				
		}catch (Exception e) {
			fail();
		}
	}
	
	
	@Test
	public void testMeioAbertoSomenteUmaChamada() {
		
		try {

			if(cb.getState() == CircuitBrakerStates.CLOSED)
				dispararChamadasComTimeout(maxFailures);		
			
			waitUntilCircuitIs(CircuitBrakerStates.HALFOPEN);
			
			cb.call(new Callable<String>() {
			      public String call() {
			    	  return "Returned";
			      }
			});

			cb.call(new Callable<String>() {
			      public String call() {
			    	  return "Returned";
			      }
			});
			
			fail();
			
		} catch (CircuitBreakerOpenException cboe) {
			assertTrue(true);
		}catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

	
	
	
	public String shortRunningThread() {
		return "Resultado obtido";
	}


	public String blockingThread() {
		for (long i = 0; i < Long.MAX_VALUE; i++) {}
		return null;
	}

	public void waitUntilCircuitIs(CircuitBrakerStates desiredState) {

		for (long i = 0; i < Long.MAX_VALUE; i++) {
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if(cb.getState() == desiredState){
				break;
			}
				
		}
		
	}

	
	private void dispararChamadasComTimeout(int concurrentThreads){
	
		for (int i = 0; i < concurrentThreads; i++) {
			cb.call(new Callable<String>() {
			      public String call() {
			        return blockingThread();
			      }
				});
		}
	}
	
}
