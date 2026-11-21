package cl.duoc.digitalfix.workorders.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaOutboxValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID orderId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void acceptsValidPayload() throws Exception {
        var payload = mapper.readTree("""
                {"workOrderId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","status":"CREATED","changedBy":"user"}
                """);
        assertDoesNotThrow(() -> KafkaOutboxPublisher.validatePayload(payload, orderId, "WORK_ORDER_CREATED"));
    }

    @Test
    void rejectsInvalidPayloads() throws Exception {
        for (String json : new String[] {
                "[]",
                "{\"status\":\"CREATED\"}",
                "{\"workOrderId\":\"bad\",\"status\":\"CREATED\"}",
                "{\"workOrderId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"status\":\"CREATED\"}",
                "{\"workOrderId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"status\":\"CLOSED\"}",
                "{\"workOrderId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\",\"status\":\"CREATED\",\"changedBy\":42}"
        }) {
            var payload = mapper.readTree(json);
            assertThrows(IllegalArgumentException.class,
                    () -> KafkaOutboxPublisher.validatePayload(payload, orderId, "WORK_ORDER_CREATED"), json);
        }
    }

    @Test
    void quarantinesInvalidRecordAndPublishesTheNextOne() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        UUID invalidId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();
        ResultSet invalid = row(invalidId, "{\"status\":\"CREATED\"}");
        ResultSet valid = row(validId, "{\"status\":\"CREATED\",\"workOrderId\":\"" + orderId + "\"}");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(invalid, 0), mapper.mapRow(valid, 1));
        });
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        new KafkaOutboxPublisher(jdbc, kafka, mapper, "orders", "audit").publishPending();
        verify(jdbc).update(contains("quarantined_at = NOW()"), anyString(), eq(invalidId));
        verify(jdbc).update(contains("published_at = NOW()"), eq(validId));
        verify(kafka).send(eq("orders"), eq(orderId.toString()), contains(validId.toString()));
        verify(kafka).send(eq("audit"), eq(validId.toString()), contains(validId.toString()));
        verifyNoMoreInteractions(kafka);
    }

    private ResultSet row(UUID eventId, String payload) throws Exception {
        ResultSet result = mock(ResultSet.class);
        when(result.getObject("event_id", UUID.class)).thenReturn(eventId);
        when(result.getObject("aggregate_id", UUID.class)).thenReturn(orderId);
        when(result.getString("event_type")).thenReturn("WORK_ORDER_CREATED");
        when(result.getObject("created_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        when(result.getString("payload")).thenReturn(payload);
        return result;
    }
}
