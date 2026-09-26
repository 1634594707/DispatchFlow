# 只装 SQL 的镜像：基线 Job 与 seed Job 的载体（FROM mysql:8.4 顺带把 mysql 客户端带进去）。
# 为什么不改用 ConfigMap 挂 seed：地理 seed 实测 2.6 MB（zjf_geo.sql 1.7M + zjf_road_network.sql 784K），
# 而 etcd 对单个 ConfigMap 对象的默认上限是 1 MiB —— 不是"稍大一点"，是 apply 直接失败。
# 构建上下文是 back/sql，所以 Dockerfile 里看到的是相对路径：
#   docker build -t dispatchflow-sql:perf -f deploy/k8s/Dockerfile.sql back/sql
FROM mysql:8.4

COPY . /sql/
