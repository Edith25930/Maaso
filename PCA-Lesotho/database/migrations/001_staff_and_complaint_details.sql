-- One-time upgrade for databases created from the earlier mobile schema.
ALTER TABLE users
    ADD COLUMN role ENUM('citizen','admin','investigator') NOT NULL DEFAULT 'citizen';

ALTER TABLE complaints
    ADD COLUMN complainant_name VARCHAR(120) NULL AFTER user_id,
    ADD COLUMN complainant_phone VARCHAR(20) NULL AFTER complainant_name,
    ADD COLUMN complainant_id_number VARCHAR(30) NULL AFTER complainant_phone,
    ADD COLUMN complainant_location VARCHAR(255) NULL AFTER complainant_id_number,
    ADD COLUMN complainant_gender ENUM('female','male','prefer_not_to_say') NULL AFTER complainant_location,
    ADD COLUMN complainant_district_id SMALLINT UNSIGNED NULL AFTER complainant_gender,
    ADD COLUMN incident_station_name VARCHAR(100) NULL AFTER incident_location_text,
    ADD CONSTRAINT fk_complaint_complainant_district
        FOREIGN KEY (complainant_district_id) REFERENCES districts(district_id);

ALTER TABLE complaint_officer_info
    ADD COLUMN station_name VARCHAR(100) NULL AFTER badge_number;

CREATE TABLE complaint_assignments (
    complaint_id        BIGINT UNSIGNED PRIMARY KEY,
    investigator_id     BIGINT UNSIGNED NOT NULL,
    assigned_by         BIGINT UNSIGNED NOT NULL,
    assigned_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignment_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_investigator FOREIGN KEY (investigator_id) REFERENCES users(user_id),
    CONSTRAINT fk_assignment_admin FOREIGN KEY (assigned_by) REFERENCES users(user_id),
    KEY ix_assignment_investigator (investigator_id)
) ENGINE=InnoDB;
