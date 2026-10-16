-- Promote the existing three pool pages; retain their menu ids and role grants.
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
 SELECT 0,NOW(),'分红池',0,75,'dividendPoolMgr','total-yesterday',0
 WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='dividendPoolMgr');
UPDATE ums_menu child JOIN ums_menu parent ON parent.name='dividendPoolMgr'
 SET child.parent_id=parent.id,child.level=1,child.hidden=0,
 child.title=CASE child.name WHEN 'financePartnerPool' THEN '合伙人池' WHEN 'financeCofounderPool' THEN '联创池' ELSE '运营池' END
 WHERE child.name IN ('financePartnerPool','financeCofounderPool','financeOperationsPool');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
 SELECT DISTINCT r.role_id,parent.id FROM ums_role_menu_relation r
 JOIN ums_menu child ON child.id=r.menu_id
 JOIN ums_menu parent ON parent.name='dividendPoolMgr'
 WHERE child.name IN ('financePartnerPool','financeCofounderPool','financeOperationsPool')
 AND NOT EXISTS (SELECT 1 FROM ums_role_menu_relation x WHERE x.role_id=r.role_id AND x.menu_id=parent.id);

-- Idempotent date-filter indexes. Does not change financial rows.
SET @tri_daily_ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tri_pool_ledger' AND INDEX_NAME='ix_tri_ledger_day')=0,
 'ALTER TABLE tri_pool_ledger ADD INDEX ix_tri_ledger_day(pool_code,created_at,id)','SELECT 1');
PREPARE tri_daily_stmt FROM @tri_daily_ddl; EXECUTE tri_daily_stmt; DEALLOCATE PREPARE tri_daily_stmt;
SET @tri_daily_ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tri_pool_distribution' AND INDEX_NAME='ix_tri_award_day')=0,
 'ALTER TABLE tri_pool_distribution ADD INDEX ix_tri_award_day(pool_code,created_at,id)','SELECT 1');
PREPARE tri_daily_stmt FROM @tri_daily_ddl; EXECUTE tri_daily_stmt; DEALLOCATE PREPARE tri_daily_stmt;
SET @tri_daily_ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tri_pool_batch' AND INDEX_NAME='ix_tri_batch_day')=0,
 'ALTER TABLE tri_pool_batch ADD INDEX ix_tri_batch_day(pool_code,created_at,id)','SELECT 1');
PREPARE tri_daily_stmt FROM @tri_daily_ddl; EXECUTE tri_daily_stmt; DEALLOCATE PREPARE tri_daily_stmt;
