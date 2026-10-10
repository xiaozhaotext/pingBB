# Phase 3A 支付、订单与库存安全整改报告

状态：**代码整改已部分落地，未完成 Java 编译、MySQL 并发和支付模拟验收；不得用于真实交易。** 固定上游为 `macrozheng/mall@dcaa93b3150352e5044708d7211b5bed0af4509f`。本轮未调用真实支付、未部署、未执行数据库迁移。

## 1. 已修改的官方源码

| 文件 | 改动与目标 |
|---|---|
| `mall/mall-portal/src/main/java/com/macro/mall/portal/controller/OmsPortalOrderController.java` | `paySuccess`、全局超时关单和任意延迟消息入口返回 403；用户取消改走有归属校验的方法 |
| `mall/mall-portal/src/main/java/com/macro/mall/portal/controller/AlipayController.java` | 所有支付宝入口默认关闭，未明确启用时 503；异步通知明确输出文本响应 |
| `mall/mall-portal/src/main/resources/application.yml` | 白名单从 `/alipay/**` 缩为仅 `/alipay/notify`；支付发起和查单需登录 |
| `mall/mall-portal/src/main/java/com/macro/mall/portal/config/AlipayConfig.java` | 增加收款方 `sellerId` 校验配置，未配置不接受成功通知 |
| `mall/mall-portal/src/main/java/com/macro/mall/portal/service/impl/AlipayServiceImpl.java` | 发起支付只用数据库应付额，并核对当前会员与待付订单归属；通知校验 SDK 签名、应用 ID、收款方、订单号、交易号、金额和可用币种；重复已支付通知不再调用库存扣减；查单不再由客户端触发状态变更 |
| `mall/mall-portal/src/main/java/com/macro/mall/portal/service/OmsPortalOrderService.java`、`impl/OmsPortalOrderServiceImpl.java` | 订单详情、用户取消与收货地址检查归属；待付→已付/已关使用条件更新；重复支付通知不重复扣库存；库存预占/扣减/释放逐 SKU 校验更新行数，失败抛异常回滚 |
| `mall/mall-portal/src/main/java/com/macro/mall/portal/dao/PortalOrderDao.java`、`mall/mall-portal/src/main/resources/dao/PortalOrderDao.xml` | 新增原子 `reserveSku`、`consumeReservedSku`、`releaseReservedSku` 与订单条件状态迁移 SQL |
| `mall/mall-portal/src/main/java/com/macro/mall/portal/service/impl/OmsPortalOrderReturnApplyServiceImpl.java` | 售后申请核对订单属于当前会员、商品属于订单，价格和商品快照来自数据库，不接受请求体伪造 |
| `mall/mall-portal/src/test/java/com/macro/mall/portal/service/impl/OmsPortalOrderServiceImplSecurityTests.java` | 增加跨用户详情/取消、重复支付、已取消订单支付的 JUnit/Mockito 用例；**尚未执行** |

## 2. 当前状态转换与库存行为

| 操作 | 代码前置条件 | 数据库行为 | 失败处理 |
|---|---|---|---|
| 下单预占 | 购物车非空、地址属于当前会员、SKU 库存有效 | `UPDATE pms_sku_stock SET lock_stock=lock_stock+q WHERE stock-lock_stock>=q AND lock_stock>=0`；当前 `generateOrder` 接口标注 `@Transactional` | 任一 SKU 更新 0 行抛异常，整笔数据库事务回滚 |
| 确认支付 | 仅可信回调服务内部调用；订单 `status=0`，支付类型有效 | 条件更新 `status 0→1`，逐 SKU 将 `stock` 与 `lock_stock` 同减，且要求两者均足够 | 任一 SKU 不满足则抛异常回滚；`status=1/2/3` 重复确认返回 0，不再扣库存 |
| 用户取消/超时取消 | 订单 `status=0`；用户操作先验证所有者 | 条件更新 `status 0→4`，逐 SKU 减 `lock_stock`；与支付确认争夺同一订单行 | 支付先提交则更新 0 行，不释放；库存异常则回滚 |
| 已取消/已关闭迟到通知 | 支付回调经签名、商户、金额校验；订单不再待付 | 不复活订单、不扣库存 | 当前仅记录错误日志并返回 `failure`；**缺持久异常单与自动退款补偿，未达到上线要求** |
| 支付渠道启用期间的自动关单 | 尚无可证实的渠道查单/关单确认 | 自动取消暂不执行，避免释放可能仍有效的库存预占 | 必须先完成渠道适配与异常流程，不能通过打开开关直接上线 |

