# DispatchFlow

DispatchFlow is a dispatch system prototype for autonomous vehicle delivery in park and short-haul logistics scenarios.

It focuses on the backend core loop instead of only UI presentation:

- order creation
- dispatch task generation
- auto assignment
- vehicle state machine execution
- low-battery charging fallback
- event-driven task lifecycle with RabbitMQ
- monitoring screen and mobile ordering linkage

## Why this project matters

This project is built to simulate a smaller version of a real autonomous delivery dispatch platform.

The key point is not “a car moves on a map”, but that the backend has a real business chain:

`order -> dispatch task -> vehicle assignment -> execution report -> task completion/failure -> standby/charging`

## Core Features

### 1. Order-driven dispatch

Mobile or admin-side ordering does not operate vehicles directly.

The backend first:

1. creates an order
2. creates a dispatch task
3. auto-assigns a vehicle

Key code:

- [ParkPilotCommandServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java)

### 2. Auto assignment and nearest-vehicle selection

Task assignment includes:

- task-level lock protection
- assignable vehicle filtering
- nearest vehicle selection by route distance
- fallback to manual pending if auto assignment fails

Key code:

- [DispatchTaskServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java)
- [ParkPilotServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotServiceImpl.java)

### 3. Vehicle state machine

Vehicles run through multiple stages instead of finishing instantly:

- `STANDBY`
- `TO_PICKUP`
- `LOADING`
- `TO_DROPOFF`
- `UNLOADING`
- `TO_CHARGING`
- `CHARGING`
- `RETURNING_TO_STANDBY`
- `OFFLINE`

After delivery, the system evaluates battery level and decides whether the vehicle:

- returns to standby
- or goes to charging

Key code:

- [ParkPilotSimulationServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java)

### 4. Vehicle-report-driven task transition

Task state changes are driven by vehicle execution reports:

- `START_EXECUTE`
- `TASK_SUCCESS`
- `TASK_FAILED`

This makes the design closer to a real device integration model.

Key code:

- [VehicleReportServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java)

### 5. RabbitMQ + Outbox event-driven architecture

Task creation, assignment, execution, success, failure, and exception handling all publish domain events.

Instead of sending MQ messages directly inside business logic, the project uses:

- RabbitMQ
- outbox persistence
- after-transaction publish
- retry scheduler for failed events

Key code:

- [RabbitDispatchEventPublisher](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java)
- [DispatchEventOutboxServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventOutboxServiceImpl.java)
- [DispatchEventRetryScheduler](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventRetryScheduler.java)

## Tech Stack

### Frontend

- Vue 3
- TypeScript
- Vite
- Ant Design Vue
- Leaflet

### Backend

- Java 21
- Spring Boot 3.3
- MyBatis-Plus

### Middleware

- MySQL
- Redis
- RabbitMQ

## Repository Structure

```text
DispatchFlow/
├─ front/    Vue frontend, monitoring screen and mobile ordering page
├─ back/     Java multi-module backend
└─ README.md
```

Backend modules:

- `fsd-common`: common models, enums, exceptions
- `fsd-order`: order domain
- `fsd-dispatch`: dispatch domain, state machine, events, park simulation
- `fsd-vehicle`: vehicle domain and vehicle reports
- `fsd-admin-api`: admin-facing aggregated APIs
- `fsd-bootstrap`: application entry module

## Main APIs

### Park monitoring

- `GET /api/admin/park/layout`
- `GET /api/admin/park/stations`
- `GET /api/admin/park/vehicles`
- `GET /api/admin/park/orders`

### Park ordering

- `POST /api/admin/park/orders`

Example:

```json
{
  "externalOrderNo": "PARK-DEMO-001",
  "pickupStationId": 101,
  "dropoffStationId": 201,
  "priority": "P1",
  "remark": "mobile order"
}
```

## Local Run

### Requirements

- Node.js 18+
- JDK 21
- Maven 3.9+
- MySQL
- Redis
- RabbitMQ

### Default dependency ports

Backend defaults:

- MySQL: `127.0.0.1:3307`
- Redis: `127.0.0.1:6380`
- RabbitMQ: `127.0.0.1:5673`

Main config:

- [application.yml](back/fsd-bootstrap/src/main/resources/application.yml)

### Start backend

The backend root `pom.xml` is an aggregator. Run the bootstrap module instead of running directly at `back/`.

```bash
cd back
mvn -pl fsd-bootstrap -am spring-boot:run
```

Default backend URLs:

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/api-docs`

### Start frontend

```bash
cd front
npm install
npm run dev
```

Default frontend URL:

- `http://localhost:3000`

## Tests

Recommended commands:

```bash
cd back
mvn -pl fsd-admin-api -am test
mvn -pl fsd-bootstrap -am test
```

## Best code entry points

If someone wants to understand the project quickly, this is the recommended reading order:

1. [AdminDispatchController](back/fsd-admin-api/src/main/java/com/fsd/admin/controller/AdminDispatchController.java)
2. [ParkPilotCommandServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java)
3. [DispatchTaskServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java)
4. [ParkPilotSimulationServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java)
5. [VehicleReportServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java)
6. [RabbitDispatchEventPublisher](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java)

## Project Positioning for Backend Roles

This repository is best presented as:

“A backend-oriented autonomous vehicle dispatch prototype with order-driven task generation, nearest-vehicle assignment, state-machine-based vehicle execution, low-battery charging fallback, and RabbitMQ-based event-driven lifecycle handling.”

That framing is much stronger than presenting it as a simple visualization project.
