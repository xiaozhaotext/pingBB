# Phase 3A 最终运行验收报告

日期：2026-10-08。范围：`mall-custom/mall` 官方 `macrozheng/mall` 固定提交 `dcaa93b3150352e5044708d7211b5bed0af4509f` 上的本地二创。**结论：本地编译、数据库、服务启动及已覆盖的安全测试通过；Phase 3A 整体验收暂不通过。** 没有执行生产部署、真实支付或多商家正式迁移。

## 1. 安装与隔离

| 工具 | 版本、来源 | 校验 | 运行方式 |
|---|---|---|---|
| JDK | Eclipse Temurin 17.0.20.1+1，Adoptium 官方 GitHub release | ZIP SHA256 `E53A79C3C3D86865BD7E787903884331068E71321714FFD44F145785AFFC7CB0`，与 Adoptium API 发布值一致 | `.tools/jdk17`，仅进程级 `JAVA_HOME`/`PATH` |
| Maven | Apache Maven 3.9.16，Apache 官方下载站 | ZIP SHA512 `ED41650D42485CFC243FAD22158CAF9CBB5DC408CE7A09DDB94DD42A019DE929CA43065BFA450612CF12BF78B5CAFA3884B96C090DE326FF590448C933454AF3`，与官方 `.sha512` 一致 | `.tools/maven`，仅进程级 `PATH` |
| MySQL | MySQL Community 8.4.11 Windows ZIP，Oracle CDN | 本地 ZIP SHA256 `A492371D687D2BAB088B0062581144A0044B8964BAEFDF4FAA579292B423D25C`；尚未取得官方签名文件，**只记录本地摘要** | `.tools/mysql`，数据 `.tools/mysql-data`，独立进程、`127.0.0.1:3307`，无系统服务 |
| Redis 协议兼容服务 | Microsoft Garnet 2.2.1 Windows x64 官方 GitHub release，依赖本地 .NET Runtime 8.0.31 | Garnet ZIP 本地 SHA256 `93F0026D305585CD249A91DDA6FD44C46BCAA56304115B039F74F23FE630C64A` | `.tools/garnet`，`127.0.0.1:6379`；**并非 Redis Server** |

下载地址分别为 `https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows`、`https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip`、`https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip`、`https://github.com/microsoft/garnet/releases/download/v2.2.1/win-x64-based-readytorun.zip`。未使用 Docker、管理员权限、系统级环境变量或生产账号。测试库 `mall_phase3a`；账号 `mall_test@127.0.0.1` 仅获该库权限。测试凭据在 `.tools` 私有文件中，未写入源码或报告。运行 MySQL 使用本地 `subst M:` 映射避开 Windows 中文空格路径的命令行解析问题。

## 2. 实际命令与结果

