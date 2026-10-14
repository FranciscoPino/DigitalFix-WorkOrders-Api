package cl.duoc.digitalfix.workorders.interfaces;

import cl.duoc.digitalfix.workorders.infrastructure.messaging.DlqDemoPublisher;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** SOLO PARA DEMOSTRACIÓN: permite provocar un mensaje a DLQ desde el front. */
@RestController
@RequestMapping("/api/workorders/demo")
public class DlqDemoController {

    private final DlqDemoPublisher publisher;

    public DlqDemoController(DlqDemoPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/dlq")
    @PreAuthorize("@securityProperties.disabled() or hasAnyAuthority('Admin', 'Supervisor', 'ROLE_Admin', 'ROLE_Supervisor')")
    public Map<String, String> sendToDlq(@RequestParam(defaultValue = "email") String queue,
                                         @RequestParam(defaultValue = "invalid-json") String mode) {
        try {
            return publisher.publishPoisonMessage(queue, mode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
