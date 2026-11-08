ALTER TABLE users
    MODIFY COLUMN id_number VARCHAR(100) NULL;

ALTER TABLE complaints
    MODIFY COLUMN complainant_id_number VARCHAR(100) NULL;
