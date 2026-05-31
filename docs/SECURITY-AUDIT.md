# 安全基线审计（2026-05-31）

> Phase 15 技术债务收尾 · 首版 checklist，非渗透测试报告。

## 1. 认证与凭证

| 项 | 现状 | 建议 |
|----|------|------|
| 管理员密码 | BCrypt（`AdminAuthServiceImpl`） | ✅ 保持 |
| 会话 Token | UUID，`X-Admin-Token` Header | 生产 HTTPS；可设 TTL / 轮换 |
| TOTP 2FA | `dev.samstevens.totp` | Secret 存 DB 明文；**Phase 16** 考虑字段级加密或 KMS |
| 车辆网关凭证 | `t_vehicle_credential` | 禁用/轮换 API 已有；生产禁止默认密钥 |
| Open API Key | `t_external_api_key` | 仅存 hash；UI 一次性展示 |

## 2. SQL 注入

| 层 | 结论 |
|----|------|
| MyBatis-Plus | 默认 `#{}` 参数化；Admin 查询 DTO 经 Wrapper 构建 |
| 动态 SQL | 无字符串拼接 `${}` 于用户输入路径（抽检） |
| 建议 | 新增 raw SQL 必须 Code Review；可选 SpotBugs / SQL 静态规则 |

## 3. XSS

| 层 | 结论 |
|----|------|
| Vue 3 模板 | 默认转义 |
| 用户 remark / 日志 | 前端 `v-html` 未用于运营输入（抽检） |
| PDF / 邮件导出 | 服务端生成二进制，无 HTML 注入面 |
| 建议 | 富文本若引入，使用 DOMPurify |

## 4. 敏感配置

| 项 | 建议 |
|----|------|
| `DB_PASSWORD` / Redis | 环境变量 / Docker secrets，勿提交仓库 |
| MQTT / Webhook | 配置项走 env；VDA5050 broker TLS 生产启用 |
| `front/scripts/perf/.auth/` | 已 gitignore，含本地 token |

## 5. 传输与暴露面

| 端点 | 说明 |
|------|------|
| `/api/admin/**` | 需 `X-Admin-Token`（除 login） |
| `/api/open/v1/**` | API Key |
| `/api/vehicle-gateway/**` | 车辆凭证 |
| Actuator | 默认仅 `health,info,prometheus`；生产禁公网暴露 prometheus |
| Swagger | 生产可关 `springdoc.swagger-ui.enabled=false` |

## 6. 后续（Phase 16+）

- [ ] 字段级加密（TOTP secret、Webhook signing secret）
- [ ] 依赖漏洞扫描（OWASP Dependency-Check / GitHub Dependabot）
- [ ] RBAC 细粒度审计日志留存策略