数据库行条件更新在 InnoDB 中提供单行原子性；事务注解依赖 Spring 代理生效。**这只是源码级结论，尚未做 MySQL 并发、故障注入和事务回滚实测。** 旧的批量 `updateSkuStock`、`updateOrderStatus`、`releaseSkuStockLock` DAO 映射保留但当前调用点已迁移；后续评审需明确废弃它们，防止新代码误用。

## 3. 尚未闭环的安全要求

1. 重复下单缺持久的 `(member_id, Idempotency-Key)` 唯一请求记录；草案见 `phase3a-transaction-safety-DRAFT.sql`，尚未执行或接入接口。
2. 迟到支付只返回 `failure` 并写日志，没有持久异常单、渠道退款请求、补偿重试和人工审核账本；不能声称退款补偿完成。
3. 回调验签依赖支付宝 SDK，但尚未在沙箱用真实渠道字段组合验证 `seller_id`、金额、币种与交易号格式；`mall.payment.alipay.enabled` 默认 false，不得在当前版本改为 true。
4. 订单确认前的渠道支付尝试、超时查单/关单、通知事件唯一键、退款幂等和对账仍需后续实现；当前自动关单在渠道启用时故意停止。
5. 售后申请仍需累计可退数量与金额限制、重复申请幂等、独立退款单；本轮只封堵跨用户和价格伪造。
6. 商家 A/B 隔离尚无数据模型或 API，不得把本轮会员级对象授权解释为商家隔离通过。
7. 跨用户拒绝目前抛出业务异常，但高风险越权审计的持久记录与请求 ID 关联尚未接入；需在商家授权阶段补齐。

## 4. 必须执行的后续测试

在专用 MySQL 8 测试库和模拟支付渠道上执行：并发 50/100 请求抢同一 SKU、重复请求、库存刚好不足、事务中途失败、支付/取消并发、超时释放/支付回调竞争、重复通知、晚到支付、伪造金额/商户号/签名、跨用户订单/售后。比较 `stock`、`lock_stock`、订单状态、库存流水与事件数；任何负数或金额不守恒均 FAIL。当前实际结果在《Phase3A测试结果与阻塞项》中分级记录。

## 5. 2026-10-08 实测整改增量（覆盖上文“尚未实现”的旧状态）

- 下单接口现要求 `Idempotency-Key`，事务内写入 `oms_order_request` 唯一记录；同键同请求返回原订单，同键异请求拒绝。JUnit 重放测试通过；真实 HTTP 成功下单/并发重放仍受 RabbitMQ 延迟消息链路阻塞。
- `oms_payment_confirmation` 对订单和渠道交易号各设唯一键；已验证的重复通知可幂等确认。迟到和第二笔异常支付进入 `oms_payment_exception`，并生成唯一的 `oms_refund_compensation` 人工复核任务。模拟服务层支付和异常记录测试通过；渠道签名回调及自动退款仍未实测。
- `confirmReceiveOrder`、`deleteOrder` 已改为同时校验会员归属及状态的 SQL 条件更新。真 MySQL 上 20 线程抢 5 件库存，仅 5 次成功；支付/释放 2 线程竞争仅 1 次成功；重复支付不重复扣减，关闭订单不复活。JUnit 12/12 PASS。仍需 50/100 线程及完整 HTTP 下单压测。
- 真实渠道开关继续关闭，渠道启用时仍暂停自动关单，避免未确认支付与库存释放竞争。以上内容仅为本机测试库证据，不能作为真实交易上线许可。详见《Phase3A最终运行验收报告.md》。

日志安全补充：官方 `WebLogAspect` 原先记录登录请求参数和 Token 响应，已在 `mall-common` 中移除敏感参数/响应体记录，并将本轮测试日志脱敏。修复后完整 Maven 打包与 12 个测试再次通过，证据见 `docs/Phase3A-final-verified-build-test.log`。
