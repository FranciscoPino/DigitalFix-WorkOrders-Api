package cl.duoc.digitalfix.workorders.infrastructure.messaging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.duoc.digitalfix.config.RabbitMQConfig;
import cl.duoc.digitalfix.workorders.application.WorkOrderEventPublisher;
import cl.duoc.digitalfix.workorders.domain.WorkOrder;

/**
 * Publica comandos en cmd.direct según el nuevo estado de la orden:
 * cada estado posterior a CREATED genera una notificación; ASSIGNED también genera
 * un ticket de despacho y CLOSED también solicita el informe PDF.
 * Un fallo de publicación se registra pero no revierte el cambio de estado ya persistido.
 */
@Component
public class RabbitWorkOrderEventPublisher implements WorkOrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitWorkOrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitWorkOrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishStatusChange(WorkOrder order) {
        switch (order.status()) {
            case ASSIGNED -> {
                send(RabbitMQConfig.RK_EMAIL, "EMAIL_ORDER_ASSIGNED", order);
                send(RabbitMQConfig.RK_DISPATCH, "DISPATCH_TICKET_REQUESTED", order);
            }
            case EN_ROUTE -> send(RabbitMQConfig.RK_EMAIL, "EMAIL_TECHNICIAN_EN_ROUTE", order);
            case IN_PROGRESS -> send(RabbitMQConfig.RK_EMAIL, "EMAIL_WORK_STARTED", order);
            case CLOSED -> {
                send(RabbitMQConfig.RK_EMAIL, "EMAIL_ORDER_CLOSED", order);
                send(RabbitMQConfig.RK_REPORT, "REPORT_GENERATION_REQUESTED", order);
            }
            case CANCELLED -> send(RabbitMQConfig.RK_EMAIL, "EMAIL_ORDER_CANCELLED", order);
            case CREATED -> log.debug("No se publica evento de estado CREATED para orderId={}", order.id());
        }
    }

    private void send(String routingKey, String type, WorkOrder order) {
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
        MessageEnvelope envelope = new MessageEnvelope(type, UUID.randomUUID().toString(), Instant.now(),
                traceId, order.id().toString(), payload(order));
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_DIRECT, routingKey, envelope, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                message.getMessageProperties().setMessageId(envelope.eventId());
                message.getMessageProperties().setCorrelationId(envelope.correlationId());
                return message;
            });
            log.info("Comando publicado type={} routingKey={} eventId={} orderId={}",
                    type, routingKey, envelope.eventId(), order.id());
            System.out.println("""

                    ========== LLAMADA A NOTIFY (RABBITMQ) ==========
                    Exchange     : %s
                    Routing key  : %s
                    Type         : %s
                    Event ID     : %s
                    Trace ID     : %s
                    Correlation  : %s
                    Payload      : %s
                    =================================================
                    """.formatted(RabbitMQConfig.EXCHANGE_DIRECT, routingKey, type, envelope.eventId(),
                    envelope.traceId(), envelope.correlationId(), envelope.payload()));
        } catch (Exception e) {
            log.error("No se pudo publicar type={} routingKey={} orderId={}", type, routingKey, order.id(), e);
        }
    }

    private Map<String, Object> payload(WorkOrder o) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("orderId", o.id());
        p.put("customerId", o.customerId());
        p.put("serviceId", o.serviceId());
        p.put("technicianId", o.technicianId());
        p.put("status", o.status());
        p.put("title", o.title());
        p.put("description", o.description());
        p.put("address", o.address());
        p.put("observations", o.observations());
        return p;
    }
}
