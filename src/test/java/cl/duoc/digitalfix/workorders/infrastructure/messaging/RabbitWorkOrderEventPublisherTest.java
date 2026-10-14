package cl.duoc.digitalfix.workorders.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import cl.duoc.digitalfix.config.RabbitMQConfig;
import cl.duoc.digitalfix.workorders.domain.OrderStatus;
import cl.duoc.digitalfix.workorders.domain.WorkOrder;

class RabbitWorkOrderEventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private RabbitWorkOrderEventPublisher publisher;
    private WorkOrder order;

    @BeforeEach
    void setUp() {
        rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        publisher = new RabbitWorkOrderEventPublisher(rabbitTemplate);
        OffsetDateTime now = OffsetDateTime.now();
        order = new WorkOrder(UUID.randomUUID(), "customer@example.com", UUID.randomUUID(), UUID.randomUUID(),
                OrderStatus.CREATED, "Repair", "Repair description", "Address", null,
                now, null, null, null, null, now);
    }

    @Test
    void publishesEmailAndDispatchWhenAssigned() {
        publishAndAssert(OrderStatus.ASSIGNED,
                new String[][] {
                        {RabbitMQConfig.RK_EMAIL, "EMAIL_ORDER_ASSIGNED"},
                        {RabbitMQConfig.RK_DISPATCH, "DISPATCH_TICKET_REQUESTED"}
                });
    }

    @Test
    void publishesEmailWhenTechnicianIsEnRoute() {
        publishAndAssert(OrderStatus.EN_ROUTE,
                new String[][] {{RabbitMQConfig.RK_EMAIL, "EMAIL_TECHNICIAN_EN_ROUTE"}});
    }

    @Test
    void publishesEmailWhenWorkStarts() {
        publishAndAssert(OrderStatus.IN_PROGRESS,
                new String[][] {{RabbitMQConfig.RK_EMAIL, "EMAIL_WORK_STARTED"}});
    }

    @Test
    void publishesEmailAndReportWhenClosed() {
        publishAndAssert(OrderStatus.CLOSED,
                new String[][] {
                        {RabbitMQConfig.RK_EMAIL, "EMAIL_ORDER_CLOSED"},
                        {RabbitMQConfig.RK_REPORT, "REPORT_GENERATION_REQUESTED"}
                });
    }

    @Test
    void publishesEmailWhenCancelled() {
        publishAndAssert(OrderStatus.CANCELLED,
                new String[][] {{RabbitMQConfig.RK_EMAIL, "EMAIL_ORDER_CANCELLED"}});
    }

    private void publishAndAssert(OrderStatus status, String[][] expected) {
        WorkOrder changed = withStatus(status);

        publisher.publishStatusChange(changed);

        for (String[] event : expected) {
            ArgumentCaptor<Object> envelopeCaptor = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_DIRECT), eq(event[0]),
                    envelopeCaptor.capture(), any(MessagePostProcessor.class));
            MessageEnvelope envelope = (MessageEnvelope) envelopeCaptor.getValue();
            assertEquals(event[1], envelope.type());
            assertEquals(status, envelope.payload().get("status"));
        }
        verifyNoMoreInteractions(rabbitTemplate);
    }

    private WorkOrder withStatus(OrderStatus status) {
        return new WorkOrder(order.id(), order.customerId(), order.serviceId(), order.technicianId(), status,
                order.title(), order.description(), order.address(), order.observations(), order.createdAt(),
                status == OrderStatus.ASSIGNED ? OffsetDateTime.now() : null,
                status == OrderStatus.IN_PROGRESS ? OffsetDateTime.now() : null,
                status == OrderStatus.CLOSED ? OffsetDateTime.now() : null,
                status == OrderStatus.CANCELLED ? OffsetDateTime.now() : null, OffsetDateTime.now());
    }
}
