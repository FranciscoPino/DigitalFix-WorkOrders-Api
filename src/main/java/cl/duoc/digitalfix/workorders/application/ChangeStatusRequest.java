package cl.duoc.digitalfix.workorders.application;

import cl.duoc.digitalfix.workorders.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChangeStatusRequest(
        @NotNull OrderStatus status,
        UUID technicianId,
        String observations) {
}
