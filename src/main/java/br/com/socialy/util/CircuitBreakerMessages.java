package br.com.socialy.util;

public class CircuitBreakerMessages {

	public static final String MEIO_ABERTO_SOMENTE_UMA_THREAD = 
			"J� existe uma thread em execu��o verificando o estado do circuito"; 

	public static final String FECHADO_NUM_MAX_FALHAS = 
			"O n�mero m�ximo de falhas foi alcan�ado o circuito ser� aberto at� rein�cio"; 

	public static final String MEIO_ABERTO_REINICIO = 
			"O circuito est� em estado meio aberto. Somente uma chamada ser� liberada"; 

	public static final String MEIO_ABERTO_VERICANDO_ESTADO = 
			"Uma chamada foi liberada para verifica��o do estado do circuito"; 

	public static final String MEIO_ABERTO_ERRO_PERSISTENTE = 
			"Circuito ainda continua com problemas, novo rein�ncio agendado"; 

	public static final String FECHADO_OK = 
			"O circuito esta fechado todas as chamadas ser�o liberadas"; 

	
}
