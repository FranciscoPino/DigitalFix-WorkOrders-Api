package cl.duoc.digitalfix.workorders.application;

import cl.duoc.digitalfix.workorders.domain.WorkOrder;

/** Puerto de salida: publica comandos derivados de cambios en una orden de trabajo. */
public interface WorkOrderEventPublisher {

    /** Publica los comandos que corresponden al estado actual de la orden (si aplica). */
    void publishStatusChange(WorkOrder order);
}
