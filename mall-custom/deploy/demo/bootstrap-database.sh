#!/usr/bin/env bash
set -euo pipefail

if mysql -NBe "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='mall_demo'" | grep -Fxq mall_demo; then
  echo 'mall_demo already exists; refusing to overwrite it' >&2
  exit 1
fi

install -d -m 700 /etc/mall-demo
db_password="$(openssl rand -hex 24)"
mysql <<SQL
CREATE DATABASE mall_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'mall_demo'@'127.0.0.1' IDENTIFIED BY '${db_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON mall_demo.* TO 'mall_demo'@'127.0.0.1';
SQL
printf 'SPRING_DATASOURCE_PASSWORD=%s\n' "$db_password" > /etc/mall-demo/db-password.env
chmod 600 /etc/mall-demo/db-password.env
unset db_password

mysql mall_demo < /root/mall-demo-staging/mall-demo-export.sql
mysql -NBe "SELECT COUNT(*) AS tables_count FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mall_demo'"
