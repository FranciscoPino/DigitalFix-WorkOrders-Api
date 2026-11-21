package cl.duoc.digitalfix.workorders.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@EnabledIfEnvironmentVariable(named = "DIGITALFIX_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class KafkaOutboxPublisherIntegrationTest {
    @Test
    void retriesBothTopicsWithTheSameEventIdIfTheSecondSendFails() throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("DIGITALFIX_TEST_DB_URL"), "postgres", "test")) {
            connection.setAutoCommit(false);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute("CREATE SCHEMA integration");
            jdbc.execute("""
                    CREATE TABLE integration.outbox_events (event_id uuid PRIMARY KEY, aggregate_type varchar NOT NULL,
                    aggregate_id uuid NOT NULL, event_type varchar NOT NULL, payload jsonb NOT NULL,
                    created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz, quarantined_at timestamptz,
                    attempts integer NOT NULL DEFAULT 0, last_error text)
                    """);
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO integration.outbox_events (event_id, aggregate_type, aggregate_id, event_type, payload)
                    VALUES (?, 'WORK_ORDER', ?, 'WORK_ORDER_CREATED', jsonb_build_object('status', 'CREATED', 'workOrderId', ?::text))
                    """, eventId, orderId, orderId);
            @SuppressWarnings("unchecked")
            KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
            CompletableFuture<SendResult<String, String>> success = CompletableFuture.completedFuture(null);
            when(kafka.send(eq("orders"), eq(orderId.toString()), anyString())).thenReturn(success);
            when(kafka.send(eq("audit"), eq(eventId.toString()), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")), success);
            KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(jdbc, kafka, new ObjectMapper(), "orders", "audit");
            publisher.publishPending();
            assertNull(jdbc.queryForObject("SELECT published_at FROM integration.outbox_events", Object.class));
            assertEquals(1, jdbc.queryForObject("SELECT attempts FROM integration.outbox_events", Integer.class));
            publisher.publishPending();
            assertNotNull(jdbc.queryForObject("SELECT published_at FROM integration.outbox_events", Object.class));
            assertEquals(2, jdbc.queryForObject("SELECT attempts FROM integration.outbox_events", Integer.class));
            verify(kafka, times(2)).send(eq("orders"), eq(orderId.toString()), contains(eventId.toString()));
            verify(kafka, times(2)).send(eq("audit"), eq(eventId.toString()), contains(eventId.toString()));
            connection.rollback();
        }
    }

    @Test
    void quarantinesInvalidPayloadAndContinuesWithNextEvent() throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("DIGITALFIX_TEST_DB_URL"), "postgres", "test")) {
            connection.setAutoCommit(false);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute("CREATE SCHEMA integration");
            jdbc.execute("""
                    CREATE TABLE integration.outbox_events (event_id uuid PRIMARY KEY, aggregate_type varchar NOT NULL,
                    aggregate_id uuid NOT NULL, event_type varchar NOT NULL, payload jsonb NOT NULL,
                    created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz, quarantined_at timestamptz,
                    attempts integer NOT NULL DEFAULT 0, last_error text)
                    """);
            UUID invalid = UUID.randomUUID();
            UUID valid = UUID.randomUUID();
            UUID order = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO integration.outbox_events (event_id, aggregate_type, aggregate_id, event_type, payload, created_at)
                    VALUES (?, 'WORK_ORDER', ?, 'WORK_ORDER_CREATED', '{"status":"INVALID"}', now() - interval '1 second'),
                           (?, 'WORK_ORDER', ?, 'WORK_ORDER_CREATED', jsonb_build_object('status', 'CREATED', 'workOrderId', ?::text), now())
                    """, invalid, order, valid, order, order);
            @SuppressWarnings("unchecked")
            KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
            when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
            KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(jdbc, kafka, new ObjectMapper(), "orders", "audit");
            publisher.publishPending();
            assertNotNull(jdbc.queryForObject("SELECT quarantined_at FROM integration.outbox_events WHERE event_id = ?", Object.class, invalid));
            assertNotNull(jdbc.queryForObject("SELECT last_error FROM integration.outbox_events WHERE event_id = ?", String.class, invalid));
            assertNotNull(jdbc.queryForObject("SELECT published_at FROM integration.outbox_events WHERE event_id = ?", Object.class, valid));
            verify(kafka, times(2)).send(anyString(), anyString(), contains(valid.toString()));
            publisher.publishPending();
            verifyNoMoreInteractions(kafka);
            connection.rollback();
        }
    }
}
