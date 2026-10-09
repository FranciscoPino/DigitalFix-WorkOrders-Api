package cl.duoc.digitalfix.workorders.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Inserta/actualiza de forma idempotente los técnicos de demostración (antes data.sql). */
@Component
public class TechnicianSeeder implements ApplicationRunner {
    private final SpringDataTechnicianRepository repository;

    public TechnicianSeeder(SpringDataTechnicianRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repository.saveAll(List.of(
                technician("a1000000-0000-4000-8000-000000000001", "camila.rojas", "Camila Rojas", "+56911110001"),
                technician("a1000000-0000-4000-8000-000000000002", "diego.munoz", "Diego Munoz", "+56911110002"),
                technician("a1000000-0000-4000-8000-000000000003", "valentina.soto", "Valentina Soto", "+56911110003")));
    }

    private TechnicianEntity technician(String id, String user, String name, String phone) {
        String email = user + "@digitalfix.local";
        return new TechnicianEntity(UUID.fromString(id), email, name, email, phone, true);
    }
}
