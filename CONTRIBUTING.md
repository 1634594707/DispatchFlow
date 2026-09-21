# Contributing to DispatchFlow

Thank you for your interest in contributing to DispatchFlow.

## Development setup

1. Clone the repository and install prerequisites (JDK 21, Maven 3.9+, Node.js 18+).
2. Start infrastructure:

   ```bash
   docker compose up -d mysql redis rabbitmq
   ```

3. Start the backend:

   ```bash
   cd back
   mvn -pl fsd-bootstrap -am clean install -DskipTests
   bash ../scripts/dev/run-backend-local.sh
   ```

   Do not run `mvn spring-boot:run` directly on the host: `application.yml` ships deliberately
   wrong host defaults (`DB_PASSWORD=changeme`, `RABBITMQ_USERNAME=fsd_user`, port 5673) so a
   misconfigured instance fails loud instead of connecting somewhere unexpected, while the local
   containers use another set of values. `scripts/dev/run-backend-local.sh` aligns them and
   refuses to start against any non-loopback address. `docker compose up backend` needs nothing
   extra because the compose file injects the variables itself.

4. Start the frontend:

   ```bash
   cd front
   npm install
   npm run dev
   ```

See [README.md](README.md) for environment, startup, and quality gate details.

## Branch workflow

1. Create a feature branch from `main`: `feat/your-topic` or `fix/your-topic`.
2. Keep commits focused and write clear commit messages (`feat:`, `fix:`, `docs:`, `refactor:`).
3. Run tests before opening a pull request:

   ```bash
   cd back && mvn -pl fsd-bootstrap -am test
   cd front && npm run build
   ```

4. Open a pull request against `main` with a concise summary and test plan.

## Code conventions

- **Backend:** Follow existing module boundaries (`fsd-order`, `fsd-dispatch`, `fsd-vehicle`). Domain logic belongs in service layers, not controllers.
- **Frontend:** Vue 3 Composition API, TypeScript, Ant Design Vue. Match existing naming in `src/views/` and `src/api/`.
- **SQL:** Flyway migrations are numbered `V{n}__description.sql` under `back/sql/migrations/`; `back/sql/init/` only holds the container entry-point script that seeds the pre-Flyway baseline.
- **Environment variables:** anything carrying a real credential or per-environment value (keys, passwords, tokens) needs a placeholder line in both `.env.example` and `.env.production`; the real value goes only into a local `.env` and the server's `.env`, never into a tracked file. Pure tuning knobs stay YAML-only with their default in `application.yml`.
- **Scope:** Prefer minimal, focused changes. Do not refactor unrelated code in the same PR.

### Migration discipline

Flyway baselines this schema at V20 (`baseline-version: 20` in `application.yml`), so V21 and above are ordinary applied migrations with recorded checksums.

- **Never edit an applied migration.** Changing one makes `flyway.validate` fail with *"Migration checksum mismatch"* and the backend refuses to start (`FLYWAY_ENABLED=true`). Express the change as a new migration instead; use `back/sql/init/00-run-migrations.sh`'s V01–V20 range only for the baseline, never for content later than that.
- **Migrations are DDL only.** Park geometry (fences, stations, slots, road network) is seeded content and belongs in `back/sql/seed/` as idempotent upserts keyed on business codes, not in `V*.sql`. Iterating map content through migrations is what produced four rounds of "fix/snap/recalibrate/reset" over the same coordinates.
- Verify before pushing:

  ```bash
  docker exec fsd-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -D fsd_core \
    -e "SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL;"' \
    | node scripts/check-migration-checksums.js
  ```

## Reporting issues

When filing an issue, include:

- Steps to reproduce
- Expected vs actual behavior
- Backend logs or browser console output if applicable
- Environment (OS, JDK/Node version, Docker or local services)

## Security

Please do not open public issues for security vulnerabilities. See [SECURITY.md](SECURITY.md).
