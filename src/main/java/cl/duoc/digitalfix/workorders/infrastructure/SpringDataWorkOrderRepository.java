package cl.duoc.digitalfix.workorders.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataWorkOrderRepository
        extends JpaRepository<WorkOrderEntity, UUID>, JpaSpecificationExecutor<WorkOrderEntity> {
}
