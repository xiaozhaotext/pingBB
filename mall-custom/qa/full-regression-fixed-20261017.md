# 2026-10-17 完整回归修复结果

基线：`d8814418c694fd639dacb6ed3e335a3e7e99546a`。修复上午回归报告中的全部 10 个未通过项，并增加两项防回归用例。

## 修复

- 商品未配置赠送积分或成长值时按 0 处理，避免结算预览和后续订单计算空值拆箱异常；保留仅自选区允许赠送积分的规则。
- 会员购买、推荐奖励、订单积分抵扣及取消返还均由 `PointsWalletService` 更新当前账户及 `ums_points_ledger`，停止写入弃用的 `member_point_ledger`。已有历史数据保留，账户金额和积分规则不变。会员商品积分兼容字段从实际钱包余额同步。
- 完整 Spring 上下文测试通过共享测试基类连接专用 `mall_github_audit_*` 数据库；缺少隔离库配置会明确失败，禁止回落开发或业务库。测试期间不启动 RabbitMQ 消费者。
- 浏览器钱包测试桩仅在 `sandbox-wallet-test` 模式加载，避免全量扫描时与真实 `/sso/info` 等路由冲突。
- 商品查询测试自建四条必需的商品记录（包含必填商品编号），会员推荐测试自建奖励规则，不依赖业务库现有数据。
- 新增用例验证空赠送字段、非自选区不赠送，以及订单积分扣减/返还在没有旧账本表时仍正常工作。保留原支付幂等、事务回滚、库存并发、余额及三专区结算断言。

## 最终结果

完整命令执行结束，Maven 返回 `BUILD SUCCESS`；没有排除测试类或跳过未通过项。

| 测试范围 | 执行数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| common | 16 | 0 | 0 | 0 |
| admin | 71 | 0 | 0 | 0 |
| portal | 134 | 0 | 0 | 0 |
| 应用端自动化 | 47 | 0 | 0 | 0 |
| 管理端自动化 | 8 | 0 | 0 | 0 |
| **合计** | **276** | **0** | **0** | **0** |

后端 admin/portal JAR 打包完成。前端业务代码未改，本轮重新执行全部 55 项前端测试；此前通过的类型检查及四份正式/沙盒构建结果见上午报告。

```powershell
# 必须先配置只指向隔离 MySQL 库的环境变量：
# MALL_MEMBERSHIP_TEST_JDBC_URL（127.0.0.1，库名 mall_github_audit_*）
# MALL_MEMBERSHIP_TEST_DB_USER、MALL_MEMBERSHIP_TEST_DB_PASSWORD
# 隔离库应应用现有表结构，并初始化 sys_site_profile 的 id=1 记录。
mvn -f mall-custom/mall/pom.xml -pl mall-admin,mall-portal -am package '-DskipTests=false' '-Ddocker.skip=true' '-Dspring.http.client.factory=simple' '-DargLine=-Djdk.net.unixdomain.tmpdir=D:/Project/Online_business'
# 在 mall-app-web 下
node --test tests/*.test.cjs scripts/test-*.cjs
# 在 mall-admin-web 下
node --test tests/*.test.cjs
```

本轮使用专属隔离 MySQL 库及账号；测试结束后已清理数据库、账号、凭证文件和 SSH 转发。没有向正式或沙盒业务库写入测试交易。本轮未部署服务器，未宣称浏览器逐页点击或真实支付渠道验收完成。
