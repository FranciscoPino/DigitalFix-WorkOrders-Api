# ms-digitalfix-workorders

Microservicio de órdenes de trabajo de DigitalFix. Es propietario del modelo y de las reglas del ciclo de vida de las órdenes y es el **productor de comandos RabbitMQ** hacia `ms-digitalfix-notify`.

## Arquitectura y patrón

Implementa DDD por capas: `domain` (WorkOrder, estados y contrato de repositorio), `application` (casos de uso y puerto `WorkOrderEventPublisher`), `interfaces` (REST) e `infrastructure` (JPA/Hibernate sobre PostgreSQL y mensajería RabbitMQ). Persiste en `workorders.work_orders` con `ddl-auto: validate`; la aplicación genera el identificador con `UUID.randomUUID()`. El esquema detallado está en [`digitalfix-postgresql.sql`](../digitalfix-postgresql.sql).

```text
Frontend -> BFF -> ms-digitalfix-workorders -> PostgreSQL
                          |
                          +-- (productor) --> RabbitMQ cmd.direct --> ms-digitalfix-notify (consumer)
```

| Paquete / clase | Responsabilidad |
|---|---|
| `application.WorkOrderApplicationService` | Reglas del ciclo de vida; tras persistir el cambio de estado llama al publicador |
| `application.WorkOrderEventPublisher` | Puerto (interfaz) de salida hacia mensajería |
| `infrastructure.messaging.RabbitWorkOrderEventPublisher` | Mapea estado → comandos y publica en `cmd.direct` |
| `infrastructure.messaging.MessageEnvelope` | Envelope común (`type`, `eventId`, `timestamp`, `traceId`, `correlationId`, `payload`) |
| `infrastructure.messaging.DlqDemoPublisher` | Solo demo: publica un mensaje inválido para provocar la DLQ |
| `config.RabbitMQConfig` | Exchange `cmd.direct` y convertidor JSON |
| `infrastructure.WorkOrderEntity`, `TechnicianEntity`, `JpaWorkOrderRepositoryAdapter`, `TechnicianSeeder` | Capa ORM (sin SQL manual) |

Las transiciones regulares son `CREATED -> ASSIGNED -> EN_ROUTE -> IN_PROGRESS -> CLOSED`. `CANCELLED` está permitido desde cualquier estado no cancelado. Para asignar se requiere un técnico existente y activo. Las observaciones se guardan en la orden y en el historial.

## Flujo de un cambio de estado

```text
PUT /api/workorders/{id}/status
  1. Valida la transición y persiste la orden (JPA)
  2. WorkOrderApplicationService -> WorkOrderEventPublisher.publishStatusChange(order)
  3. RabbitWorkOrderEventPublisher arma el envelope y publica en cmd.direct (persistente)
  4. RabbitMQ enruta por routing key a q.cmd.email / q.cmd.dispatch / q.cmd.report
  5. ms-digitalfix-notify consume, procesa y hace ACK (o NACK -> DLQ)
```

## Configuración local

Completa `.env` con `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` y `DB_SSL_MODE=require`, y las variables de RabbitMQ (ver más abajo). El servicio importa ese archivo desde su directorio de ejecución. En el arranque, `TechnicianSeeder` inserta/actualiza de forma idempotente (vía JPA) tres técnicos de demostración; sus UUID están en esa clase.

Si la base ya fue creada antes de agregar observaciones y cancelación, ejecuta una vez [`alter-workorders-observations-cancel.sql`](../alter-workorders-observations-cancel.sql) contra `digitalfix`.

## Ejecutar y probar

Requiere un RabbitMQ accesible (clúster local de `docker-compose.yml`: AMQP `localhost:5672` y `localhost:5673`, UI `http://localhost:15672` / `15673`, usuario `admin` / `admin`) y, para ver el consumo, `ms-digitalfix-notify` en ejecución.

```powershell
mvn clean verify
mvn spring-boot:run
```

