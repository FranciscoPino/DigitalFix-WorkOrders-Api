package cl.duoc.digitalfix.workorders.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkOrder(
        UUID id,
        String customerId,
        UUID serviceId,
        UUID technicianId,
        OrderStatus status,
        String title,
        String description,
        String address,
        String observations,
        OffsetDateTime createdAt,
        OffsetDateTime assignedAt,
        OffsetDateTime startedAt,
        OffsetDateTime closedAt,
        OffsetDateTime cancelledAt,
        OffsetDateTime updatedAt) {
}
