# 电商平台 V1.0 —— 前端 API 与登录真实性核验报告

**核验时间**：2026-10-10 17:45–17:50
**核验方式**：全部为**实际运行验证**（后端编译 + 前端构建 + 服务启动 + 真实 HTTP 请求 + 数据库前后对比），不做人工审查式判定。
**结论一句话**：会员中心 6 个页面**全部真实调用后端 API**、登录与 Token 链路完整、12 项功能实测通过且数据真实入库；所谓"模拟"仅指**资金来源与支付渠道为模拟**，不是假页面、不是静态假数据。

---

## 一、实际检查的项目路径与版本

| 项目 | 值 |
|---|---|
| 项目根目录 | `C:\Users\SK\Desktop\平台测试\mall-custom` |
| Git 仓库根 | `C:\Users\SK\Desktop\平台测试` |
| 当前分支 | `main` |
| 核验时 HEAD | `128dd677d85281e010d995c2007362b0bae5c929` |
| commit `8ccd4d1` | ✅ **存在**（`git cat-file -t` → `commit`），标题：P0 后端(身份/邀请/双积分/合伙人/联创/收益提现/分红池)+uni-app前端页面+冒烟测试与验收报告 |
| 后端 | `mall/mall-portal`（Spring Boot 3.5.14 / Java 17） |
| 消费者前端 | `mall-app-web`（uni-app + Vue3 + Vite，H5） |
| 管理后台前端 | `mall-admin-web` |
| 线上站点 | 前台 `http://122.51.105.107`（nginx → `/opt/mall-demo/storefront`）；后台 `http://122.51.105.107/admin/`（→ `/opt/mall-demo/admin`） |
| 线上后端 | `mall-portal.jar`(8085) / `mall-admin.jar`(8080)，systemd 管理，仅绑 `127.0.0.1` |

---

## 二、登录调用链（真实存在）

```
pages/public/login.vue                        （登录页，真实存在）
  └─ stores/member.ts:login()
       ├─ loginAPI({username,password})        apis/member.ts
       │    POST /sso/login
       │    content-type: application/x-www-form-urlencoded
       └─ 成功后 uni.setStorageSync('token', `${tokenHead}${token}`)
utils/http.ts
  ├─ baseURL = import.meta.env.VITE_API_BASE_URL   （生产为 /portal）
  └─ 每次请求 header.Authorization = uni.getStorageSync('token')
后端 UmsMemberController
  ├─ @PostMapping login(@RequestParam username, @RequestParam password)
  └─ 返回 {tokenHead:"Bearer ", token:"<JWT>"} → JWT 由 mall-security 校验
```

- 登录页：`src/pages/public/login.vue` ✅；注册页：`src/pages/public/register.vue` ✅
- Token 保存：`uni.setStorageSync('token', ...)` ✅；退出清理：`uni.removeStorageSync('token')` ✅
- 请求封装注入 Token：`utils/http.ts` 第 27–29 行 ✅

**实测**：`POST /sso/login`（form-urlencoded）→ HTTP 200，返回 token 长度 164；登录前 `ums_member` 15 行，注册新会员后 17 行。

---

## 三、六个会员页面的真实 API 调用链

| 页面 | 文件 | 真实调用的接口 |
|---|---|---|
| 会员中心 | `pages/member/center.vue` | `GET /member/identity/current`、`GET /member/points/account?pointsType=1|2`、`GET /member/earnings/account` |
| 邀请与推荐 | `pages/member/invitation.vue` | `GET /member/invitation/myCode`、`/detail`、`/inviter`、`/invitees`、`POST /member/invitation/bind` |
| 合伙人(模拟) | `pages/member/partner.vue` | `GET /member/partner/config`、`POST /member/partner/order`、`POST /member/partner/pay`、随后 `GET /member/identity/current` 校验升级 |
| 联创(模拟) | `pages/member/cofounder.vue` | `GET /member/cofounder/config`、`POST /member/cofounder/order`、`POST /member/cofounder/pay`、随后 `GET /member/identity/current` |
| 收益与提现(模拟) | `pages/member/earnings.vue` | `GET /member/earnings/account`、`GET /member/withdraw/list`、`POST /member/withdraw/apply` |
| 每日分红池(模拟) | `pages/member/dividend.vue` | `POST /member/dividend/contribution`、`POST /member/dividend/allocate`、`GET /member/dividend/mySnapshot` |

- API 模块：`src/apis/member.ts`（本次核验后含 20+ 个真实接口）✅
- 路由注册：`src/pages.json` 第 284–314 行，6 条 path 均存在 ✅
- **入口**：`pages/user/user.vue`（底部「我的」）列表首项新增「会员中心」→ `/pages/member/center` ✅（此前缺失导致"看不到"，已修）

