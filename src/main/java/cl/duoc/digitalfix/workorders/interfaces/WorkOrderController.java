package cl.duoc.digitalfix.workorders.interfaces;

import cl.duoc.digitalfix.workorders.application.ChangeStatusRequest;
import cl.duoc.digitalfix.workorders.application.CreateWorkOrderRequest;
import cl.duoc.digitalfix.workorders.application.WorkOrderApplicationService;
import cl.duoc.digitalfix.workorders.domain.OrderStatus;
import cl.duoc.digitalfix.workorders.domain.WorkOrder;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workorders")
public class WorkOrderController {
    private final WorkOrderApplicationService service;

    public WorkOrderController(WorkOrderApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@securityProperties.disabled() or hasAnyAuthority('Admin', 'Supervisor', 'Cliente', 'ROLE_Admin', 'ROLE_Supervisor', 'ROLE_Cliente')")
    public WorkOrder create(@Valid @RequestBody CreateWorkOrderRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public WorkOrder get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public List<WorkOrder> find(@RequestParam(required = false) OrderStatus status,
                                @RequestParam(required = false) String customerId) {
        return service.find(status, customerId);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@securityProperties.disabled() or hasAnyAuthority('Admin', 'Supervisor', 'ROLE_Admin', 'ROLE_Supervisor')")
    public WorkOrder changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(id, request);
    }
}
