ALTER TABLE complaints
    ADD COLUMN case_priority ENUM('low','normal','high','urgent') NOT NULL DEFAULT 'normal'
        AFTER expected_completion_date;
