# 思维导图修复与验证记录（2026-10-15）

基线：main c6196b3。修复分支：codex/mindmap-fixes。本文记录工程验收边界，不代表已部署或全业务通过。

## 本轮实现

- 提现：数据库按会员加锁、请求唯一索引、请求重试只冻结一次、同键不同金额拒绝、金额精确到分。手机端保存未确认请求，刷新/超时重试复用原编号。消费者处理提现接口关闭。
- 分红：锁定池余额、每份金额向下保留两位、尾差留池、余额条件扣减、收益入账失败回滚。
- 后台：显示分红池余额/累计注入/累计发放/今日注入和每日明细中文标题。资金分析独立查询完整月份与历史年度数据，显示日/月环比同比，不再用7天数据冒充月份。
- 人工权益：联创每月200元申请与审核，按会员+月份防重；一次旅游人工核销，不增加现金。审核入账与收益流水、操作审计同事务。此处“发放”是收益入账，仍未接入银行卡或支付平台代付。
- 手机端：奖励/旅游审核记录；真实姓名、邮箱、微信账号自填资料（不代表实名核验）；待付款和关闭订单金额标签、金额两位小数；物流按钮显示商家录入的公司和运单号。
- 消费区：管理端分配商品、手机端列表、禁止与其他区混单、100%商品积分校验、积分兑换完成后不走外部支付。与其他区重复分配时拒绝。
- 旅游SKU：在平台设置明确关联合伙人/联创商品ID；该商品跳转已有的会员权益购买流程，普通购物车结算拒绝该商品，防止漏发双积分。不会按售价猜测权益。上线需运营人员明确指定SKU，历史普通商品订单不会自动转换。
- 安全与边界：私有评价操作强制认证，即使旧部署还保留/product/**白名单也不能绕过；401/403使用相应HTTP状态。分页用long偏移，数据库异常不向客户端返回SQL。下架商品详情不再公开。隐私政策未发布返回明确状态，不制造正文。

## 数据库升级

先备份，再依次执行 document/sql/custom-v10/44_withdraw_idempotency.sql、45_manual_benefits.sql、46_member_contact.sql。
44兼容历史NULL请求编号，45/46仅新建表；脚本可重复执行。必须先执行升级再切换两个服务。代码回滚时保留新增列和表，不自动回滚/恢复业务数据。

## 已验证的范围

- JUnit真实H2数据库、MyBatis、SQL事务与8并发提现；0.05分红池不会发出0.06；错误时账本/余额回滚。
- 嵌入式HTTP测试：充值创建、重复回调、错误金额/签名、漏回调补查；人工月奖励审核、防重、旅游核销、无权限拒绝与回滚。
- 实际Spring Security过滤链及签名JWT：私有评价匿名401，合法JWT可访问，公开评价正常。
- 手机端脚本执行真实Vue页面逻辑：充值/提现9项，覆盖金额、重复点击、超时、刷新及请求编号复用。
- 浏览器真实Vue后台组件→真实人工权益Controller→H2→手机端实际H5页面：待审核→200元审核入账→手机端同步；重复申请拒绝；旅游核销不加现金；联系资料保存后刷新读取。
- 浏览器联调使用固定的测试会员/测试管理员身份，不是生产账号登录验收；H2不是生产MySQL。所有UI测试地址绑定127.0.0.1，不接触线上资金。

## 仍需服务器验收

1. MySQL数据库升级的首次/重复执行、已有数据兼容与备份恢复演练。
2. 使用实际JWT登录和权限数据，验证管理端侧边栏、会员列表、新权益操作权限及手机端越权拒绝。
3. 完整下单/库存竞争/取消/退款/积分退回/零现金消费区订单。现有PortalOrderStockConcurrencyTests启动时因本机MySQL不可用失败，不计为通过。
4. 月统计的MySQL查询和完整月份数据对账，旅游商品明确关联后的支付/赠送闭环。
5. 微信/支付宝真实支付及提现代付仍需商户凭据，不能声称已完成真扣款测试。隐私正文需运营方审核发布。

## 可重复本地测试

Maven显式传 -DskipTests=false；项目默认跳过测试，只有BUILD SUCCESS不足以证明执行过测试。
选择 FinancialIntegrityTest,ConsumptionAndContactTest,ProductAuthenticationHttpTest,RechargeHttpTest,RechargeServiceTest,GapSecurityTest,CrossReviewRegressionTest,ProductZoneAdminControllerTest,ManualBenefitHttpTest,OmsPortalOrderServiceImplSecurityTests。

手机端：node --test scripts/test-recharge.cjs scripts/test-withdraw.cjs；vue-tsc --noEmit --skipLibCheck；npm run build:h5（包含复制静态资源）。
管理端：vue-tsc --build；vite build。

qa目录的Vite页面与Java测试classpath中的BrowserHarness仅用于隔离组件联调，不属于生产入口；不得把测试身份过滤器或测试端口发布到公网。
