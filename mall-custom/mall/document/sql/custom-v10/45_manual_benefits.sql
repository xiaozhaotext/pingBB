-- Manual benefit requests. No automatic monthly cash posting or external payout.
CREATE TABLE IF NOT EXISTS member_manual_benefit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT NOT NULL,
 benefit_type VARCHAR(24) NOT NULL,
 benefit_period VARCHAR(7) NOT NULL,
 amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 request_reason VARCHAR(500) NOT NULL,
 requested_by BIGINT NOT NULL,
 reviewed_by BIGINT NULL,
 review_note VARCHAR(500) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_at DATETIME NULL,
 UNIQUE KEY uk_member_benefit_period(member_id,benefit_type,benefit_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
