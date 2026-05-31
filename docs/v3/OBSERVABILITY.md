# V3 可观测性栈

> 路线图：[`../ROADMAP-V3.md`](../ROADMAP-V3.md) · M6  
> Compose 文件：[`../../back/docker-compose.observability.yml`](../../back/docker-compose.observability.yml)

## 组件一览

| 组件 | 端口 | 用途 |
|------|------|------|
| Prometheus | 9090 | 指标采集（已有） |
| Grafana | 3001 | 指标大盘（已有） |
| Zipkin | 9411 | 分布式链路追踪 |
| Elasticsearch | 9200 | 日志索引 |
| Kibana | 5601 | 日志检索 |
| Filebeat | — | Docker 容器日志采集 |

## 启动

```bash
cd back
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

后端默认连接本机 Zipkin；启用追踪：

```bash
export MANAGEMENT_TRACING_ENABLED=true
export ZIPKIN_ENDPOINT=http://127.0.0.1:9411/api/v2/spans
cd back && ./run-dev.ps1
```

## 日志与 ELK

1. 启动应用时附加 Spring profile `json-logging`，输出 JSON 到 stdout：

   ```bash
   mvn -pl fsd-bootstrap spring-boot:run -Dspring-boot.run.profiles=json-logging
   ```

2. Filebeat 采集 Docker 容器 stdout，写入 Elasticsearch；Kibana 创建 Data View `dispatchflow-*`。

3. 普通模式日志已包含 `[traceId,spanId]`，可与 Zipkin 互跳。

## 字段加密（生产）

TOTP secret 与 Webhook secret 支持 AES-GCM 落库（前缀 `enc:v1:`），兼容历史明文：

```bash
export FSD_FIELD_ENCRYPTION_ENABLED=true
export FSD_FIELD_ENCRYPTION_KEY=<32+ char secret>
```

## 静态质量

```bash
cd back
mvn -Pquality verify
```

Checkstyle / SpotBugs 当前为 **warning 模式**（`failOnViolation=false`），便于逐步收敛。

## JaCoCo

CI 已上传覆盖率报告；本地：

```bash
mvn -pl fsd-bootstrap -am test
# 报告：back/fsd-*/target/site/jacoco/index.html
```

覆盖率目标 80% 为 M6 持续项，按模块逐步补测。
