-- A mobile number must resolve to only one department subaccount.
ALTER TABLE ums_admin ADD UNIQUE KEY uk_ums_admin_phone (phone);
