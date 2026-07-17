FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml ./
COPY fsd-common/pom.xml fsd-common/pom.xml
COPY fsd-order/pom.xml fsd-order/pom.xml
COPY fsd-dispatch/pom.xml fsd-dispatch/pom.xml
COPY fsd-vehicle/pom.xml fsd-vehicle/pom.xml
COPY fsd-admin-api/pom.xml fsd-admin-api/pom.xml
COPY fsd-bootstrap/pom.xml fsd-bootstrap/pom.xml

COPY fsd-common/src fsd-common/src
COPY fsd-order/src fsd-order/src
COPY fsd-dispatch/src fsd-dispatch/src
COPY fsd-vehicle/src fsd-vehicle/src
COPY fsd-admin-api/src fsd-admin-api/src
COPY fsd-bootstrap/src fsd-bootstrap/src
# 复制 SQL 迁移脚本目录（fsd-bootstrap/pom.xml 中通过 ../sql/migrations 引用）
COPY sql/migrations sql/migrations

RUN mvn -q -DskipTests clean package

FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms1024m -Xmx1536m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/heapdump.hprof"

COPY --from=builder /build/fsd-bootstrap/target/fsd-bootstrap-0.1.0-SNAPSHOT.jar /app/fsd-core-server.jar

RUN mkdir -p /app/logs

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/fsd-core-server.jar"]
