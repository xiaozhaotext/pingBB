# custom-v10 数据库迁移脚本（平台测试 V1.0 会员/分销/双积分/分红池）

> 本目录为「电商平台 V1.0」新增模块的统一数据库契约。所有新增表均为**独立、可回滚**脚本，不修改 `mall.sql` 中原有表结构。

**执行警告：01–09 当前含 `DROP TABLE IF EXISTS`，只能在全新、可丢弃的测试库首次运行；不能对已有 P0 数据库重放，更不能用于生产在线迁移。10 号管理后台脚本无 DROP，可重复执行。**

## P0-1a 真实会员购买数据库迁移

现有商城库已包含 `ums_member` 后，单独执行 `12_membership_real_payment.sql`。此脚本只新增表、扩充现有 `sys_audit_log` 字段、插入缺失的 `sys_config` 键，不运行本目录的 01–09 旧脚本。`12_membership_real_payment_DRAFT.sql` 仅为历史设计草案，不能与正式脚本混用。

```bash
mysql --default-character-set=utf8mb4 -u<user> -p <database> < 12_membership_real_payment.sql
```

该迁移为 P0-1b/c/e 保留真实订单、积分流水、身份变更、直推奖励和独立现金账本。旧 `ums_earnings` 与 `ums_earnings_ledger` 是模拟数据，不能作为真实可提现余额。订单记录创建时的实付金额及赠送积分，后续配置修改不能改写历史订单。`transaction_id`、平台订单号、积分来源和直推订单均有唯一索引，业务事务仍需同时更新现有 `ums_points_account` / `ums_points_ledger` 与新增流水。

新规则键位于 `sys_config` 的 `membership.*` 命名空间；`INSERT IGNORE` 保留管理员已有配置。`membership.purchase.enabled` 初始为 `0`。直推复购积分数量尚未得到业务确认，两个配置值留空；支付与发奖代码必须在配置完整且真实通道验签通过后才允许启用。本次不建立或启用每日分红规则。

当前代码包含微信/支付宝下单与验签回调框架，以及购买成功后的事务入账逻辑；均保持默认关闭。尚未取得商户资料做真实回调联调，事务链路也未完成数据库集成测试。直推奖励、分红、前端正式入口与服务器部署不在本次提交的已验收范围内，请勿直接开启支付开关用于生产。

## 应用顺序（必须按编号执行）

```bash
mysql -u<user> -p<pass> mall < 01_member_identity.sql
mysql -u<user> -p<pass> mall < 02_invitation.sql
mysql -u<user> -p<pass> mall < 03_points_wallet.sql
mysql -u<user> -p<pass> mall < 04_partner.sql
mysql -u<user> -p<pass> mall < 05_cofounder.sql
mysql -u<user> -p<pass> mall < 06_earnings_withdraw.sql
mysql -u<user> -p<pass> mall < 07_system_log.sql
mysql -u<user> -p<pass> mall < 08_dividend_pool.sql
mysql -u<user> -p<pass> mall < 09_mall_experience.sql
mysql -u<user> -p<pass> mall < 10_admin_p0.sql
```

一键执行（bash）：
```bash
for f in 0*.sql 10_admin_p0.sql; do mysql -u<user> -p<pass> mall < "$f"; done
```

## 回滚

```bash
mysql -u<user> -p<pass> mall < rollback_all.sql
```

## 模块与表映射

| 模块 | 文件 | 主要表 |
|------|------|--------|
| A 会员身份 | 01 | `ums_member_identity`, `ums_member_identity_log` |
| B 邀请码/关系 | 02 | `ums_invitation_code`, `ums_member_recommend`, `ums_member_recommend_audit` |
| C 双积分钱包 | 03 | `ums_points_account`, `ums_points_ledger` |
| D 合伙人(模拟) | 04 | `biz_partner_config`, `biz_partner_order` |
| E 联创(模拟) | 05 | `biz_cofounder_config`, `biz_cofounder_order` |
| F 收益提现(模拟) | 06 | `ums_earnings`, `ums_earnings_ledger`, `ums_withdraw_apply` |
| G 系统配置/审计 | 07 | `sys_config`, `sys_audit_log`, `sys_finance_audit` |
| 分红池 | 08 | `dividend_contribution_event`, `dividend_daily_stat`, `dividend_snapshot`, `dividend_allocate_detail`, `dividend_exception_audit` |
| H 商城体验 | 09 | `biz_consult_message`, `biz_share_log` |
| P0 管理后台 | 10 | `p0_points_adjust_request`，后台 RBAC 资源与菜单 |

## 关键设计约束（来自需求）

- 双积分：余额变动**必须**写 `ums_points_ledger` 流水；`idempotency_key` 防重复发放；`version` 乐观锁防并发；事务内一致，失败回滚；禁止负余额。
- 推荐关系：`ums_member_recommend.uk_member` 防重复绑定；`bind_path` 防循环；`ums_member_recommend_audit` 防会员自改推荐人。
- 资金/分红：所有金额表均带 `simulate_flag=1`，仅做模拟计算与展示，**不启用真实资金、不自动奖励发放、不真实打款**（待业务确认 + 合规审批）。
- 分红池：比例分配 `ratio = 个人贡献 / 当日总贡献`；贡献来自可核验真实业务行为；退款/取消产生负贡献事件冲回；计算按 `calc_batch` 幂等可复算。
- 所有新增表均含必要唯一约束、索引及指向 `ums_member(id)` 的外键（RESTRICT）。

## 接口契约约定（供前后端联调）

通用响应：`{ "code": 200, "message": "success", "data": ... }`
时间统一 ISO8601；金额字段单位：积分=整数，金额=元(decimal, 模拟标识)。
