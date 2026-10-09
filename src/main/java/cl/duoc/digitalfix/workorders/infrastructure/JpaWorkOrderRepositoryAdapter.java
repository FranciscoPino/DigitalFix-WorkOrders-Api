package cl.duoc.digitalfix.workorders.infrastructure;

import cl.duoc.digitalfix.workorders.domain.OrderStatus;
import cl.duoc.digitalfix.workorders.domain.WorkOrder;
import cl.duoc.digitalfix.workorders.domain.WorkOrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaWorkOrderRepositoryAdapter implements WorkOrderRepository {
    private final SpringDataWorkOrderRepository repository;
    private final SpringDataTechnicianRepository technicianRepository;

    public JpaWorkOrderRepositoryAdapter(SpringDataWorkOrderRepository repository,
                                         SpringDataTechnicianRepository technicianRepository) {
        this.repository = repository;
        this.technicianRepository = technicianRepository;
    }

    @Override
    @Transactional
    public WorkOrder create(WorkOrder o) {
        return toDomain(repository.save(new WorkOrderEntity(o.id(), o.customerId(), o.serviceId(), o.technicianId(),
                o.status(), o.title(), o.description(), o.address(), o.observations(), o.createdAt(), o.assignedAt(),
                o.startedAt(), o.closedAt(), o.cancelledAt(), o.updatedAt())));
    }

    @Override
    @Transactional
    public WorkOrder update(WorkOrder o) {
        WorkOrderEntity entity = repository.findById(o.id())
                .orElseThrow(() -> new IllegalArgumentException("Work order not found: " + o.id()));
        entity.applyChanges(o.technicianId(), o.status(), o.observations(), o.assignedAt(), o.startedAt(),
                o.closedAt(), o.cancelledAt(), o.updatedAt());
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkOrder> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrder> find(OrderStatus status, String customerId) {
        Specification<WorkOrderEntity> spec = Specification.where(null);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (customerId != null && !customerId.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customerId"), customerId));
        }
        return repository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean technicianIsActive(UUID technicianId) {
        return technicianRepository.existsByIdAndActiveTrue(technicianId);
    }

    private WorkOrder toDomain(WorkOrderEntity e) {
        return new WorkOrder(e.getId(), e.getCustomerId(), e.getServiceId(), e.getTechnicianId(), e.getStatus(),
                e.getTitle(), e.getDescription(), e.getAddress(), e.getObservations(), e.getCreatedAt(),
                e.getAssignedAt(), e.getStartedAt(), e.getClosedAt(), e.getCancelledAt(), e.getUpdatedAt());
    }
}
