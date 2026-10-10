# Phase 3A 测试结果与阻塞项

日期：2026-10-08。只记录真实执行结果；未运行的 JUnit、MySQL 并发、模拟支付和商家 A/B 用例不标 PASS。

## 1. 分级结果

| 层级 | 已执行命令 | 结果 | 证据与限制 |
|---|---|---|---|
| 源码版本/变更 | `git rev-parse HEAD`；`$env:GIT_NO_LAZY_FETCH='1'; git status --short` | PASS（静态） | 固定 SHA `dcaa93b...`；本轮开始时状态为空，随后仅产生 Phase 3A 代码变更；完整历史对象仍缺失 |
| Java 环境 | `java -version`、`mvn -version` | BLOCKED | PowerShell 报命令未识别；未编译，也未运行 JUnit |
| 数据服务 | `mysql --version`、`redis-cli ping`、端口 3306/6379 探测 | BLOCKED | 客户端命令未识别、端口不可连接；未运行任何迁移或数据库集成测试 |
| Mapper XML 语法 | Python `xml.etree.ElementTree.parse` 解析 `PortalOrderDao.xml` | PASS（仅语法） | `mapper_xml_ok`；不证明 SQL 在 MySQL 8 上可执行 |
| 订单对象授权 | 新增 `OmsPortalOrderServiceImplSecurityTests` | NOT RUN | JDK/Maven 缺失；不能把代码用例当成执行结果 |
| 库存并发/事务回滚 | 未运行 | BLOCKED | 缺 MySQL 8。单线程或 SQLite 结果均不替代 MySQL 并发验收 |
| 模拟支付回调/重复通知 | 未运行 | BLOCKED | 缺 Java 运行环境；真实支付宝开关保持关闭 |
| 后端 API 冒烟 | 未运行 | BLOCKED | 后端未启动；无登录、商品或订单 HTTP 结果 |
| 商家 A/B 隔离 | 已形成计划，接口/商家模型未实现 | NOT IMPLEMENTED | 多商家 DDL 仅评审草案；`SEC-TEN-001` 至 `SEC-TEN-010` 均不能标 PASS |

## 2. 待执行的交易安全用例

测试库须使用专用 MySQL 8/InnoDB，初始 SKU `stock=10, lock_stock=0`，保留事务隔离级别、数据库版本和线程日志。至少运行：

1. 两个及 50/100 个线程同时预占同一 SKU，总成功数量不超过可售 10，最终 `stock>=0`、`lock_stock<=stock`。
2. 库存不足、负样例库存或已锁库存异常时预占 0 行，订单和优惠/积分操作全部回滚。
3. 预占成功后并发执行支付确认与超时/用户取消，仅一个状态迁移成功；若支付赢则库存只扣一次，若取消赢则锁定只释放一次。
4. 同一已验证支付通知重复 2/20 次，仅第一次消费库存；已取消/已关闭订单不变成已支付，迟到支付进入持久异常与退款补偿（目前未实现，预期 FAIL/阻塞）。
5. 同一 `Idempotency-Key` 并发重放只生成一笔订单，不同请求体复用同键拒绝（目前唯一请求表/接口未接入，预期未实现）。
6. 会员 A 枚举 B 的订单详情、取消、收货地址和售后申请，均拒绝且无数据副作用；伪造订单金额、商品价格、数量和回调签名/商户号/金额均拒绝。
7. 数据库事务中途注入异常，支付状态/库存/优惠/积分保持一致；模拟 Redis/RabbitMQ 故障检查下单消息与 DB 的一致性。

## 3. 未解决问题与当前验收判断

- 环境安装批准未到；没有 JDK/Maven/MySQL/Redis，后端编译、启动和并发测试均为 BLOCKED。
- GitHub promisor 缺失历史对象，`git diff --check` 会尝试取回对象并失败；源文件已保留，待网络恢复补齐后再做完整 Git 校验。
- 支付异常持久化、退款补偿、下单幂等、真实渠道查单/关单与对账尚未实现；草案 `phase3a-transaction-safety-DRAFT.sql` 未执行。
- 原 `mall.sql` 的部分样例 `lock_stock` 为负值，不能直接作为安全并发验收初始数据。
- 本轮多商家仅设计数据模型，不开发业务接口；商家 A/B 安全测试计划尚不能执行。
- 高风险越权尝试的持久审计尚未接入，不能把异常拒绝当作完整权限安全验收。

**总体结论：Phase 3A 未达到真实运行与交易安全验收条件。** 下一步依赖用户对开发环境安装范围的明确批准；本轮不启用支付或部署生产。

## 4. 2026-10-08 授权后实际结果（替代上文环境 BLOCKED 结论）

| 层级 | 最新结果 | 证据 |
|---|---|---|
| JDK/Maven/编译 | PASS | Java 17.0.20.1、Maven 3.9.16；`docs/Phase3A-maven-build-2.log`、`Phase3A-maven-build-final.log` 为 `BUILD SUCCESS` |
| JUnit | PASS，12/12 | `docs/Phase3A-maven-junit-final2.log`；0 失败、0 错误、0 跳过 |
| MySQL 8.4.11 初始化 | PASS | 独立测试库导入官方 76 表，安全增量后 80 表；事务回滚 `5→999→5` |
| Redis Server | BLOCKED | Windows 本机使用微软 Garnet 2.2.1 进行 PING/SET/GET/DEL 兼容测试 PASS；断连失败、恢复成功；不能称为真实 Redis Server 测试通过 |
| API 冒烟 | PARTIAL PASS | 后台登录/info、商品搜索/详情可用；他人订单详情/取消拒绝，取消前后 `status,delete_status` 均为 `4,0`；完整下单受 RabbitMQ 缺失阻塞 |
| 模拟支付/库存并发 | PASS（服务层范围） | 20 线程争抢 5 件仅 5 次成功，支付/释放竞争单赢家，重复支付不重复扣减，关闭订单不复活，迟到支付补偿记录唯一 |
| 商家 A/B | NOT IMPLEMENTED | 多商家业务接口及迁移未开发；不得标记 PASS |

完整证据、已修复文件和未解决项见《Phase3A最终运行验收报告.md》。**总体仍未达到完整 Phase 3A 交易安全验收条件**，尤其是渠道签名回调、退款执行和完整下单链路。