Puerto local: `8081`. Prueba preferida a través del BFF (`http://localhost:8080`); para depurar el servicio directamente usa `http://localhost:8081`. En Postman agrega `Authorization: Bearer <token>` si se activa seguridad en el gateway/BFF.

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/api/workorders` | Crea orden en estado `CREATED`; el servidor genera el ID (no publica en RabbitMQ) |
| GET | `/api/workorders/{id}` | Obtiene una orden |
| GET | `/api/workorders?status=CREATED&customerId=...` | Filtra órdenes |
| PUT | `/api/workorders/{id}/status` | Cambia estado, técnico y observación; **publica comandos en RabbitMQ** |
| POST | `/api/workorders/demo/dlq?queue=email\|dispatch\|report&mode=invalid-json\|invalid-envelope` | Solo demo: provoca un mensaje a DLQ |

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

Este servicio es el **productor** de comandos hacia `ms-digitalfix-notify` (consumer, que declara colas, DLQ y bindings; ver su README). Publica en el exchange `cmd.direct` (direct, durable) con mensajes persistentes y el envelope común. `correlationId` es el ID de la orden y `eventId` se usa como `messageId` AMQP.

### Topología (declarada por el consumer)

| Exchange | Tipo | Cola principal | Routing key (direct) | DLQ |
|---|---|---|---|---|
| `cmd.direct` | direct | `q.cmd.email` | `email.send` | `q.cmd.email.dlq` |
| `cmd.direct` | direct | `q.cmd.dispatch` | `dispatch.ticket` | `q.cmd.dispatch.dlq` |
| `cmd.direct` | direct | `q.cmd.report` | `report.gen` | `q.cmd.report.dlq` |

`cmd.topic` (topic) y `cmd.dead.dlx` (direct, para DLQ) existen en el broker pero este servicio solo publica en `cmd.direct`. Este servicio solo declara el exchange `cmd.direct`, con la misma definición que el consumer; las colas y bindings dependen de que notify se haya iniciado al menos una vez.

### Eventos por estado

| Nuevo estado (`PUT /{id}/status`) | Routing key | `type` |
|---|---|---|
| `ASSIGNED` | `email.send` | `EMAIL_ORDER_ASSIGNED` |
| `ASSIGNED` | `dispatch.ticket` | `DISPATCH_TICKET_REQUESTED` |
| `EN_ROUTE` | `email.send` | `EMAIL_TECHNICIAN_EN_ROUTE` |
| `IN_PROGRESS` | `email.send` | `EMAIL_WORK_STARTED` |
| `CLOSED` | `email.send` | `EMAIL_ORDER_CLOSED` |
| `CLOSED` | `report.gen` | `REPORT_GENERATION_REQUESTED` |
| `CANCELLED` | `email.send` | `EMAIL_ORDER_CANCELLED` |

Todos los estados alcanzables desde `PUT /{id}/status` publican al menos una notificación en `q.cmd.email`. Además, `ASSIGNED` publica el ticket de despacho y `CLOSED` solicita el informe en PDF. `CREATED` no se publica como cambio de estado porque es el estado inicial de la creación.

### Envelope

```json
{
  "type": "EMAIL_ORDER_ASSIGNED",
  "eventId": "962acedd-4f64-40bb-99b0-c8107978da7c",
  "timestamp": "2026-10-13T21:17:20.164Z",
  "traceId": "<MDC traceId o UUID>",
  "correlationId": "<id de la orden>",
  "payload": {
    "orderId": "...", "customerId": "...", "serviceId": "...", "technicianId": "...",
    "status": "ASSIGNED", "title": "...", "description": "...", "address": "...", "observations": "..."
  }
}
```

### Variables de entorno

`RABBITMQ_ADDRESSES` (opcional; lista de nodos, p. ej. `localhost:5672,localhost:5673`), `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`. Si `RABBITMQ_ADDRESSES` se define, Spring AMQP la usa en lugar de host/port para intentar conexión a ambos nodos; las credenciales deben coincidir con el clúster.

Este productor solo declara el exchange `cmd.direct`; la metadata de exchanges, colas y bindings se replica entre nodos. Las colas quorum declaradas por notify replican también los mensajes. `queue_leader_locator=balanced` distribuye líderes de colas clásicas, pero no replica sus mensajes. Con dos nodos, una quorum queue necesita ambos para conservar mayoría; para tolerar la caída de un nodo, RabbitMQ recomienda tres nodos.

### Salida por consola (demostración)

Cada comando publicado imprime en la consola del servicio un bloque `LLAMADA A NOTIFY (RABBITMQ)` con exchange, routing key, type, eventId, traceId, correlationId y payload, además del log `Comando publicado ...`. En notify se ven luego los bloques de mensaje recibido, consumido y procesado.

### Limitaciones

- La publicación ocurre después de persistir el cambio de estado y un fallo solo se registra en el log (no revierte la orden).
- No hay *publisher confirms* ni patrón outbox: si se requiere entrega garantizada, publicar desde `integration.outbox_events` en vez de directamente.
- Si el consumer nunca se inició, las colas/bindings no existen y el broker descarta el mensaje sin error.

## Demo: provocar un mensaje a DLQ

Solo para demostración en clase; no habilitar en producción. `POST /api/workorders/demo/dlq?queue=email|dispatch|report&mode=invalid-json|invalid-envelope` (roles Admin/Supervisor). El BFF lo expone igual y el front lo muestra en un bloque plegable "Demo RabbitMQ" bajo la tabla de Órdenes de trabajo.

| `mode` | Cuerpo publicado | Resultado en notify |
|---|---|---|
| `invalid-json` | `esto no es un json valido` | Rechazado (NACK sin requeue) |
| `invalid-envelope` | `{"foo":"bar"}` (sin `type`/`eventId`) | Rechazado (NACK sin requeue) |

El mensaje termina en `q.cmd.<cola>.dlq` (`email` → `q.cmd.email.dlq`, `dispatch` → `q.cmd.dispatch.dlq`, `report` → `q.cmd.report.dlq`). Un valor de `queue` o `mode` inválido responde `400`.

## Cómo comprobar el flujo

1. Levanta RabbitMQ, `ms-digitalfix-notify` y este servicio.
2. Cambia el estado de una orden (front o `PUT /api/workorders/{id}/status`).
3. En esta consola: bloque `LLAMADA A NOTIFY (RABBITMQ)`. En notify: `MENSAJE RECIBIDO`, `CONSUMER [...]` y `MENSAJE PROCESADO` con `Resultado : OK (ACK)`.
4. En `http://localhost:15672` → Queues: los mensajes se consumen de `q.cmd.*`; los rechazados aparecen en `q.cmd.*.dlq`.

Pruebas unitarias del enrutamiento por estado: `RabbitWorkOrderEventPublisherTest` (`mvn test`).
