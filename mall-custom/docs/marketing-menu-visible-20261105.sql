-- Show the existing marketing center in the new admin navigation.
-- Coupon and flash-sale pages and permissions already exist; the legacy
-- duplicate entries under System Settings remain hidden.
UPDATE ums_menu SET hidden=0 WHERE name='sms';
UPDATE ums_menu SET hidden=1 WHERE name IN ('serviceCoupon','serviceFlash');
