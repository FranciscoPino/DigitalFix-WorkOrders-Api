package cl.duoc.digitalfix.workorders.application;

import cl.duoc.digitalfix.workorders.domain.OrderStatus;
import cl.duoc.digitalfix.workorders.domain.WorkOrder;
import cl.duoc.digitalfix.workorders.domain.WorkOrderRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderApplicationService {
    private final WorkOrderRepository repository;

    private final WorkOrderEventPublisher eventPublisher;

    public WorkOrderApplicationService(WorkOrderRepository repository, WorkOrderEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public WorkOrder create(CreateWorkOrderRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        return repository.create(new WorkOrder(UUID.randomUUID(), request.customerId(), request.serviceId(), null,
            OrderStatus.CREATED, request.title(), request.description(), request.address(), null, now, null, null, null, null, now));
    }

    public WorkOrder get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Work order not found: " + id));
    }

    public List<WorkOrder> find(OrderStatus status, String customerId) {
        return repository.find(status, customerId);
    }

    public WorkOrder changeStatus(UUID id, ChangeStatusRequest request) {
        WorkOrder current = get(id);
        validateTransition(current.status(), request.status());
        OffsetDateTime now = OffsetDateTime.now();
        UUID technicianId = request.technicianId() == null ? current.technicianId() : request.technicianId();
        if (request.status() == OrderStatus.ASSIGNED && technicianId == null) {
            throw new IllegalArgumentException("A work order cannot be assigned without a technician");
        }
        if (request.status() == OrderStatus.ASSIGNED && !repository.technicianIsActive(technicianId)) {
            throw new IllegalArgumentException("The selected technician does not exist or is inactive. Run seed-technicians.sql and retry.");
        }
        WorkOrder updated = repository.update(new WorkOrder(current.id(), current.customerId(), current.serviceId(), technicianId,
            request.status(), current.title(), current.description(), current.address(),
                request.observations() == null || request.observations().isBlank() ? null : request.observations().trim(), current.createdAt(),
                request.status() == OrderStatus.ASSIGNED ? now : current.assignedAt(),
                request.status() == OrderStatus.IN_PROGRESS ? now : current.startedAt(),
                request.status() == OrderStatus.CLOSED ? now : current.closedAt(),
                request.status() == OrderStatus.CANCELLED ? now : current.cancelledAt(), now));
        eventPublisher.publishStatusChange(updated);
        return updated;
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        if (to == OrderStatus.CANCELLED && from != OrderStatus.CANCELLED) {
            return;
        }
        boolean valid = (from == OrderStatus.CREATED && (to == OrderStatus.ASSIGNED || to == OrderStatus.CANCELLED))
                || (from == OrderStatus.ASSIGNED && (to == OrderStatus.EN_ROUTE || to == OrderStatus.CANCELLED))
                || (from == OrderStatus.EN_ROUTE && (to == OrderStatus.IN_PROGRESS || to == OrderStatus.CANCELLED))
                || (from == OrderStatus.IN_PROGRESS && (to == OrderStatus.CLOSED || to == OrderStatus.CANCELLED));
        if (!valid) {
            throw new IllegalArgumentException("Invalid work order transition: " + from + " -> " + to);
        }
    }
}
