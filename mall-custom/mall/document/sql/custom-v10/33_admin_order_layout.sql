-- Order management IA: expose exactly Filter, Sales Summary and Order Details.
-- Safe to repeat. This changes only the five menu records in the dedicated six-group tree.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE ums_menu SET title='筛选', name='orderMgrFilter', icon='product-list', hidden=0, sort=10
WHERE parent_id=102 AND id=114 AND name='orderMgrList';

UPDATE ums_menu SET title='订单销量汇总', name='orderSummary', icon='total-today', hidden=0, sort=9
WHERE parent_id=102 AND id=115;

UPDATE ums_menu SET title='订单详情', name='orderMgrDetails', icon='document', hidden=0, sort=8
WHERE parent_id=102 AND id=116 AND name='orderMgrSetting';

UPDATE ums_menu SET hidden=1 WHERE parent_id=102 AND id IN (117,118)
  AND name IN ('orderMgrReturnApply','orderMgrReturnReason');

COMMIT;
