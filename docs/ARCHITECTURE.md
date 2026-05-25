# DispatchFlow Architecture

## Overview

DispatchFlow is a modular monolith for campus autonomous vehicle dispatch. It separates **business state** (orders, tasks, vehicles in MySQL) from **fleet runtime** (position, SOC, stage in Redis), enabling simulation today and real telematics adapters tomorrow.

## Module map

```text
front/                    Vue 3 admin SPA
  └─ /api ──proxy──▶ fsd-admin-api     REST aggregation
                         ├─ fsd-order      Order lifecycle
                         ├─ fsd-dispatch   Tasks, fleet, simulation, exceptions, events
                         └─ fsd-vehicle    Vehicle occupancy & reports
fsd-bootstrap             Spring Boot entry, application.yml
fsd-common                Shared enums, ApiResponse, exceptions
```

## Domain boundaries

| Domain | Responsibility | Key tables / stores |
|--------|----------------|---------------------|
| Order | Create and track delivery orders | `t_order` |
| Dispatch | Task creation, auto/manual assign, operate logs | `t_dispatch_task`, `t_dispatch_task_operate_log` |
| Fleet | Runtime telemetry, charge policy | Redis `fleet:runtime:{vehicleId}` |
| Vehicle | Occupancy, execution reports | `t_vehicle` |
| Exception | OPEN/RESOLVED records, severity, resolve actions | `t_dispatch_exception_record` |
| Event | Outbox + RabbitMQ publish | `t_dispatch_event_outbox` |

## Request flow — park order to completion

```text
POST /api/admin/park/orders
  → ParkPilotCommandService: validate stations, create order + task
  → DispatchTaskService.autoAssign: lock task, filter vehicles (FleetChargePolicy), assign nearest
  → Vehicle occupied; task → ASSIGNED

Simulation tick (ParkPilotSimulationServiceImpl)
  → State machine: TO_PICKUP → LOADING → TO_DROPOFF → UNLOADING → ...
  → SimulationFleetAdapter writes FleetRuntime to Redis
  → VehicleReportService: TASK_SUCCESS / TASK_FAILED
  → Order + task status updated; events published via Outbox

Monitor page
  → GET /api/admin/park/vehicles
  → FleetSnapshotAssembler reads FleetRuntimeService + DB vehicle metadata
```

## Fleet runtime model

`FleetRuntime` fields: `runtimeStage`, `pluggedIn`, `soc`, `targetCode`, `parkId`, `lastTelemetryAt`, trajectory points.

| Semantic | Condition |
|----------|-----------|
| Plugged standby | `STANDBY + pluggedIn + SOC=100%` — no drain, assignable |
| Unplug on assign | `pluggedIn=false` when task assigned |
| Low SOC | Auto route to charger via `FleetChargePolicy` |
| Persistence | Redis TTL 7 days; survives backend restart |

## Simulation vs production adapter

Today: `SimulationFleetAdapter` + `SimulationMotionStore` drive ticks and write runtime.

Future: replace adapter with MQTT/HTTP telematics ingest; keep `FleetRuntimeService` and monitoring APIs unchanged.

## Event reliability

Critical transitions publish domain events. `DispatchEventOutboxService` writes to `t_dispatch_event_outbox` in the same transaction; a scheduler retries failed RabbitMQ publishes.

## Configuration

| Prefix | Purpose |
|--------|---------|
| `fsd.fleet.energy.*` | SOC thresholds, charge rate, idle charging |
| `fsd.park.simulation.*` | Tick interval, simulation enable flag |
| `fsd.dispatch.outbox.*` | Retry delay for failed event publish |

See [DEPLOYMENT.md](DEPLOYMENT.md) for environment setup.
