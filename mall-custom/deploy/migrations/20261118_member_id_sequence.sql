-- Raise the member ID allocation floor without rewriting existing accounts.
-- If member 1000 already exists, MySQL allocates the next account as 1001.
ALTER TABLE ums_member AUTO_INCREMENT=1000;
