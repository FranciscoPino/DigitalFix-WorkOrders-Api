package cl.duoc.digitalfix.workorders.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KafkaOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);
    private static final Set<String> STATUSES = Set.of("CREATED", "ASSIGNED", "EN_ROUTE", "IN_PROGRESS", "CLOSED", "CANCELLED");
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final String workordersTopic;
    private final String auditTopic;

    public KafkaOutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
                                @Value("${KAFKA_WORKORDERS_TOPIC:workorders.events}") String workordersTopic,
                                @Value("${KAFKA_AUDIT_TOPIC:audit.timeline}") String auditTopic) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.workordersTopic = workordersTopic;
        this.auditTopic = auditTopic;
    }

    @Scheduled(fixedDelayString = "${KAFKA_OUTBOX_POLL_MS:5000}")
    @Transactional
    public void publishPending() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(1145456728)", Boolean.class))) {
            return;
        }
        List<OutboxEvent> events = jdbc.query("""
                SELECT event_id, aggregate_id, event_type, created_at, payload::text AS payload
                FROM integration.outbox_events
                WHERE published_at IS NULL AND quarantined_at IS NULL AND aggregate_type = 'WORK_ORDER'
                ORDER BY created_at, event_id LIMIT 25 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new OutboxEvent(rs.getObject("event_id", UUID.class),
                rs.getObject("aggregate_id", UUID.class), rs.getString("event_type"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("payload")));
        for (OutboxEvent event : events) {
            String message;
            try {
                message = serialize(event);
            } catch (IllegalArgumentException | JsonProcessingException e) {
                log.error("Quarantining invalid outbox eventId={}: {}", event.eventId(), e.getMessage());
                jdbc.update("""
                        UPDATE integration.outbox_events
                        SET quarantined_at = NOW(), attempts = attempts + 1, last_error = ? WHERE event_id = ?
                        """, e.toString(), event.eventId());
                continue;
            }
            try {
                kafka.send(workordersTopic, event.aggregateId().toString(), message).get(30, TimeUnit.SECONDS);
                kafka.send(auditTopic, event.eventId().toString(), message).get(30, TimeUnit.SECONDS);
                jdbc.update("""
                        UPDATE integration.outbox_events
                        SET published_at = NOW(), attempts = attempts + 1, last_error = NULL WHERE event_id = ?
                        """, event.eventId());
                log.info("Published work order eventId={} workOrderId={} to both topics",
                        event.eventId(), event.aggregateId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Outbox publisher interrupted", e);
            } catch (ExecutionException | TimeoutException | KafkaException e) {
                log.error("Failed to publish outbox eventId={}", event.eventId(), e);
                jdbc.update("""
                        UPDATE integration.outbox_events SET attempts = attempts + 1, last_error = ? WHERE event_id = ?
                        """, e.toString(), event.eventId());
                break;
            }
        }
    }

    private String serialize(OutboxEvent event) throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(event.payload());
        if (event.eventId() == null || event.aggregateId() == null || event.createdAt() == null
                || payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Invalid event envelope or payload");
        }
        validatePayload(payload, event.aggregateId(), event.eventType());
        ObjectNode message = objectMapper.createObjectNode();
        message.put("schemaVersion", 1);
        message.put("eventId", event.eventId().toString());
        message.put("eventType", event.eventType());
        message.put("aggregateType", "WORK_ORDER");
        message.put("aggregateId", event.aggregateId().toString());
        message.put("createdAt", event.createdAt().toString());
        message.set("payload", payload);
        return objectMapper.writeValueAsString(message);
    }

    static void validatePayload(JsonNode payload, UUID aggregateId, String eventType) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Invalid event payload");
        }
        JsonNode status = payload.path("status");
        JsonNode orderId = payload.path("workOrderId");
        if (!status.isTextual() || !STATUSES.contains(status.asText())
                || !("WORK_ORDER_" + status.asText()).equals(eventType)
                || !orderId.isTextual()) {
            throw new IllegalArgumentException("Invalid work order event type, status or workOrderId");
        }
        UUID payloadOrderId = UUID.fromString(orderId.asText());
        if (!aggregateId.equals(payloadOrderId)) {
            throw new IllegalArgumentException("Payload workOrderId does not match aggregateId");
        }
        JsonNode actor = payload.path("changedBy");
        if (!actor.isMissingNode() && !actor.isNull() && !actor.isTextual()) {
            throw new IllegalArgumentException("Invalid event actor");
        }
    }

    private record OutboxEvent(UUID eventId, UUID aggregateId, String eventType,
                               OffsetDateTime createdAt, String payload) {}
}
