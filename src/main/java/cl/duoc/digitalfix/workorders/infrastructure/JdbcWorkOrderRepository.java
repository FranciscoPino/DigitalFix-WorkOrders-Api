package cl.duoc.digitalfix.workorders.infrastructure;

import cl.duoc.digitalfix.workorders.domain.OrderStatus;
import cl.duoc.digitalfix.workorders.domain.WorkOrder;
import cl.duoc.digitalfix.workorders.domain.WorkOrderRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkOrderRepository implements WorkOrderRepository {
    private final JdbcTemplate jdbc;

    public JdbcWorkOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public WorkOrder create(WorkOrder order) {
        jdbc.update("""
                INSERT INTO workorders.work_orders
                (work_order_id, customer_external_user_id, service_id, status, title, description, address_line, city,
                 observations, created_by_external_user_id, updated_by_external_user_id, created_at, updated_at)
                VALUES (?, ?, ?, CAST(? AS workorders.order_status), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, order.id(), order.customerId(), order.serviceId(), order.status().name(), order.title(),
                order.description(), order.address(), order.address(), order.observations(), order.customerId(), order.customerId(),
                order.createdAt(), order.updatedAt());
        return order;
    }

    @Override
    public WorkOrder update(WorkOrder order) {
        jdbc.update("""
                UPDATE workorders.work_orders SET technician_id = ?, status = CAST(? AS workorders.order_status), assigned_at = ?, started_at = ?,
                closed_at = ?, cancelled_at = ?, observations = ?, updated_by_external_user_id = ?, updated_at = ? WHERE work_order_id = ?
                """, order.technicianId(), order.status().name(), order.assignedAt(), order.startedAt(),
                order.closedAt(), order.cancelledAt(), order.observations(), order.customerId(), order.updatedAt(), order.id());
        return order;
    }

    @Override
    public Optional<WorkOrder> findById(UUID id) {
        return jdbc.query("SELECT * FROM workorders.work_orders WHERE work_order_id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public List<WorkOrder> find(OrderStatus status, String customerId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM workorders.work_orders WHERE TRUE");
        List<Object> parameters = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status = CAST(? AS workorders.order_status)");
            parameters.add(status.name());
        }
        if (customerId != null && !customerId.isBlank()) {
            sql.append(" AND customer_external_user_id = ?");
            parameters.add(customerId);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbc.query(sql.toString(), this::map, parameters.toArray());
    }

    @Override
    public boolean technicianIsActive(UUID technicianId) {
        Boolean active = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM workorders.technicians
                    WHERE technician_id = ? AND active = TRUE
                )
                """, Boolean.class, technicianId);
        return Boolean.TRUE.equals(active);
    }

    private WorkOrder map(ResultSet rs, int row) throws SQLException {
        return new WorkOrder(rs.getObject("work_order_id", UUID.class), rs.getString("customer_external_user_id"),
                rs.getObject("service_id", UUID.class), rs.getObject("technician_id", UUID.class),
                OrderStatus.valueOf(rs.getString("status")), rs.getString("title"), rs.getString("description"),
                rs.getString("address_line"), rs.getString("observations"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("assigned_at", OffsetDateTime.class), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class), rs.getObject("cancelled_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}