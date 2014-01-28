package br.com.socialy.util;

public class CircuitBreakerMessages {

	public static final String MEIO_ABERTO_SOMENTE_UMA_THREAD = 
			"Já existe uma thread em execução verificando o estado do circuito"; 

	public static final String FECHADO_NUM_MAX_FALHAS = 
			"O número máximo de falhas foi alcançado o circuito será aberto até reinício"; 

	public static final String MEIO_ABERTO_REINICIO = 
			"O circuito está em estado meio aberto. Somente uma chamada será liberada"; 

	public static final String MEIO_ABERTO_VERICANDO_ESTADO = 
			"Uma chamada foi liberada para verificação do estado do circuito"; 

	public static final String MEIO_ABERTO_ERRO_PERSISTENTE = 
			"Circuito ainda continua com problemas, novo reiníncio agendado"; 

	public static final String FECHADO_OK = 
			"O circuito esta fechado todas as chamadas serão liberadas"; 

	
}
