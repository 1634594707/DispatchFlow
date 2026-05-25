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
   mvn -pl fsd-bootstrap spring-boot:run
   ```

4. Start the frontend:

   ```bash
   cd front
   npm install
   npm run dev
   ```

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for full environment details.

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
- **SQL:** Add numbered scripts under `back/sql/init/` using `V{n}__description.sql` naming.
- **Scope:** Prefer minimal, focused changes. Do not refactor unrelated code in the same PR.

## Reporting issues

When filing an issue, include:

- Steps to reproduce
- Expected vs actual behavior
- Backend logs or browser console output if applicable
- Environment (OS, JDK/Node version, Docker or local services)

## Security

Please do not open public issues for security vulnerabilities. See [SECURITY.md](SECURITY.md).
