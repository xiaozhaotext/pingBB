# Ecommerce V1.0 foundation (Phase 2A + 2B)

This repository implements only engineering foundations, identities, RBAC, merchant isolation, and basic workbenches. Product transactions, cart/checkout, payments/refunds/settlement, membership purchases, commissions, points, and withdrawals are intentionally not implemented.

## Requirements

- Python 3.11+
- Node.js 20+ and pnpm (or npm)
- MySQL 8 for deployed use; SQLite is supported only for local development/tests
- Redis 6+ recommended. With `REDIS_REQUIRED=false`, the API falls back to a bounded per-process limiter; this is not a distributed production rate limiter. For deployment set `REDIS_REQUIRED=true`.

## API

```powershell
cd apps/api
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e '.[test]'
$env:DATABASE_URL='sqlite:///./ecommerce-dev.db'
$env:BOOTSTRAP_TOKEN='paste-a-random-32-byte-or-longer-value'
alembic upgrade head
uvicorn app.asgi:app --reload
```

OpenAPI: `http://127.0.0.1:8000/api/v1/docs`. The initial platform administrator is created once through `POST /api/v1/admin/bootstrap` using the bootstrap token and a password of at least 16 characters; bootstrap is disabled when no token is configured. No default credentials exist.

For MySQL set `DATABASE_URL=mysql+pymysql://...` (URL-encode reserved characters). Use `alembic upgrade head`; never create production tables with `create_all`.

## Frontends

From the project root, install the frontend workspace with `pnpm install`, then use `pnpm --dir apps/admin dev`, `pnpm --dir apps/merchant dev` or `pnpm --dir apps/storefront dev`. Admin and merchant apps use Element Plus; consumer account app uses Vant. Configure the dev server proxy to API `/api` before using the login pages (frontend proxy configuration is in `vite.config.ts`). Use separate origins/storage keys for admin, merchant and consumer identities.

## Tests

```powershell
cd ecommerce
python -m pip install -e 'apps/api[test]'
$env:PYTHONPATH='apps/api'
pytest
```

Tests use isolated SQLite databases and never claim MySQL/Redis server verification. SEC-TEN-001..010 status is in `docs/phase2-acceptance.md`.

## Deployment preparation (not deployed)

See `infra/nginx`, `infra/systemd`, `infra/logrotate`, `infra/scripts/backup-mysql.sh`, and `docs/operations.md`. Keep real credentials outside the repository. Configure TLS at the deployment edge. Restore a backup into an isolated database and verify it before any recovery action.
