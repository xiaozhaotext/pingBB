# Operations notes

## Apply migrations

1. Back up the target database.
2. Set DATABASE_URL in a protected environment file.
3. From `apps/api`, run `alembic upgrade head`.
4. Verify `alembic current` and `/readyz`.

## Backup and restore

Run `infra/scripts/backup-mysql.sh` with MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD and BACKUP_DIR set in a protected shell environment. Verify the SHA256 file. Restore only into an isolated target first, e.g. `gzip -dc backup.sql.gz | mysql isolated_db`; compare row counts and application readiness before any approved recovery. Never test restore over production.

## Production preparation

The included Nginx/systemd files are examples only. Configure TLS, protected environment file permissions, dedicated OS/database users, firewall rules, monitoring and a tested restore process before deployment. No deployment was performed.
