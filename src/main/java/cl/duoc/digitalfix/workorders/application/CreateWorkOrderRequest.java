package cl.duoc.digitalfix.workorders.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateWorkOrderRequest(
        @NotBlank String customerId,
        @NotNull UUID serviceId,
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String address) {
}
