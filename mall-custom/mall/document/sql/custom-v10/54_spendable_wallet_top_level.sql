-- Move the existing spendable-wallet entry to its own first-level sidebar group.
-- Keeps the old menu ID and all grants; safe to rerun after 53_spendable_wallet_menu.sql.
START TRANSACTION;

INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT 0,NOW(),'消费余额与充值',0,945,'spendableWalletMgr','total-week',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='spendableWalletMgr');

SET @spendableWalletRootId = (SELECT id FROM ums_menu WHERE name='spendableWalletMgr' ORDER BY id LIMIT 1);
UPDATE ums_menu SET parent_id=0,level=0,sort=945,title='消费余额与充值',icon='total-week',hidden=0
WHERE id=@spendableWalletRootId;
UPDATE ums_menu SET parent_id=@spendableWalletRootId,level=1,sort=100,title='消费余额与充值',icon='total-week',hidden=0
WHERE name='financeSpendableWallet';

INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT grant_row.role_id,@spendableWalletRootId
FROM ums_role_menu_relation grant_row
JOIN ums_menu child ON child.id=grant_row.menu_id AND child.name='financeSpendableWallet'
LEFT JOIN ums_role_menu_relation existing ON existing.role_id=grant_row.role_id AND existing.menu_id=@spendableWalletRootId
WHERE existing.menu_id IS NULL;

COMMIT;
