package cl.duoc.digitalfix.workorders.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@EnabledIfEnvironmentVariable(named = "DIGITALFIX_KAFKA_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "DIGITALFIX_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class KafkaOutboxEndToEndTest {
    @Test
    void publishesOutboxEventToBothKafkaTopics() throws Exception {
        String bootstrap = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        String ordersTopic = System.getenv("KAFKA_WORKORDERS_TOPIC");
        String auditTopic = System.getenv("KAFKA_AUDIT_TOPIC");
        var factory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                "bootstrap.servers", bootstrap, "key.serializer", StringSerializer.class,
                "value.serializer", StringSerializer.class, "acks", "all"));
        try (Connection connection = DriverManager.getConnection(System.getenv("DIGITALFIX_TEST_DB_URL"), "postgres", "test");
             KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                    "bootstrap.servers", bootstrap, "group.id", UUID.randomUUID().toString(),
                    "key.deserializer", StringDeserializer.class, "value.deserializer", StringDeserializer.class))) {
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
            List<TopicPartition> partitions = new ArrayList<>();
            for (String topic : List.of(ordersTopic, auditTopic)) {
                for (int i = 0; i < 3; i++) {
                    partitions.add(new TopicPartition(topic, i));
                }
            }
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            new KafkaOutboxPublisher(jdbc, new KafkaTemplate<>(factory), new ObjectMapper(), ordersTopic, auditTopic)
                    .publishPending();
            assertNotNull(jdbc.queryForObject("SELECT published_at FROM integration.outbox_events", Object.class));
            boolean ordersSeen = false;
            boolean auditSeen = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            ObjectMapper mapper = new ObjectMapper();
            while (System.nanoTime() < deadline && !(ordersSeen && auditSeen)) {
                for (var record : consumer.poll(Duration.ofSeconds(1))) {
                    if (mapper.readTree(record.value()).path("eventId").asText().equals(eventId.toString())) {
                        if (record.topic().equals(ordersTopic)) {
                            assertEquals(orderId.toString(), record.key());
                            ordersSeen = true;
                        } else if (record.topic().equals(auditTopic)) {
                            assertEquals(eventId.toString(), record.key());
                            auditSeen = true;
                        }
                    }
                }
            }
            assertTrue(ordersSeen, "Work order event not found");
            assertTrue(auditSeen, "Audit event not found");
            connection.rollback();
        } finally {
            factory.destroy();
        }
    }
}
