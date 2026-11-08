ALTER TABLE complaints
    ADD COLUMN complaint_type VARCHAR(120) NULL AFTER category_unknown,
    ADD COLUMN region_district VARCHAR(100) NULL AFTER complaint_type,
    ADD COLUMN next_of_kin_name VARCHAR(255) NULL AFTER description,
    ADD COLUMN next_of_kin_address VARCHAR(255) NULL AFTER next_of_kin_name;

UPDATE complaint_status_types
SET label_en = 'Pending', label_st = 'E Emetse'
WHERE code = 'SUBMITTED';
