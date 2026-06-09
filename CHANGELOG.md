# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-05-31

### Added

- Phase 12–14: trajectory replay, engine simulation, vertical (routes/hub/peak/automation rules), swap cabinets, field ops, 2FA
- Phase 15: VDA5050 MQTT adapter, REAL swap sessions, task pool server pagination (V20), Redis fleet batch read
- Flyway migration baseline, Swagger OpenAPI tags, ESLint/Prettier, JaCoCo, Prometheus metrics
- MAPF architecture evaluation and VDA5050 delivery documentation

### Changed

- Frontend version 0.3.0, Vite manualChunks, SSE connection registry
- GitHub Release **v3.0.0** supersedes v2.0.0 as latest

## [0.2.0] - 2026-05-25

### Added

- Fleet runtime layer: `FleetRuntimeService` with Redis persistence
- Simulation adapter: `SimulationFleetAdapter` decouples tick simulation from business state
- Unified energy policy: `FleetChargePolicy` and `fsd.fleet.energy.*` configuration
- Exception severity (`INFO` / `WARN` / `ERROR` / `CRITICAL`) and resolve actions
- Auto-resolve OPEN exceptions on successful reassignment
- SQL migration `V6__exception_severity.sql`
- Project documentation: architecture, deployment guide, roadmap
- GitHub Actions CI for backend tests and frontend build
- Root `docker-compose.yml` for one-command infrastructure startup

### Changed

- Vehicle monitoring reads fleet runtime from Redis instead of in-memory simulation map
- Full-charge plugged-in vehicles enter STANDBY (assignable, no drain)
- README rewritten as formal project documentation
- Branding unified to **DispatchFlow** across frontend and backend metadata

### Fixed

- Simulation charging: vehicles now charge to full SOC before leaving
- Battery drain during charging and transit to charger
- Exception spam: deduplication and INFO-level audit-only offline events
- Tracking panel layout and header overlap

## [0.1.0] - 2026-05

### Added

- Multi-park support (`t_park`, `t_station`)
- Admin dashboard, order/task/vehicle/exception management
- Full-screen vehicle tracking map with Leaflet
- Mobile park order page
- Auto dispatch, manual dispatch, vehicle state machine simulation
- RabbitMQ event publishing with Outbox pattern
- Docker Compose for MySQL, Redis, RabbitMQ, and backend

[0.3.0]: https://github.com/1634594707/DispatchFlow/compare/v2.0.0...v3.0.0
[0.2.0]: https://github.com/1634594707/DispatchFlow/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/1634594707/DispatchFlow/releases/tag/v0.1.0