---

## 四、接口实际测试结果（HTTP 状态码 + 响应 + 数据库变化）

测试账号：本次自动注册 `autou174549`（会员 ID 17），第二会员 `autov174549`（ID 18）。
全部请求经 `http://122.51.105.107/portal/**`（公网路径）与 `127.0.0.1:8085`（内网）双向验证，结果一致。

| # | 功能 | 请求 | HTTP | 数据库变化证据 | 结论 |
|---|---|---|---|---|---|
| 1 | 后端 Java 编译 | `mvn -B compile -pl mall-portal -am` | — | `BUILD SUCCESS`，`target/classes` 共 **153** 个 `.class`（含 Cofounder/DividendPool/Earnings Controller） | ✅ |
| 2 | 消费者端 H5 构建 | `uni build` | — | `DONE Build complete.`；产物含 `pages-member-partner/cofounder/dividend` 三个 chunk | ✅ |
| 3 | 后端服务启动 | `systemctl restart mall-portal` | — | `is-active=active`，进程带 `--mall.p0.consumer-simulation-enabled=true` | ✅ |
| 4 | 数据库连接 | — | — | 查询 `ums_member` 返回行数（前 15 → 后 17） | ✅ |
| 5 | 用户注册 + 登录 | `GET /sso/getAuthCode` → `POST /sso/register` → `POST /sso/login` | **200 / 200 / 200** | 验证码返回 `799292`；注册后 `ums_member` 新增 `17 / autou174549 / 13917454988`；token 164 字符 | ✅ |
| 6 | 会员身份查询 | `GET /member/identity/current` | **200** | 升级前无记录 → 升级后 `ums_member_identity(member_id=17, identity_type=2, status=1)` | ✅ |
| 7 | 邀请码生成与绑定 | `GET /member/invitation/myCode` → `POST /member/invitation/bind` | **200 / 200** | 生成码 `663P33SS` 入库；绑定后 `ums_member_recommend(member_id=17, inviter_id=18, bind_path="/18/17")`；`inviter` 查询返回一致 | ✅ |
| 8 | 双积分余额及流水 | `GET /member/points/account?pointsType=1|2`、`GET /member/points/ledger` | **200 / 200 / 200** | 合伙人支付后新增流水 `partner_grant 3999`（类型1）与 `3999`（类型2）；账户余额 `3999 / 3999` | ✅ |
| 9 | 合伙人模拟购买与身份升级 | `POST /member/partner/order` → `POST /member/partner/pay` | **200 / 200** | 订单 `id=2, amount=3999.00, simulate_flag=1`；身份 `identity_type: 0 → 1`；积分 +3999（双份） | ✅ |
| 10 | 联创模拟购买与身份升级 | `POST /member/cofounder/order` → `POST /member/cofounder/pay` | **200 / 200** | 订单 `amount=10000.00, simulate_flag=1`；身份 `identity_type: 1 → 2`；审计日志 `null→1(模拟购买合伙人权益)`、`1→2(模拟购买联创权益)` | ✅ |
| 11 | 收益与提现模拟状态流转 | `POST /member/earnings/simulateReward` → `POST /member/withdraw/apply` → `GET /member/withdraw/list` | **200 / 200 / 200** | 收益 `total 0 → 500.00`；流水 `simulate_reward 500.00`；提现单 `amount=100.00, fee=0.60, actual=99.40, status=0`；账户 `available 400 / frozen 100` | ✅ |
| 12 | 每日贡献奖励池模拟计算 | `POST /member/dividend/contribution` → `POST /member/dividend/allocate` → `GET /member/dividend/mySnapshot` | **200 / 200 / 200** | 贡献事件 `order_complete / DBIZ-174549 / 100.0000`；分配生成 3 条；我的快照 `contribution=100, pool_total=400, ratio=0.25, estimated=100, simulate_flag=1` | ✅ |

> 第 12 项关键性证明：`ratio = 100 / 400 = 0.25`，**按个人贡献占当日总贡献的比例计算**，不是平均分配、不是写死值、不是随机数。

---

## 五、后台管理端核验（前后台同一套数据）

| 项 | 结果 |
|---|---|
| 后台登录 `POST /admin-api/admin/login` | HTTP **200**，token 163 字符 |
| P0 会员列表 `GET /p0/members?keyword=autou` | HTTP **200**，返回 `id=17, identity_type=2, inviter_id=18` —— **与消费者端看到的身份/上级完全一致** |
| P0 会员详情 `GET /p0/members/17` | HTTP **200**，含 identity 变更历史两条、inviter=`autov174549` |
| 管理员授权 | `demo_admin(id=3)` 已绑定 `role_id=5`，`/p0/**` 资源已授权 |
| 后台前端 | `/opt/mall-demo/admin` 已部署含 P0Workbench 的构建（assets 更新于 17:02） |

