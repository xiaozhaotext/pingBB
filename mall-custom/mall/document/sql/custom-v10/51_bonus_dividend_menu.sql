-- Add the three bonus-pool routes for the existing P0 administrator role.
-- Run after 50_bonus_dividend_pools.sql; safe to repeat. No existing menu is removed.
START TRANSACTION;

INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT 0,NOW(),'奖金分红池',0,955,'bonusDividendPool','total-yesterday',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='bonusDividendPool');

SET @bonus_menu_parent = (SELECT id FROM ums_menu WHERE name='bonusDividendPool' ORDER BY id LIMIT 1);

INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @bonus_menu_parent,NOW(),routes.title,1,routes.sort,routes.name,'total-yesterday',0
FROM (
  SELECT '合伙人池' AS title,100 AS sort,'bonusPoolPartner' AS name
  UNION ALL SELECT '联创池',90,'bonusPoolCofounder'
  UNION ALL SELECT '运营池',80,'bonusPoolOperations'
) routes
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name=routes.name);

UPDATE ums_menu SET parent_id=@bonus_menu_parent,level=1,hidden=0
WHERE name IN ('bonusPoolPartner','bonusPoolCofounder','bonusPoolOperations');

INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m
WHERE m.name IN ('bonusDividendPool','bonusPoolPartner','bonusPoolCofounder','bonusPoolOperations')
  AND EXISTS (SELECT 1 FROM ums_role WHERE id=5)
  AND NOT EXISTS (SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);

COMMIT;
