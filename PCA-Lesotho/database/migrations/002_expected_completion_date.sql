ALTER TABLE complaints
    ADD COLUMN expected_completion_date DATE NULL AFTER gcr_number;
