# Phase 2C — 环境修复与真实验收报告

日期：2026-10-08  
项目：`C:\Users\SK\Desktop\平台测试\ecommerce`  
范围：仅 Phase 2A/2B 环境修复、迁移/测试、前端构建、认证/RBAC/商家隔离验收。未进入 Phase 3；未部署；未连接资金渠道。

## 结论

Phase 2A/2B 的代码级和 SQLite 本地验收已通过：pytest **21 passed**；Alembic 新空库连续升级和模型漂移检查通过；三个 Vue 应用类型检查和生产构建通过；已实现商家资料/员工隔离安全测试通过。

**Phase 2 尚不能作“完整目标环境验收通过”**：本机没有 MySQL 8 或 Redis 服务，端口 3306/6379 均未监听。因此 MySQL 8 与真实 Redis 集成仍为 **BLOCKED**。未安装服务或改系统配置；需要项目方提供隔离测试实例，或明确批准本机安装方案后才能完成这两项。

## 1. 执行前状态和环境

- 操作系统：Windows 10 专业版，版本 `10.0.19045`（64 位）。
- Python：`3.11.9`；Node.js：`v24.19.0`；pnpm：`11.25.0`。npm 命令不在 PATH；使用可用 pnpm。
- 项目此前没有 `.venv`、Node 锁文件、Git 仓库；当前目录也没有 MySQL/Redis 命令或相应 Windows 服务。
- `git status --short --branch` 返回 `fatal: not a git repository`。没有创建 Git 仓库或覆盖历史。
- 执行前检查到项目源码与已有 Phase 1 文档均保留。本次新增项目 `.venv`、`node_modules`、`pnpm-lock.yaml`、`dist` 构建产物、两份 SQLite **测试专用库**（`phase2c_migration_test.db`、`phase2c_schema_check.db`）。没有清理既有锁文件/虚拟环境/数据库；检查时项目内没有原有锁文件或虚拟环境。
- 未发现真实 `.env`；前端构建产物密钥扫描未发现密钥模式。

## 2. 依赖源诊断与安装

- pip 全局配置指向 `https://pypi.tuna.tsinghua.edu.cn/simple`，并对该镜像设置了 `trusted-host`；未修改用户/系统 pip 配置。
- DNS 可解析 `pypi.org`。HTTPS 检查：官方 PyPI `/simple/pytest/` 返回 200；清华镜像请求超时。代理环境变量未设置。
- 使用 `--index-url https://pypi.org/simple`，TLS 证书正常校验；没有使用 `--trusted-host` 绕过证书检查。
- 创建新的 `apps/api/.venv`，命令：`python -m venv apps/api/.venv`；依赖安装命令：`apps/api/.venv/Scripts/python.exe -m pip install --index-url https://pypi.org/simple -e 'apps/api[test]'`。
- 首次 editable 安装发现 setuptools 把 `app` 与迁移目录 `alembic` 当成两个顶层包。修复 `pyproject.toml` 的包发现规则后，依赖安装成功。解析版本：FastAPI 0.142.4、SQLAlchemy 2.1.4、Alembic 1.20.0、pytest 8.4.2、redis-py 6.4.0、PyMySQL 1.2.3、pydantic-settings 2.15.0、Uvicorn 0.54.0。
- pnpm registry 为官方 `https://registry.npmjs.org/`。在线 workspace 安装成功，生成 `pnpm-lock.yaml`。锁文件复现命令 `pnpm install --frozen-lockfile --offline` 返回 `Already up to date`。
- 未锁定的 `latest` 最初解析到 TypeScript 7.0.2，与 `vue-tsc` 的 TypeScript 内部模块调用不兼容，真实报错 `ERR_PACKAGE_PATH_NOT_EXPORTED`。根据 `vue-tsc@3.3.12` 公布的 TypeScript peer 范围（`>=5.0.0`）和实际失败组合，三个前端统一约束为 `~5.9.3` 并更新 lockfile；这不是降低项目原来锁定版本（此前不存在锁文件），是建立已验证兼容组合。

## 3. 实际修改文件

