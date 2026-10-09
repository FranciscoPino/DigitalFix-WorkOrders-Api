# ms-digitalfix-workorders

Microservicio de órdenes de trabajo de DigitalFix. Es propietario del modelo y de las reglas del ciclo de vida de las órdenes.

## Arquitectura y patrón

Implementa DDD por capas: `domain` (WorkOrder, estados y contrato de repositorio), `application` (casos de uso), `interfaces` (REST) e `infrastructure` (JPA/Hibernate sobre PostgreSQL: `WorkOrderEntity`, `TechnicianEntity`, repositorios Spring Data y `JpaWorkOrderRepositoryAdapter`; sin SQL manual). Persiste en `workorders.work_orders` con `ddl-auto: validate`; la aplicación genera el identificador con `UUID.randomUUID()`. El esquema detallado está en [`digitalfix-postgresql.sql`](../digitalfix-postgresql.sql).

```text
Frontend -> BFF -> ms-digitalfix-workorders -> PostgreSQL
```

Las transiciones regulares son `CREATED -> ASSIGNED -> EN_ROUTE -> IN_PROGRESS -> CLOSED`. `CANCELLED` está permitido desde cualquier estado no cancelado. Para asignar se requiere un técnico existente y activo. Las observaciones se guardan en la orden y en el historial.

## Configuración local

Completa `.env` con `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` y `DB_SSL_MODE=require`. El servicio importa ese archivo desde su directorio de ejecución. En el arranque, `TechnicianSeeder` inserta/actualiza de forma idempotente (vía JPA) tres técnicos de demostración; sus UUID están en esa clase.

Si la base ya fue creada antes de agregar observaciones y cancelación, ejecuta una vez [`alter-workorders-observations-cancel.sql`](../alter-workorders-observations-cancel.sql) contra `digitalfix`.

## Ejecutar y probar

```powershell
mvn clean verify
mvn spring-boot:run
```

Puerto local: `8081`. Prueba preferida a través del BFF (`http://localhost:8080`); para depurar el servicio directamente usa `http://localhost:8081`. En Postman agrega `Authorization: Bearer {{accessToken}}` si se activa seguridad en el gateway/BFF.

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/api/workorders` | Crea orden en estado `CREATED`; el servidor genera el ID |
| GET | `/api/workorders/{id}` | Obtiene una orden |
| GET | `/api/workorders?status=CREATED&customerId=...` | Filtra órdenes |
| PUT | `/api/workorders/{id}/status` | Cambia estado, técnico y observación |

Ejemplo Postman para crear:

```json
{
	"customerId": "cliente@empresa.cl",
	"serviceId": "<UUID_DE_CATALOG_SERVICES>",
	"title": "Falla eléctrica",
	"description": "Falla en enchufe",
	"address": "Prueba 123"
}
```

Ejemplo para asignar y agregar nota:

```json
{
	"status": "ASSIGNED",
	"technicianId": "a1000000-0000-4000-8000-000000000001",
	"observations": "Técnico asignado para visita"
}
```

`observations` es opcional. Para cancelar desde cualquier estado no cancelado envía `{"status":"CANCELLED","observations":"Motivo de cancelación"}`. No se requiere `technicianId` para cancelar.

Tras crear una orden y avanzar su estado, el script SQL registra eventos en `workorders.status_history` y `integration.outbox_events` mediante triggers.

## Productor RabbitMQ

Este servicio es el **productor** de comandos hacia `ms-digitalfix-notify` (consumer, que declara colas, DLQ y bindings; ver su README). Publica en el exchange `cmd.direct` con mensajes persistentes y el envelope común (`type`, `eventId`, `timestamp`, `traceId`, `correlationId`, `payload`). `correlationId` es el ID de la orden y `eventId` se usa como `messageId` AMQP.

| Nuevo estado (`PUT /{id}/status`) | Routing key | `type` |
|---|---|---|
| `ASSIGNED` | `email.send` | `EMAIL_ORDER_ASSIGNED` |
| `ASSIGNED` | `dispatch.ticket` | `DISPATCH_TICKET_REQUESTED` |
| `EN_ROUTE` | `email.send` | `EMAIL_TECHNICIAN_EN_ROUTE` |
| `CLOSED` | `email.send` | `EMAIL_ORDER_CLOSED` |
| `CLOSED` | `report.gen` | `REPORT_GENERATION_REQUESTED` |

`CREATED`, `IN_PROGRESS` y `CANCELLED` no publican. El `payload` incluye `orderId`, `customerId`, `serviceId`, `technicianId`, `status`, `title`, `description`, `address` y `observations`.

Variables en `.env`: `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` (por defecto `localhost:5672`, `guest/guest`, `/`).

Limitación: la publicación ocurre después de persistir el cambio de estado y un fallo solo se registra en el log (no revierte la orden). Si se requiere entrega garantizada, publicar desde `integration.outbox_events` (patrón outbox) en vez de directamente. Si RabbitMQ no tiene la cola/binding (consumer no iniciado nunca), el mensaje se descarta sin error.
