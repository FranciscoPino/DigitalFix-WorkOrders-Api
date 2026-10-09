package cl.duoc.digitalfix.workorders.infrastructure;

import cl.duoc.digitalfix.workorders.domain.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Solo mapea las columnas que gestiona este servicio; el resto conserva su valor por defecto en BD. */
@Entity
@Table(name = "work_orders", schema = "workorders")
public class WorkOrderEntity {
    @Id
    @Column(name = "work_order_id", nullable = false)
    private UUID id;
    @Column(name = "customer_external_user_id", nullable = false, length = 200)
    private String customerId;
    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
    @Column(name = "technician_id")
    private UUID technicianId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "workorders.order_status")
    private OrderStatus status;
    @Column(name = "title", nullable = false, length = 180)
    private String title;
    @Column(name = "description", nullable = false)
    private String description;
    @Column(name = "observations")
    private String observations;
    @Column(name = "address_line", nullable = false, length = 250)
    private String address;
    @Column(name = "city", nullable = false, length = 100)
    private String city;
    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;
    @Column(name = "started_at")
    private OffsetDateTime startedAt;
    @Column(name = "closed_at")
    private OffsetDateTime closedAt;
    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;
    @Column(name = "created_by_external_user_id", nullable = false, length = 200)
    private String createdBy;
    @Column(name = "updated_by_external_user_id", nullable = false, length = 200)
    private String updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WorkOrderEntity() { }

    public WorkOrderEntity(UUID id, String customerId, UUID serviceId, UUID technicianId, OrderStatus status,
                           String title, String description, String address, String observations,
                           OffsetDateTime createdAt, OffsetDateTime assignedAt, OffsetDateTime startedAt,
                           OffsetDateTime closedAt, OffsetDateTime cancelledAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.serviceId = serviceId;
        this.title = title;
        this.description = description;
        this.address = address;
        this.city = address;
        this.createdBy = customerId;
        this.createdAt = createdAt;
        applyChanges(technicianId, status, observations, assignedAt, startedAt, closedAt, cancelledAt, updatedAt);
    }

    /** Campos que cambian en el ciclo de vida de la orden. */
    public void applyChanges(UUID technicianId, OrderStatus status, String observations, OffsetDateTime assignedAt,
                             OffsetDateTime startedAt, OffsetDateTime closedAt, OffsetDateTime cancelledAt,
                             OffsetDateTime updatedAt) {
        this.technicianId = technicianId;
        this.status = status;
        this.observations = observations;
        this.assignedAt = assignedAt;
        this.startedAt = startedAt;
        this.closedAt = closedAt;
        this.cancelledAt = cancelledAt;
        this.updatedBy = customerId;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public UUID getServiceId() { return serviceId; }
    public UUID getTechnicianId() { return technicianId; }
    public OrderStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getObservations() { return observations; }
    public String getAddress() { return address; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public OffsetDateTime getCancelledAt() { return cancelledAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
