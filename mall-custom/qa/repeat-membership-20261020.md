# 自选区会员复购与按份推荐

基于远程 main `50312ab` 实现，2026-10-20 本地验证。

## 业务规则

- 已有会员可继续购买已上架且已批准的3999元自选区路线；支持分次购买及一单多份。
- 每份增加3999消费积分、3999复购积分；合伙人池本人份额增加1，累计上限增加1200元。联创身份在联创池按本人购买份数分红，本池累计上限为1200元×本人份数，并受两池共用总上限约束。
- 本人增购不重置身份、有效直邀份数或累计已领分红。已经封顶的会员增购后可使用新额度。
- 推荐人按直接邀请会员实际支付的购买份数累计：甲首次1份、再买2份，乙有效直邀3份并晋升合伙人。每份继续发放399.90元直邀奖励、1200复购积分，三池资金按份增加。
- 保留3份合伙人、9份联创门槛及原有直邀阶梯上限；本人每额外购买1份，在原阶梯共用总上限基础上加1200元；两池累计已领合并扣减总额度。
- 只有验证支付成功后发放；支付订单号仍是唯一幂等键。无优惠的3999整数倍自选区订单才能获取对应份数；混合其他专区商品的订单不接受会员权益结算。
- 独立会员卡购买入口仍用于首次开通，会员增购入口为自选区。

## 发布顺序

先在目标库执行 `mall/document/sql/custom-v10/56_repeat_membership_purchase.sql`，再发布后端与前后台。

迁移移除 `tri_payment_event.member_id` 的单列唯一索引、建立普通会员索引，保留 `source_key` 主键及全部历史资金记录。可重复执行。新安装的50号脚本也已使用普通会员索引。

购买份数根据已验证支付金额除以3999计算，不回填或补发旧普通商品订单。

## 验证范围

- 核心数据库事务测试覆盖分次复购、单笔增购2份、并发复购、重复回调、同订单金额冲突、积分失败回滚、保留联创身份及已领金额、封顶后增购恢复额度，以及按真实份数分红。
- 自选区订单测试覆盖已有会员生成权益快照、一单2份冻结7998元、拒绝优惠金额、混合专区与无效数量。
- 已在本次新建的独立 MySQL 8.0.34 实例验证旧索引迁移、重复执行迁移、同会员新增支付记录、重复订单号仍被拒绝；测试实例已关闭，未连接线上库。
- 用户端41项测试与H5生产构建、管理端20项测试与包含类型检查的生产构建均通过。
- 后端共235项通过：公共模块29项、管理端70项、门户136项。门户内5项沙箱HTTP测试在修正过期测试依赖后定向复测通过，其余230项已在上一轮回归通过；不能将上一轮的失败日志误读为最终状态。最终沙箱复测日志为项目根目录 `repeat-membership-sandbox-tests.log`，其余回归日志为 `repeat-membership-regression-tests.log`。
- 后端回归运行命令：`mvn -f mall-custom/mall/pom.xml -pl mall-common,mall-portal,mall-admin -am test -DskipTests=false "-Dtest=*,!*MysqlTest,!MallPortalApplicationTests,!PortalProductDaoTests,!PortalOrderStockConcurrencyTests" -Dsurefire.failIfNoSpecifiedTests=false "-DargLine=-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp"`。本机Windows临时目录短路径会使Java NIO回环连接失败，测试进程使用上述临时目录参数；业务配置不受影响。
- 尝试无筛选执行时，既有 `SiteProfileServiceMysqlTest` 的4项测试因缺少 `MALL_MEMBERSHIP_TEST_JDBC_URL` 而失败，不能记为通过。其余既有 `*MysqlTest` 也需要完整专用测试库；上述回归明确排除这类外部环境测试。独立执行的本次MySQL迁移验证不依赖这些既有验收库。
- `MallPortalApplicationTests`、`PortalProductDaoTests`、`PortalOrderStockConcurrencyTests` 同样要求既有完整隔离MySQL测试库，上述回归明确排除，未记为通过。
- 回归时发现最新main移除订单号Redis依赖后，`SandboxWalletHttpTest` 仍反射注入已删除的Redis服务和两个配置字段；同步移除过期注入，使沙箱HTTP测试可以正常执行。

本次完成代码实现和本地测试；Git分支为 codex/repeat-membership-20261020，未部署到线上。

## 合并最新main后的规则

2026-10-20 合并远程main `6e57369` 的两池共用总上限规则。上面的首次实现测试为历史记录；最新合并版以共用总额度为准。甲本人3份、有效直邀9份时共用总上限26400元，联创池3份且本池最多3600元，两池总计不能超过26400元。
