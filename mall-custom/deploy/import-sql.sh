#!/usr/bin/env bash
# 原生部署：把 init-db 下的 SQL 按文件名顺序导入 MySQL 的 mall 库
# 用法： DB_HOST=127.0.0.1 DB_USER=root DB_PASS=xxx bash import-sql.sh
set -u
DIR="$(cd "$(dirname "$0")/init-db" && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"
DB_NAME="${DB_NAME:-mall}"

echo "==> 确保数据库 $DB_NAME 存在"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` DEFAULT CHARACTER SET utf8mb4;"

echo "==> 按序导入 init-db/*.sql"
for f in $(ls "$DIR"/*.sql | sort); do
  echo "----> $f"
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$f"
done
echo "==> 导入完成"
