# Product Roadmap

## v0.2 — Fleet domain integration (current)

- [x] Fleet runtime abstraction with Redis persistence
- [x] Simulation adapter decoupled from business layer
- [x] Unified charge policy and energy configuration
- [x] Exception severity, deduplication, auto-resolve on reassignment
- [ ] Dispatch workbench UI (task pool + quick exception actions)
- [ ] Task ↔ OPEN exception linked query API

## v0.3 — Dispatch engine & real-time monitoring

- [ ] Charging slot occupancy model
- [ ] WebSocket / SSE for live vehicle positions
- [ ] Scoring-based dispatch (distance + SOC + idle time)
- [ ] Exception severity badges in admin UI

## v0.4 — Operations & integration

- [ ] Real vehicle telematics adapter (replace simulation)
- [ ] Operate log read API and detail page history
- [ ] Role-based access control and audit trail
- [ ] Operations reports and export

## v1.0 — Production readiness

- [ ] Authentication and authorization
- [ ] Flyway-managed migrations
- [ ] Production Docker images (frontend + backend)
- [ ] Testcontainers integration test suite
- [ ] OpenAPI spec export and versioning

See [ARCHITECTURE.md](ARCHITECTURE.md) for technical design details.
