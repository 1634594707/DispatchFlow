# Deployment Guide

## Prerequisites

| Component | Version |
|-----------|---------|
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 18+ |
| Docker | 24+ (recommended) |

## Option A — Docker Compose (recommended)

From the repository root:

```bash
docker compose up -d
```

This starts MySQL, Redis, RabbitMQ, and the backend API (port **8080**).

Start the frontend separately:

```bash
cd front
npm install
npm run dev
```

Open `http://localhost:3000`.

### Default ports

| Service | Host port |
|---------|-----------|
| MySQL | 3307 |
| Redis | 6380 |
| RabbitMQ AMQP | 5673 |
| RabbitMQ Management | 15673 |
| Backend API | 8080 |
| Frontend dev | 3000 |

Copy [.env.example](../.env.example) to `.env` to override host ports.

## Option B — Local development

### 1. Start infrastructure only

```bash
docker compose up -d mysql redis rabbitmq
```

### 2. Database initialization

On **first** MySQL container start, scripts in `back/sql/init/` run automatically (V1–V9).

For an **existing** database, apply missing migrations manually (example V6 / V7):

```bash
docker cp back/sql/init/V6__exception_severity.sql fsd-mysql:/tmp/V6.sql
docker exec fsd-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 fsd_core < /tmp/V6.sql"
docker cp back/sql/init/V7__parking_slot_and_charging_pile.sql fsd-mysql:/tmp/V7.sql
docker exec fsd-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 fsd_core < /tmp/V7.sql"
docker cp back/sql/init/V8__charging_session.sql fsd-mysql:/tmp/V8.sql
docker exec fsd-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 fsd_core < /tmp/V8.sql"
docker cp back/sql/init/V9__road_network.sql fsd-mysql:/tmp/V9.sql
docker exec fsd-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 fsd_core < /tmp/V9.sql"
```

Always use `--default-character-set=utf8mb4` when importing SQL with Chinese content.

### 3. Start backend

```bash
cd back
mvn -pl fsd-bootstrap -am clean install -DskipTests
mvn -pl fsd-bootstrap spring-boot:run
```

Windows:

```powershell
cd back
.\run-dev.ps1
```

> Always use `-pl fsd-bootstrap -am`. Running from the parent POM without `-am` may load stale module JARs and cause `/api/admin/park/*` to return 404.

### 4. Start frontend

```bash
cd front
npm install
npm run dev
```

## Verification

| Check | URL / command |
|-------|---------------|
| Health | `GET http://localhost:8080/api/health` |
| Swagger | `http://localhost:8080/swagger-ui.html` |
| Dashboard | `http://localhost:3000/dashboard` |
| Tracking map | `http://localhost:3000/vehicle-tracking` |
| Mobile order | `http://localhost:3000/mobile/order` |

## Production notes

DispatchFlow ships without built-in authentication. Before exposing to a network:

- Add API gateway or Spring Security
- Use secrets management for DB/Redis/RabbitMQ credentials
- Configure CORS for your frontend origin
- Replace simulation adapter with real vehicle telematics when available

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Park API 404 | Stale JAR / wrong Maven target | `mvn -pl fsd-bootstrap -am clean install` then restart |
| Empty vehicle map | Redis not running | Start Redis; restart backend |
| Chinese garbled in DB | Wrong charset on import | Re-import with `utf8mb4`; run V5 fix script |
| Exceptions missing severity | V6 not applied | Run V6 migration manually |
