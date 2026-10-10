# 电商平台 V1.0 —— 服务器部署指南（一键部署包）

本包让你把已开发完成的「会员身份 / 邀请 / 双积分 / 合伙人 / 联创 / 收益提现 / 分红池」整套系统，
在 **一台装好 Docker 的 Linux 服务器** 上跑起来并打开浏览器查看。

> 说明：代码在上一个步骤已写完并提交（commit `8ccd4d1`）。本包只负责「部署运行」。
> 本沙箱无 JDK/Maven/Node，无法在此预编译；请在你的服务器上执行本指南的命令。

**两种部署方式（二选一）**：
- **Docker Compose（推荐，真正一键）**：见「二」。服务器只要装了 Docker 就用这个，MySQL 会自动建表，Redis/RabbitMQ/Mongo 一并带起。
- **原生 Linux（备选）**：见「四」。服务器不能装 Docker 时才用 `import-sql.sh` / `start-backend.sh` / `build-frontend.sh`。

> 另注：`deploy/demo/` 是另一套**原生 systemd 生产加固模板**（服务仅监听 127.0.0.1、独立 demo 凭据、TLS 前置、`nginx -t` 校验）。
> 它**不属于**本一键 Docker 包；如果你只想快速把系统跑起来看效果，忽略这个目录即可。

---

## 一、前置条件（服务器上）

- 一台 Linux 服务器（CentOS / Ubuntu 均可），建议 2C4G 以上
- 已安装 **Docker** 与 **Docker Compose v2**（`docker --version` / `docker compose version`）
- 开放端口：前端 `8080`、后端 `8085`（如需外网访问）
- 本项目**资金类功能全部为模拟**，不会真实扣款/打款，可放心演示

---

## 二、一键部署（Docker，推荐）

```bash
# 1) 把整个 mall-custom 目录传到服务器（例如 /opt/mall-custom）
#    如果你用 git：先解决 GitHub 推送令牌问题后 git clone，或直接 scp/打包上传

# 2) 进入部署目录
cd /opt/mall-custom/deploy

# 3) （可选）修改 .env 里的密码/端口
#    vi .env

# 4) 构建并启动（首次会下载依赖，后端镜像构建较慢，耐心等几分钟）
docker compose up -d --build

# 5) 查看状态
docker compose ps
docker compose logs -f backend     # 看后端是否起来
```

启动成功后：
- 打开浏览器访问 **http://服务器IP:8080** → 看到「优选商城」前端
- 进入「我的 → 会员中心」即可体验：身份、双积分、邀请码、合伙人/联创模拟购买、收益提现、分红池
- 后端接口基址：`http://服务器IP:8085`（前端已用 `/portal` 代理，无需手动配置）

### 常用命令
```bash
docker compose down                 # 停止
docker compose up -d                # 重启（已构建过则很快）
docker compose logs -f frontend     # 看前端日志
docker exec -it mall-db mysql -uroot -p mall   # 进数据库
```

---

## 三、数据库说明（已内置，无需手动建表）

`deploy/init-db/` 目录已按执行顺序放好 SQL，MySQL 容器首次启动会自动执行：
- `01_mall.sql`：mall 基础表（会员、商品、订单等）
- `02~10_*.sql`：本次新增模块表（身份/邀请/双积分/合伙人/联创/收益提现/系统日志/分红池/商城体验），含乐观锁、幂等键、`simulate_flag=1` 模拟标记
- `11_admin_p0.sql`：P0 管理后台表 `p0_points_adjust_request`（积分调整申请，带幂等键），
  并给 role 5 授权 `/p0/**` 资源与「会员运营」菜单（会员/合伙人/联创/双积分/财务中心/贡献奖励池/系统配置）。
  脚本无 DROP，仅 `INSERT ... WHERE NOT EXISTS`，可重复执行。
  若你的后台登录账号不是 role 5，请改本文件里的 `role_id`，或改 `P0AdminAccess` 的 `mall.p0.admin-role-id`。

如需重置数据库：`docker compose down -v` 会删除数据卷，下次启动重新初始化。

---

## 四、原生 Linux 部署（不用 Docker，备选）

适合服务器不能装 Docker 的情况。需自行安装：**JDK17、MySQL8、Redis7、RabbitMQ、MongoDB**。

1. **建库**：用 `deploy/import-sql.sh` 把 `init-db/*` 按顺序导入 MySQL 的 `mall` 库。
2. **后端**：`bash deploy/start-backend.sh`
   - 脚本会 `mvn clean package -pl mall-portal -am -DskipTests` 打 jar，
   - 并用 `deploy/application-server.yml`（把主机名改成 `localhost`）以 `server`  profile 启动。
   - 请按需修改 `application-server.yml` 里的 MySQL/Redis 账号密码。
3. **前端**：`bash deploy/build-frontend.sh`
   - `npm install && npm run build:h5`，产物在 `mall-app-web/dist/build/h5`。
   - 用 nginx 托管该目录，并把 `/portal` 反向代理到 `http://127.0.0.1:8085/`（参考 `deploy/nginx.conf`）。
   - 生产环境 `.env.production` 已设 `VITE_API_BASE_URL=/portal`，无需改动。

---

## 五、验证是否跑通

```bash
# 后端是否健康
curl http://localhost:8085/sso/info
# 前端是否能打开（返回 HTML）
curl -s http://localhost:8080 | head -5
```

也可使用之前提供的接口冒烟脚本（需先注册两个测试会员拿到 token）：
```bash
BASE=http://服务器IP:8085 TOKEN_A=xxx TOKEN_B=yyy bash mall-custom/docs/p0-api-smoke-test.sh
```

---

## 六、常见问题

- **后端一直起不来**：先看 `docker compose logs backend`。多半是 DB/Redis 未就绪或密码不对（改 `.env` 与 `application-docker.yml` 保持一致）。
- **页面打不开 / 接口 404**：确认前端访问的是 `:8080`，且 nginx 已把 `/portal` 代理到后端 `:8085`。
- **logstash 报错**：仅为日志收集告警，不影响业务；如需消除，可在 `application-docker.yml` 把 `logstash.host` 指向真实服务或忽略。
- **想换域名/HTTPS**：在 nginx 前加一层反代或证书即可，业务无需改动。