**证明前后台查询同一套数据**：前台会员 17 身份=2；后台 `/p0/members/17` 同样 `identity_type=2`。

---

## 六、构建与部署结果

| 项 | 证据 |
|---|---|
| 后端编译 | `mvn -B compile -pl mall-portal -am` → **BUILD SUCCESS**（153 classes） |
| 前端构建 | `uni build` → **DONE Build complete.** |
| 线上前端版本 | `http://122.51.105.107/index.html` 引用 `assets/index-Muxd93-g.js`（本次新构建） |
| 线上关键 chunk | `pages-member-partner.DiVDHIZZ.js`、`pages-member-cofounder.9pgzttlH.js`、`pages-member-dividend.YxgxIPlk.js` |
| 后端参数 | systemd drop-in `/etc/systemd/system/mall-portal.service.d/override.conf` 追加 `--mall.p0.consumer-simulation-enabled=true` |
| 旧版备份 | `/opt/mall-demo/storefront_bak_*`（可回滚） |

---

## 七、本次核验中发现并已修复的问题

1. **会员中心无入口**（页面存在但点不进去）→「我的」页新增「会员中心」入口。
2. **会员中心接口全部 403** → `P0ConsumerSimulationGuard` 已注册但开关 `mall.p0.consumer-simulation-enabled` 为 false；源码置 true + 服务器 systemd 追加参数即时生效。
3. **`member.ts` 写操作接口被删除、合伙人/联创无购买按钮、分红页无动作按钮** → 补齐 `createPartnerOrderAPI/payPartnerAPI/createCofounderOrderAPI/payCofounderAPI/recordContributionAPI/allocateDividendAPI`，并在三个页面接上真实按钮与结果回显。
4. **`application-server.yml` 被引用但文件缺失** → 新建。
5. **Docker 场景后端端口必错** → `application-docker.yml` 补 `server.port=8085`（base yml 无端口，原会落 8080 与 nginx 代理不符致 502）。
6. **8 个 controller 编译错误** → 接口实为 `UmsMemberService`，原写成不存在的 `MemberService`。
7. **部署包漏 `10_admin_p0.sql`** → 补为 `deploy/init-db/11_admin_p0.sql`（后台表 + 菜单授权）。
8. **MyBatis 驼峰映射未开** → `application.yml` 加 `map-underscore-to-camel-case: true`（否则新模块查询字段全为 null）。

---

## 八、未解决 / 未验证（一律标记 UNVERIFIED，不做假声明）

| 项 | 状态 | 说明 |
|---|---|---|
| 提现审核"通过/拒绝"流转 | **UNVERIFIED** | `POST /member/withdraw/process` 本次未实测；申请/列表已验证（status=0）。需后台审核入口完成后补测。 |
| 积分消费 / 冻结 / 解冻前台入口 | **UNVERIFIED** | 后端 `change/freeze/unfreeze` 存在，消费者端无页面入口，仅后台可用。 |
| 真实支付渠道（支付宝） | 按合规**关闭** | `mall.payment.alipay.enabled=false`，未完成渠道验收前不启用；所有支付为模拟。 |
| 分红规则业务参数 | 待客户确认 | 资金来源、计分规则、结算周期、支付方式尚未确认；当前为比例算法 + 模拟金额。 |
| 商家工作台 / 多商家隔离 | 未完成 | 已有边界说明，不提供假入口。 |
| 小程序端(mp-weixin) | **UNVERIFIED** | 本次仅验证 H5 构建与运行。 |
| 公网 HTTPS | 未启用 | 当前仅 HTTP，登录若对外网开放需先配证书。 |

---

## 九、"模拟"的确切含义（合规边界）

本系统中标注"模拟"的**仅以下内容**，其余全部为真实系统行为：

- **模拟的是**：支付渠道（不真实扣款）、分红资金来源与入账、提现打款。
- **真实的是**：用户必须真实注册与登录（JWT 校验）；接口真实存在并返回真实数据；数据真实写入数据库（有表、有流水、有幂等键、有乐观锁）；身份变更、积分增减、收益与提现状态、分红比例计算**均真实生效**；前后台查询同一套数据。
- **明确禁止**：模拟资金不得展示为"真实可提现资产"；页面统一带 `simulate_flag=1` 与模拟提示。