| 验收项 | 实际执行 | 结果与证据 |
|---|---|---|
| Java | `java -version`、`mvn -version` | PASS：Temurin 17.0.20.1，Maven 3.9.16 |
| 编译/打包 | `mvn -B -ntp -pl mall-portal,mall-admin -am '-DskipTests=true' '-Ddocker.skip=true' package`，后续 portal 同参数重打包 | PASS：`docs/Phase3A-maven-build-2.log`、`Phase3A-maven-build-final.log` 均有 `BUILD SUCCESS`。首次打包因官方 POM 自动连接远程 Docker `192.168.3.101:2375` 而失败；仅用 Maven 参数跳过镜像构建，没有使用 Docker |
| JUnit | 测试库环境变量覆盖默认连接后运行 `mvn -B -ntp -pl mall-portal,mall-admin -am '-DskipTests=false' '-Ddocker.skip=true' test` | PASS：12 个测试，0 失败、0 错误、0 跳过；`docs/Phase3A-maven-junit-final2.log`。首次未覆盖默认库端口时 2 个上下文测试失败，已定位并重跑通过 |
| MySQL 初始化 | `mysqld --initialize-insecure` 后立即设置随机 root 密码、建立专用库和账号；通过 `mysql` 导入 `mall/document/sql/mall.sql` | PASS：官方 SQL 导入无错误，原版 76 张表；仅在测试库执行安全增量表后为 80 张表、97 条索引记录、1 条外键。字符集 `utf8mb4`，InnoDB，隔离级别 `REPEATABLE-READ`，时区为 `SYSTEM`；未执行多商家迁移 |
| 事务回滚 | `START TRANSACTION; UPDATE pms_sku_stock SET stock=999 ...; ROLLBACK; SELECT stock ...` | PASS：测试 SKU 库存 `5 → 999 → 5` |
| 库存并发 | `PortalOrderStockConcurrencyTests` 真 MySQL 集成测试 | PASS：20 线程争抢 5 件库存恰好 5 次预占成功；支付扣减与释放 2 线程竞争仅 1 次成功；无负库存。测试结果在上述 JUnit 日志 |
| 支付状态模拟 | `PortalOrderStockConcurrencyTests.simulatedVerifiedPaymentIsIdempotentAndCanceledOrderCannotResurrect` | PASS（服务层模拟）：同一订单确认两次只扣一次库存，已取消订单无法变已支付。**未运行渠道签名回调端到端测试** |
| 迟到支付补偿记录 | `latePaymentCreatesOneDurableRefundReviewTask` | PASS（持久化）：重复模拟交易仅 1 条 `oms_payment_exception` 和 1 条 `oms_refund_compensation`，状态 `WAITING_MANUAL_REVIEW`。**未自动向任何渠道发起退款** |
| Redis 协议 | `python docs/phase3a_redis_probe.py`，停止 Garnet 后再次运行，再启动复测 | Garnet 兼容性 PASS：PING/SET/GET/DEL 正常；断连探测退出码 1，重启后恢复。真实 Redis Server 验收 **BLOCKED**，不能把 Garnet 当作 Redis PASS；断连日志 `docs/Phase3A-redis-disconnect.log` |
| 后端运行 | 本地 `java -jar` 启动 `mall-admin` 与 `mall-portal`，绑定 `127.0.0.1` | PASS（受限）：admin 8080、portal 8085 均输出 `Started ...Application`；日志见 `docs/Phase3A-admin.stdout.log`、`Phase3A-portal-final.stdout.log`。Portal 关闭 RabbitMQ listener 自动启动以隔离未授权中间件；MongoDB 连接仍告警 |
| API 冒烟 | `POST /admin/register`、`POST /admin/login`、`GET /admin/info`；`GET /product/search`、`GET /product/detail/1`；认证会员调用订单接口 | PASS（已测接口）：测试管理员注册/登录/info 均业务码 200；商品查询/详情业务码 200；他人订单详情业务码 500 拒绝、`POST /order/cancelUserOrder?orderId=12` 业务码 500，数据库状态前后均 `4,0`；客户端 `POST /order/paySuccess` 返回 HTTP 403；缺 `Idempotency-Key` 下单返回 HTTP 400，非法短键被拒绝。**真实购物车生成订单未打通** |

## 3. 本轮修复文件

相对 `mall`：`mall-portal/src/main/java/com/macro/mall/portal/controller/OmsPortalOrderController.java`、`service/OmsPortalOrderService.java`、`service/impl/OmsPortalOrderServiceImpl.java`、`service/impl/AlipayServiceImpl.java`、新增 `service/impl/PaymentExceptionRecorder.java`、新增/扩充 `src/test/java/com/macro/mall/portal/service/impl/*Tests.java`。此前 Phase 3A 改动仍在工作树：`AlipayConfig.java`、`AlipayController.java`、`OmsPortalOrderReturnApplyServiceImpl.java`、`PortalOrderDao.java/.xml`、`application.yml`。报告与测试探针在 `docs`；安全增量表的 SQL 在 `docs/phase3a-transaction-safety-DRAFT.sql`，**仅测试库已执行，不是生产迁移**。

