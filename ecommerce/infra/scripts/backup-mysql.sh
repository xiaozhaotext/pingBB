#!/usr/bin/env bash
set -euo pipefail
: "${MYSQL_DATABASE:?set MYSQL_DATABASE}"
: "${MYSQL_USER:?set MYSQL_USER}"
: "${MYSQL_PASSWORD:?set MYSQL_PASSWORD}"
: "${BACKUP_DIR:=./backups}"
mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/${MYSQL_DATABASE}-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
mysqldump --single-transaction --routines --triggers -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" | gzip > "$file"
sha256sum "$file" > "$file.sha256"
echo "Backup written: $file"
