package br.com.socialy.util;

public class CircuitBreakerOpenException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8933523773530228803L;

	public CircuitBreakerOpenException() {
		super();
		// TODO Auto-generated constructor stub
	}

	public CircuitBreakerOpenException(String message, Throwable cause) {
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	public CircuitBreakerOpenException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	public CircuitBreakerOpenException(Throwable cause) {
		super(cause);
		// TODO Auto-generated constructor stub
	}
	
}