本轮补齐：下单必须带 16–100 位幂等键；`oms_order_request(member_id,idempotency_key)` 唯一，重放原请求返回同一订单，不同请求体拒绝；订单收货和删除改为包含会员 ID、状态的条件更新；经校验的通知采用唯一交易/订单确认记录；迟到或第二笔异常支付入库，产生人工退款复核任务。支付宝开关仍默认关闭。

安全复核发现官方 `mall-common/src/main/java/com/macro/mall/common/log/WebLogAspect.java` 记录完整请求参数及响应体，导致本机早期测试日志出现测试密码和 JWT。本轮已改为仅记录请求元信息，删除参数/响应体日志字段的赋值；本轮生成的 `.log` 文件已按测试凭据及 JWT 模式脱敏，复查四组测试密钥在日志中命中数均为 0。修复后再次真实运行 `mvn ... '-DskipTests=false' '-Ddocker.skip=true' package`，`docs/Phase3A-final-verified-build-test.log` 显示编译打包成功、12/12 JUnit 通过。进一步运行 `python docs/phase3a_log_redaction_probe.py`，HTTP 登录业务码 200，`password_logged=False`、`token_logged=False`。

## 4. 未解决问题及验收判断

按用户要求的二值验收口径：**编译 PASS；JUnit PASS；MySQL 初始化 PASS；Redis Server FAIL（Garnet 兼容验证 PASS）；API 冒烟 FAIL（登录、商品和拒绝类订单接口 PASS，完整下单未通过）；模拟支付 PASS（仅服务层模拟，签名通知端到端 FAIL）；库存并发 PASS（已执行 20 线程范围）；订单越权 PASS（已实现的详情与取消接口范围）。** 二值结果不扩大各自测试覆盖范围。

1. **下单真实 API 闭环未通过。** 原工程订单超时依赖 RabbitMQ 延迟消息，当前未安装 RabbitMQ；本轮没有获准安装。幂等逻辑有 JUnit 重放测试，但未在完整购物车下单 API 中实测成功订单和并发重放。
2. **渠道与退款闭环未通过。** 无支付宝沙箱配置或可信模拟签名密钥，本轮没有执行真实签名、商户号、币种、金额的端到端通知；补偿任务仅持久化为人工复核，不自动退款。启用渠道前须完成查单/关单、退款幂等、对账与补偿任务状态机。
3. **Redis Server 未验收。** Garnet 只验证所测 RESP 命令，未覆盖 Redis 的全部语义、持久化和故障切换。MongoDB 缺失且服务日志出现连接告警，相关足迹等功能未验收。
4. **并发覆盖仍有限。** 已运行 20 线程库存测试和 2 线程支付/释放测试，尚未运行用户要求的 50/100 线程及完整 HTTP 并发下单；历史样例 SKU 存在负 `lock_stock`，不能拿样例值作为安全初始数据。
5. 商家 A/B 数据隔离、商家迁移和支付对账属于后续阶段或评审草案，状态 **NOT IMPLEMENTED**；本轮未执行正式多商家数据库迁移。
6. 官方样例 SQL 导入了演示管理员、会员和历史订单数据；当前仅在 `127.0.0.1` 独立测试环境使用。任何共享环境启用前必须清理或禁用演示身份并重建测试数据，不能复用本库作为生产库。

因此 Phase 3A 达到了**可编译、可启动、MySQL 集成测试与基础安全测试可重复运行**的阶段目标，**尚未达到完整交易安全验收条件**。维持真实支付关闭，不进入 Phase 3B；等待评审后再确定 RabbitMQ/Redis 原生服务、渠道沙箱和剩余安全闭环的授权范围。

验收结束后已用 `mysqladmin ... shutdown` 正常关闭本轮测试 MySQL 进程，并停止 Garnet、admin、portal 进程；`127.0.0.1:3307` 与 `127.0.0.1:6379` 均不再监听。临时盘符 `M:` 映射已撤销。便携工具、测试库文件、私有测试凭据和脱敏日志保留，以便复核；未删除用户文件。