- `apps/api/pyproject.toml`：明确 setuptools 仅打包 `app*`，避免 Alembic 目录被误当 Python 包。
- `apps/api/app/main.py`：商家身份只能匹配 `merchant:` 权限；拒绝给商家角色绑定平台权限；错误角色授权继续留审计；已有认证/商家管理 API 保持业务边界。
- `apps/api/app/models.py`、`apps/api/alembic/versions/0001_identity_rbac.py`：统一唯一字段的索引/约束定义，消除 Alembic schema drift。
- `apps/api/app/runtime.py`：提供 Redis JSON cache get/set/delete 接口；Redis required 模式下限流失败关闭。
- `apps/api/alembic.ini`：加 `path_separator = os`，消除 Alembic 路径解析弃用告警。
- `tests/test_phase2.py`：改为新临时库执行两次 Alembic upgrade；SQLite 测试连接启用外键；增加密码哈希、重复注册/员工、禁用会话、输入校验、RBAC、Redis 降级、审计和 A/B 越权测试；每个测试隔离本地限流状态。
- `apps/admin/package.json`、`apps/merchant/package.json`、`apps/storefront/package.json`、`pnpm-lock.yaml`：TypeScript 兼容性约束及可复现依赖锁。
- `apps/merchant/src/App.vue`：将模板中的全局 `fetch` 内联表达式移入脚本方法，解决 Vue TypeScript 类型错误。
- 本报告与 `docs/phase2-acceptance.md`：记录分级验收结果。

## 4. 七层验收结果

### 4.1 静态检查 — PASS

实际命令：

```powershell
.\apps\api\.venv\Scripts\python.exe -m compileall -q apps/api/app apps/api/alembic tests
pnpm install --frozen-lockfile --offline
```

结果：Python 编译检查通过；pnpm frozen lockfile 离线复装通过；三个 app 的 `vue-tsc -b` 通过；构建产物密钥模式扫描为 clean。Git 状态不可用，因为该目录不是 Git 仓库。

### 4.2 SQLite 冒烟测试 — PASS（仅本地 SQLite）

在新的 SQLite 文件上运行显式 Alembic revision，两次执行 `alembic upgrade head` 均成功。`alembic current` 为 `0001_identity_rbac (head)`；`alembic check` 输出 `No new upgrade operations detected.`。schema 含 12 张应用表、9 个外键、9 个唯一约束、29 个索引，SQLite encoding 为 UTF-8。pytest fixture 也在每个测试创建新的 SQLite 测试库并连续 upgrade 两次。

实际 Uvicorn（项目 `.venv`）启动后，HTTP `/healthz`、`/readyz`、`/api/v1/openapi.json` 均返回 200，`X-Request-ID` 正常回传。SQLite 测试启用外键；时区行为在当前 SQLite/ORM测试有覆盖，但这不代表 MySQL 时区兼容验证。

### 4.3 MySQL 集成测试 — BLOCKED

实际检查：`mysql`、`mysqld` 不在 PATH；无名称含 MySQL/MariaDB 的 Windows 服务；`127.0.0.1:3306` 连接失败。虽然 PyMySQL 驱动已安装，但它不是 MySQL Server。

因此没有执行 MySQL 8 的迁移、索引/唯一键、字符集/时区、事务隔离或重复迁移验收；没有用 SQLite 冒充 MySQL 结果。

