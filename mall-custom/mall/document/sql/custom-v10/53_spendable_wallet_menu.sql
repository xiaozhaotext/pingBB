-- Read-only spendable wallet: reuse existing platform finance role grants.
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
 SELECT id,NOW(),'消费余额与充值',2,101,'financeSpendableWallet','document',0
 FROM ums_menu parent WHERE parent.name='financeWalletManagement'
 AND NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='financeSpendableWallet');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
 SELECT DISTINCT r.role_id,child.id FROM ums_role_menu_relation r
 JOIN ums_menu parent ON parent.id=r.menu_id AND parent.name='financeWalletManagement'
 JOIN ums_menu child ON child.name='financeSpendableWallet'
 WHERE NOT EXISTS(SELECT 1 FROM ums_role_menu_relation x WHERE x.role_id=r.role_id AND x.menu_id=child.id);
