# Docker Deploy

在 `DISPATCHFLOW` 目录执行：

```bash
docker compose up --build
```

启动内容：

- `mysql` on `3307`
- `redis` on `6380`
- `rabbitmq` on `5673`
- `rabbitmq management` on `15673`
- `fsd-core-server` on `8080`

初始化说明：

- MySQL 容器会自动执行 `sql/init` 目录中的：
  - `V1__init_schema.sql`
  - `V2__dispatch_constraints.sql`
  - `V3__dispatch_event_outbox.sql`

常用地址：

- Health: `http://localhost:8080/api/health`
- Swagger: `http://localhost:8080/swagger-ui.html`
- RabbitMQ Console: `http://localhost:15673`

如需改宿主机端口，可在启动前设置环境变量：

- `MYSQL_HOST_PORT`
- `REDIS_HOST_PORT`
- `RABBITMQ_HOST_PORT`
- `RABBITMQ_MANAGEMENT_HOST_PORT`
- `APP_HOST_PORT`

停止：

```bash
docker compose down
```

清理数据卷：

```bash
docker compose down -v
```
