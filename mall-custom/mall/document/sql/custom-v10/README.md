# custom-v10 数据库迁移脚本（平台测试 V1.0 会员/分销/双积分/分红池）

> 本目录为「电商平台 V1.0」新增模块的统一数据库契约。所有新增表均为**独立、可回滚**脚本，不修改 `mall.sql` 中原有表结构。

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
```

一键执行（bash）：
```bash
for f in 0*.sql; do mysql -u<user> -p<pass> mall < "$f"; done
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

## 关键设计约束（来自需求）

- 双积分：余额变动**必须**写 `ums_points_ledger` 流水；`idempotency_key` 防重复发放；`version` 乐观锁防并发；事务内一致，失败回滚；禁止负余额。
- 推荐关系：`ums_member_recommend.uk_member` 防重复绑定；`bind_path` 防循环；`ums_member_recommend_audit` 防会员自改推荐人。
- 资金/分红：所有金额表均带 `simulate_flag=1`，仅做模拟计算与展示，**不启用真实资金、不自动奖励发放、不真实打款**（待业务确认 + 合规审批）。
- 分红池：比例分配 `ratio = 个人贡献 / 当日总贡献`；贡献来自可核验真实业务行为；退款/取消产生负贡献事件冲回；计算按 `calc_batch` 幂等可复算。
- 所有新增表均含必要唯一约束、索引及指向 `ums_member(id)` 的外键（RESTRICT）。

## 接口契约约定（供前后端联调）

通用响应：`{ "code": 200, "message": "success", "data": ... }`
时间统一 ISO8601；金额字段单位：积分=整数，金额=元(decimal, 模拟标识)。
