package cl.duoc.digitalfix.workorders.infrastructure.messaging;

import java.time.Instant;
import java.util.Map;

/** Envelope común de comandos; debe coincidir con el que espera ms-digitalfix-notify. */
public record MessageEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        Map<String, Object> payload) {
}
