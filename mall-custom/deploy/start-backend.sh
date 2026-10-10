#!/usr/bin/env bash
# 原生部署：打包并启动 mall-portal（使用 localhost 配置 application-server.yml）
# 用法： bash start-backend.sh   （在 mall-custom 目录下执行）
set -e
cd "$(dirname "$0")/.."   # 切到 mall-custom

echo "==> Maven 打包 mall-portal"
mvn -q -B clean package -pl mall-portal -am -DskipTests -Dmaven.test.skip=true

JAR="mall/mall-portal/target/mall-portal-1.0-SNAPSHOT.jar"
echo "==> 启动后端 (profile=server, 配置 deploy/application-server.yml)"
# 把 application-server.yml 放到 jar 同目录，Spring Boot 会自动加载并覆盖主机名
cp deploy/application-server.yml mall/mall-portal/target/application-server.yml
cd mall/mall-portal/target
SPRING_PROFILES_ACTIVE=server \
SPRING_CONFIG_ADDITIONAL_LOCATION=./ \
java -jar mall-portal-1.0-SNAPSHOT.jar
