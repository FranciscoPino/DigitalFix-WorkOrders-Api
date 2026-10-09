package cl.duoc.digitalfix.workorders.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTechnicianRepository extends JpaRepository<TechnicianEntity, UUID> {
    boolean existsByIdAndActiveTrue(UUID id);
}
