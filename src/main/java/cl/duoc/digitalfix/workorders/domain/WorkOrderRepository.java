package cl.duoc.digitalfix.workorders.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderRepository {
    WorkOrder create(WorkOrder order);
    WorkOrder update(WorkOrder order);
    Optional<WorkOrder> findById(UUID id);
    List<WorkOrder> find(OrderStatus status, String customerId);
    boolean technicianIsActive(UUID technicianId);
}