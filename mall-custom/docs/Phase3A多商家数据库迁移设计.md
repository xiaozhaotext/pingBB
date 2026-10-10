# Phase 3A 多商家数据库迁移设计

状态：**评审草案，未执行 DDL**。依据固定提交 `dcaa93b...` 的 `mall/document/sql/mall.sql`、现有 MyBatis 模型、原图 134 条需求矩阵以及 Phase 1 商家 A/B 隔离与主/子订单设计。SQL 草案见 `phase3a-merchant-migration-DRAFT.sql`。`merchant_id` 是商家归属，不是 SaaS 平台级 `tenant_id`。

## 1. 现有表与目标模型

| 当前表/能力 | 当前结构证据 | 目标变化 |
|---|---|---|
| 平台账号 `ums_admin`、`ums_role` | 仅平台管理员/角色，无商家归属 | 保留平台账号；新增 `merchant`、`merchant_user`、`merchant_role`、`merchant_user_role`。商家登录与平台管理员令牌受众分离 |
| `pms_product` | 商品 ID、分类/品牌、发布状态；无商家 ID | 加 `merchant_id`，自营历史商品统一归属自营商家；列表、搜索、管理读写均附商家过滤 |
| `pms_sku_stock` | SKU 关联 `product_id`，`stock`/`lock_stock`；无商家 ID | 加 `merchant_id`，复合约束保证 SKU 与所属商品商家一致；库存操作在后端确认作用域 |
| `oms_cart_item` | 会员/商品/SKU/数量，无商家 ID | 加 `merchant_id` 并由 SKU/商品服务端推导；消费者购物车可跨商家，UI 按商家分组 |
| `oms_order` | 单一订单、买家和聚合金额、状态；无商家拆单 | 保留为**主订单**，只汇总支付与子单履约；不把商家属性直接压到主单 |
| `oms_order_item` | 只关联 `order_id` | 加 `merchant_id`、`merchant_order_id`；子订单商品明细的商家必须与子单一致 |
| 无子订单表 | `oms_order` 与 `oms_order_item` 之间无商家对象 | 新增 `oms_merchant_order`；每主单每商家一条子单，独立金额、状态、发货与收货时间 |
| `oms_order_return_apply` | 仅订单/商品、退货状态、金额 | 加 `merchant_id`、`merchant_order_id`；商家仅处理所属子单的售后；退款单和渠道资金流水另阶段设计 |
| 无结算关系 | SQL 无商家结算表 | 仅增 `merchant_settlement_profile` 作为**元数据关系**；本阶段不建模拟到账余额、不执行分账 |

商家类型严格为 `PLATFORM_SELF` 或 `THIRD_PARTY`；商家状态、审核状态为独立字段。第三方资质、合同、费率与资金结算规则仍待确认。本阶段不开发商家入驻页面或结算业务。

## 2. 可回滚分步迁移

1. **基线备份与预检**：对专用测试库备份、记录版本和行数；检查孤儿 SKU、订单项、历史退货记录、负库存/锁库存、重复订单号。原 `mall.sql` 含 `DROP TABLE IF EXISTS` 和样例数据，禁止导入现有业务库。
2. **扩展阶段**：建立商家/员工/角色/子单表，旧表先加可空 `merchant_id`、`merchant_order_id` 与索引。老代码仍可读写；写入新字段的应用版本通过功能开关启动。
3. **历史归属**：用稳定 `merchant_code='PLATFORM_SELF'` 创建唯一自营商家，按代码查 ID，不能硬编码 `1`。所有现存商品、SKU、购物车项、订单项、售后归属自营；每张历史主单生成一张自营子单并关联商品、售后。迁移批次记录进度/行数/校验摘要，支持幂等重跑。
4. **双写与校验**：增量写入保留旧主单字段并写子单字段；按订单验证 `SUM(子单应付)=主单应付`，商品/子单/商家一致，所有新旧数据无 NULL 归属和跨商家关联。并行检查搜索索引与导出范围。
5. **收紧约束**：仅在所有数据校验通过后，将归属列设 `NOT NULL`，添加复合外键、唯一约束、限制状态；切换商家查询入口。新表索引先压测，不直接在生产库锁表执行。

**回滚边界**：扩展阶段且尚无真实第三方数据时，可回滚应用版本，按逆序去除新增外键/索引/列/空新表，先恢复已验证的数据库快照。已有第三方订单后禁止简单 `DROP` 回滚；应回退应用到兼容读写版本、保留商家和子单数据，执行前向修复并对账。任何回滚须在专用测试库演练，生产另行审批。

## 3. 键、索引与数据一致性

- `merchant.merchant_code` 唯一；`merchant_user(merchant_id,username)`、`merchant_role(merchant_id,role_code)` 唯一；商家员工角色关联用复合外键限制同商家作用域。
- `pms_product(merchant_id,publish_status,id)`、`pms_sku_stock(merchant_id,product_id,id)`、`oms_cart_item(member_id,merchant_id,delete_status,id)` 支持按授权作用域查询。`pms_product(id,merchant_id)` 唯一供 SKU 复合 FK 使用。
- `oms_merchant_order(order_id,merchant_id)` 唯一，`merchant_order_sn` 唯一；`(merchant_id,status,created_at)` 服务商家列表。`oms_order_item(merchant_order_id,merchant_id)` 和售后表同类复合 FK 防止跨商家关联。
- MySQL 8/InnoDB、`utf8mb4`、金额 `DECIMAL(18,2)`；所有新表有创建/更新时间与状态。旧表字符集是 `utf8`，跨表 FK 建立前须检查类型、排序规则与引擎，不能直接假设草案可执行。
- 服务端从认证上下文确定商家，客户端 `merchant_id` 仅可作可审计的范围选择参数，不能成为授权事实；平台跨商家查询需单独权限和审计。

## 4. 验收与权限测试计划

使用自营商家、商家 A 和 B、各自管理员/员工及消费者构造数据。执行 `SEC-TEN-001` 至 `SEC-TEN-010`：A 读取/修改 B 商品、SKU 库存、子单、售后、结算关系、员工均拒绝；URL/body/header 伪造、批量 ID、分页/搜索/导出均不能泄漏；拒绝操作无数据库副作用，审计可关联操作者、目标与请求 ID。跨商家购物车拆单验证各子单金额守恒和独立履约。**本阶段这些多商家接口尚未开发，测试状态为 NOT IMPLEMENTED，不能标 PASS。**

## 5. 当前阻塞

无 MySQL 8 服务且未获安装批准；草案尚未在空测试库执行、回滚或压测。旧 SQL 无显式外键，可能有历史数据不满足新约束。商家费率、结算渠道和分账能力未知，`merchant_settlement_profile` 仅保留后续关联入口。

## 6. 2026-10-08 环境复验说明（覆盖上文 MySQL 环境阻塞）

本地 MySQL 8.4.11 已运行，原版官方 SQL 在独立 `mall_phase3a` 库导入成功，76 张原版表。额外 4 张交易安全测试表仅服务于 Phase 3A 本机验证，与多商家迁移草案无关。**本文件所述 `phase3a-merchant-migration-DRAFT.sql` 仍未执行、未回滚、未做历史数据归属迁移**；旧表字符集、历史异常库存与跨表关联仍须正式评审，商家 A/B 接口仍为 NOT IMPLEMENTED。详见《Phase3A最终运行验收报告.md》。
