package cl.duoc.digitalfix.workorders.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.duoc.digitalfix.config.RabbitMQConfig;

/**
 * SOLO PARA DEMOSTRACIÓN: publica un mensaje inválido (poison message) para que el consumer
 * lo rechace sin reencolar y RabbitMQ lo dirija a la DLQ de la cola elegida.
 */
@Component
public class DlqDemoPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqDemoPublisher.class);

    public static final Map<String, String> ROUTING_KEYS = Map.of(
            "email", RabbitMQConfig.RK_EMAIL,
            "dispatch", RabbitMQConfig.RK_DISPATCH,
            "report", RabbitMQConfig.RK_REPORT);

    private final RabbitTemplate rabbitTemplate;

    public DlqDemoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** @param mode "invalid-json" (cuerpo no parseable) o "invalid-envelope" (JSON sin type/eventId) */
    public Map<String, String> publishPoisonMessage(String queue, String mode) {
        String routingKey = ROUTING_KEYS.get(queue);
        if (routingKey == null) {
            throw new IllegalArgumentException("queue debe ser email, dispatch o report");
        }
        String body = switch (mode) {
            case "invalid-json" -> "esto no es un json valido";
            case "invalid-envelope" -> "{\"foo\":\"bar\"}";
            default -> throw new IllegalArgumentException("mode debe ser invalid-json o invalid-envelope");
        };

        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE_DIRECT, routingKey,
                new Message(body.getBytes(StandardCharsets.UTF_8), props));

        log.warn("[DEMO DLQ] Mensaje inválido publicado exchange={} routingKey={} mode={}",
                RabbitMQConfig.EXCHANGE_DIRECT, routingKey, mode);
        System.out.println("""

                ========== DEMO DLQ: MENSAJE INVALIDO PUBLICADO ==========
                Exchange     : %s
                Routing key  : %s
                Modo         : %s
                Cuerpo       : %s
                Destino final: q.cmd.%s.dlq (tras ser rechazado por notify)
                ==========================================================
                """.formatted(RabbitMQConfig.EXCHANGE_DIRECT, routingKey, mode, body, queue));

        return Map.of("exchange", RabbitMQConfig.EXCHANGE_DIRECT, "routingKey", routingKey,
                "mode", mode, "expectedDlq", "q.cmd." + queue + ".dlq");
    }
}
