-- ============================================================
-- custom-v10 全量回滚脚本（独立、可回滚）
-- 执行顺序与创建相反。删除前已移除外键依赖（本文件内 DROP 顺序已处理）。
-- 注意：回滚会删除所有新增表及数据，请先备份。
-- ============================================================

DROP TABLE IF EXISTS `dividend_exception_audit`;
DROP TABLE IF EXISTS `dividend_allocate_detail`;
DROP TABLE IF EXISTS `dividend_snapshot`;
DROP TABLE IF EXISTS `dividend_daily_stat`;
DROP TABLE IF EXISTS `dividend_contribution_event`;

DROP TABLE IF EXISTS `biz_share_log`;
DROP TABLE IF EXISTS `biz_consult_message`;

DROP TABLE IF EXISTS `sys_finance_audit`;
DROP TABLE IF EXISTS `sys_audit_log`;
DROP TABLE IF EXISTS `sys_config`;

DROP TABLE IF EXISTS `ums_withdraw_apply`;
DROP TABLE IF EXISTS `ums_earnings_ledger`;
DROP TABLE IF EXISTS `ums_earnings`;

DROP TABLE IF EXISTS `biz_cofounder_order`;
DROP TABLE IF EXISTS `biz_cofounder_config`;

DROP TABLE IF EXISTS `biz_partner_order`;
DROP TABLE IF EXISTS `biz_partner_config`;

DROP TABLE IF EXISTS `ums_points_ledger`;
DROP TABLE IF EXISTS `ums_points_account`;

DROP TABLE IF EXISTS `ums_member_recommend_audit`;
DROP TABLE IF EXISTS `ums_member_recommend`;
DROP TABLE IF EXISTS `ums_invitation_code`;

DROP TABLE IF EXISTS `ums_member_identity_log`;
DROP TABLE IF EXISTS `ums_member_identity`;
