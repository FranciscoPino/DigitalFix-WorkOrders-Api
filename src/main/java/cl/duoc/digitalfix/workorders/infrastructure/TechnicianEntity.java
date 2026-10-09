package cl.duoc.digitalfix.workorders.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "technicians", schema = "workorders")
public class TechnicianEntity {
    @Id
    @Column(name = "technician_id", nullable = false)
    private UUID id;
    @Column(name = "external_user_id", nullable = false, unique = true, length = 200)
    private String externalUserId;
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Column(name = "email", nullable = false, length = 254)
    private String email;
    @Column(name = "phone", length = 40)
    private String phone;
    @Column(name = "active", nullable = false)
    private boolean active;

    protected TechnicianEntity() { }

    public TechnicianEntity(UUID id, String externalUserId, String fullName, String email, String phone, boolean active) {
        this.id = id;
        this.externalUserId = externalUserId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.active = active;
    }

    public UUID getId() { return id; }
    public String getExternalUserId() { return externalUserId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
}
