# DispatchFlow Deployment

## Prerequisites

- Docker Engine with Docker Compose v2
- A Linux deployment account with access to the project directory and Docker
- DNS and TLS configured for the web and mobile domains
- A production `.env` created from `.env.production`

Never commit the production `.env`, SSH credentials, AMap keys, database passwords, or application secrets.

## Deploy

1. Upload or check out the repository into `/opt/dispatchflow`.
2. Copy `.env.production` to `.env` and replace every placeholder.
3. Run `bash scripts/deploy.sh` from the repository root.
4. Confirm `fsd-backend` is healthy and the frontend container is running.
5. Confirm the Flyway schema version and execute the browser smoke tests.

```bash
cd /opt/dispatchflow
cp .env.production .env
chmod 600 .env
bash scripts/deploy.sh
docker compose -f docker-compose.prod.yml ps
curl -fsS http://127.0.0.1:8080/internal/actuator/health
curl -fsS http://127.0.0.1:8081/
```

## Release Packages

GitHub Releases trigger `.github/workflows/release.yml`, which builds the backend JAR, frontend static assets, SQL migrations, and deployment files into downloadable archives.

Container releases trigger `.github/workflows/publish-container.yml` and publish the backend image to GitHub Container Registry.

## Rollback

- Restore the previous source archive and rebuild the affected container.
- Do not delete or edit successful Flyway history rows.
- Restore the matching database backup when a data migration itself must be rolled back.
- Re-run health, API, database, and browser checks after rollback.