**建议方案与需授权操作：**优先使用项目方已有的隔离 MySQL 8 测试实例。若本机安装，建议使用 MySQL 官方 Windows 64 位 MSI/Configurator，建立独立测试数据目录与服务，限制监听到本机，仅创建专用测试库和最小权限迁移账号；安装 Windows 服务/建立数据目录可能需要本机管理员授权。本次未安装或改防火墙。[MySQL 8.4 官方 Windows 安装说明](https://dev.mysql.com/doc/refman/8.4/en/windows-installation.html)

### 4.4 Redis 集成测试 — BLOCKED（降级逻辑已测）

实际检查：`redis-server`、`redis-cli` 不在 PATH；无 Redis 服务；`127.0.0.1:6379` 连接失败。redis-py 已安装。

已通过测试：Redis 未连接时 `REDIS_REQUIRED=true` 使 readiness 失败、限流拒绝；开发降级限流有效；已撤销 Session 仍经数据库校验返回未授权；Redis cache adapter 使用 fake client 做了单元级读写测试。这些不是 Redis 服务集成，不计 Redis PASS。

**建议方案与需授权操作：**优先提供现有隔离 Redis 测试地址和凭据；否则在 Windows 10 上可申请启用 WSL2 并在 Linux 环境安装 Redis，或使用 Redis 官方说明中的 Windows 兼容伙伴 Memurai。启用 WSL2/虚拟化或安装服务属于系统级操作，当前没有进行。[Redis 官方 Windows 安装说明](https://redis.io/docs/latest/operate/oss_and_stack/install/archive/install-redis/install-redis-on-windows/)

### 4.5 pytest 自动化测试 — PASS

命令：

```powershell
.\apps\api\.venv\Scripts\pytest.exe -q
```

最终结果：**21 passed, 1 warning in 7.41s**。唯一警告来自当前 Starlette TestClient 对 `httpx` 依赖的弃用提醒，不影响测试通过。覆盖管理员安全引导/重复引导、用户注册/重复注册、密码哈希、错误密码、禁用账户与令牌失效、登录限流、参数校验、修改资料、RBAC、受限管理员/消费者访问拒绝、角色权限边界、商家 A/B 档案/员工隔离、伪造 merchant ID、越权无副作用、审计、SQLite 外键/UTF-8、Alembic 新库重复 upgrade、Redis required 安全降级及 cache adapter 单元测试。

### 4.6 前端类型检查与生产构建 — PASS

项目没有声明前端单元测试脚本，故没有可执行的前端 test 命令。三个 app 的实际命令和结果：

| App | 类型检查 | 生产构建 | 产物目录 |
|---|---|---|---|
| admin | `pnpm --dir apps/admin run typecheck` PASS | `pnpm --dir apps/admin run build` PASS | `apps/admin/dist/` |
| merchant | `pnpm --dir apps/merchant run typecheck` PASS | `pnpm --dir apps/merchant run build` PASS | `apps/merchant/dist/` |
| storefront | `pnpm --dir apps/storefront run typecheck` PASS | `pnpm --dir apps/storefront run build` PASS | `apps/storefront/dist/` |

构建均有生成 HTML/CSS/JS。admin 与 merchant 主 JS bundle 大于 Vite 默认 500 KB 阈值，只有分包建议警告；storefront 没有该 warning。API 地址使用相对 `/api`，开发代理到 `127.0.0.1:8000`，生产由 Nginx `/api/` 转发。本次没有运行浏览器端到端测试。

### 4.7 商家权限安全测试 — PASS（仅当前已实现资源）

- 商家 A 读取/修改自己的商家档案，不能通过请求体、请求头切换为 B。
- A 的员工列表只返回 A；以 B 员工 ID 调用员工状态修改返回 404，数据库中 B 员工状态无变化。
- 试图将平台权限绑定到商家角色被拒绝。
- 商家员工、普通消费者、未授予平台权限的管理员不能访问平台商家管理接口；未登录请求拒绝。
- 权限拒绝产生审计记录。

尚未开发的商品、订单、库存、售后、结算等资源没有伪造接口/数据来测试。

## 5. SEC-TEN-001 至 SEC-TEN-010

| 编号 | 状态 | 依据 |
|---|---|---|
| SEC-TEN-001 | NOT IMPLEMENTED | 商品模块属于 Phase 3，当前无 API。 |
| SEC-TEN-002 | NOT IMPLEMENTED | 订单模块属于 Phase 5，当前无 API。 |
| SEC-TEN-003 | NOT IMPLEMENTED | 库存模块属于 Phase 3，当前无 API。 |
| SEC-TEN-004 | NOT IMPLEMENTED | 售后/退款属于 Phase 5，当前无 API。 |
| SEC-TEN-005 | NOT IMPLEMENTED | 商家结算属于 Phase 6，当前无 API。 |
| SEC-TEN-006 | PASS | A/B 员工列表及 A 修改 B 员工拒绝/无副作用均由 pytest 实测。 |
| SEC-TEN-007 | PASS | 伪造 body/header merchant ID 和跨租户员工 URL ID 均由 pytest 实测拒绝/隔离。 |
| SEC-TEN-008 | NOT IMPLEMENTED | 商家员工列表租户过滤已测；搜索、批量、报表和导出业务接口尚未实现。 |
| SEC-TEN-009 | NOT IMPLEMENTED | 异步业务任务及签名文件下载模块尚未实现。 |
| SEC-TEN-010 | PASS | 匿名、消费者、商家员工及受限平台管理员的拒绝路径均由 pytest 实测。 |

## 6. 当前仍未完成与整体判定

1. **MySQL 8 集成：BLOCKED**，需可用测试实例或批准本机系统级安装。
2. **Redis 集成：BLOCKED**，需可用测试实例或批准 WSL2/兼容服务安装。
3. 前端无单元测试脚本，未做浏览器 E2E；TypeScript 与生产构建已通过。
4. admin/merchant 构建 bundle 有体积警告，后续可按需要做代码分割；不是本阶段验收失败项。
5. pytest 有 1 条第三方弃用警告。

**Phase 2A+2B：代码和 SQLite/pytest/前端验收通过；整体环境验收暂不通过/待完成，原因仅为 MySQL 8 与 Redis 真实服务集成未能执行。** 不进入 Phase 3。请求项目方提供隔离 MySQL/Redis 测试端点或授权系统级安装方案后，再只补跑两项集成验收并更新结论。
