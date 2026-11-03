-- Store a department subaccount's mobile number without changing legacy admin email data.
ALTER TABLE ums_admin ADD COLUMN phone VARCHAR(11) NULL COMMENT '子账号手机号' AFTER email;
