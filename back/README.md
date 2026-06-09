# DispatchFlow Backend

Java 21 / Spring Boot 3 modular monolith for autonomous vehicle dispatch in campus logistics scenarios.

## Modules

| Module | Description |
|--------|-------------|
| `fsd-common` | Shared enums, exceptions, API response models |
| `fsd-order` | Order domain |
| `fsd-dispatch` | Dispatch tasks, fleet runtime, simulation, exceptions, events |
| `fsd-vehicle` | Vehicle occupancy and execution reports |
| `fsd-admin-api` | Admin REST API aggregation |
| `fsd-bootstrap` | Application entry point |

## Quick start

```bash
# Infrastructure (from repo root)
docker compose up -d mysql redis rabbitmq

# Backend
mvn -pl fsd-bootstrap -am clean install -DskipTests
mvn -pl fsd-bootstrap spring-boot:run
```

Windows: `.\run-dev.ps1`

## API documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Database

Initialization: `sql/migrations/V01__init_schema.sql` through `V11__admin_user.sql`, applied by `sql/init/00-run-migrations.sh` on first Docker MySQL start.

Fresh Docker MySQL containers apply all scripts automatically. See the root [README.md](../README.md) for startup, configuration, and quality gate details.

## Tests

```bash
mvn -pl fsd-bootstrap -am test
```

## Docker

Full stack including backend image:

```bash
docker compose up -d
```

Compose file: [docker-compose.yml](docker-compose.yml)
