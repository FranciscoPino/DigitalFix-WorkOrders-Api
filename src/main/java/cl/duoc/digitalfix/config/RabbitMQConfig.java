package cl.duoc.digitalfix.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuración del productor RabbitMQ. Las colas, DLQ y bindings los declara el consumer
 * (ms-digitalfix-notify); aquí solo se asegura el exchange de comandos.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_DIRECT = "cmd.direct";

    public static final String RK_EMAIL = "email.send";
    public static final String RK_DISPATCH = "dispatch.ticket";
    public static final String RK_REPORT = "report.gen";

    /** Misma definición que en el consumer (durable, direct) para que la redeclaración sea idempotente. */
    @Bean
    DirectExchange cmdDirectExchange() {
        return new DirectExchange(EXCHANGE_DIRECT, true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }
}
