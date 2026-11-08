-- ============================================================================
-- PCA LESOTHO — MOBILE COMPLAINT REPORTING, TRACKING & PUBLIC INFORMATION APP
-- Central Database Schema (MySQL 8.x, InnoDB, utf8mb4)
-- Normal Form: Third Normal Form (3NF)
--
-- Design notes on normalization:
--  - Every non-key column depends on the whole primary key, and only on the
--    key (no partial dependencies) — all tables use single-column surrogate
--    PKs, so partial dependency cannot occur.
--  - No transitive dependencies: lookup/reference data (districts, statuses,
--    categories, notification types, languages) is factored into its own
--    table and referenced by foreign key rather than repeated inline.
--  - Bilingual (English/Sesotho) text is stored as paired columns on the
--    owning row rather than a separate translation table, because the brief
--    only requires two fixed languages (not an open-ended set) — adding a
--    generic i18n table would be over-engineering for this scope.
--  - "Current status" is kept as a single FK on `complaints` (a genuine,
--    single-valued fact about a complaint at any point in time) while the
--    full audit trail lives in `complaint_status_history`. This is not a
--    3NF violation: current_status_id depends only on the complaint's key,
--    not on another non-key attribute.
--  - Officer information supplied by a complainant is NOT assumed to match
--    a verified officer register (the complainant may only know a name or
--    nothing at all), so it is captured as complaint-owned facts in
--    `complaint_officer_info` rather than forcing a join to a master
--    `officers` table PCA staff would need to curate.
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. REFERENCE / LOOKUP TABLES
-- ----------------------------------------------------------------------------

CREATE TABLE districts (
    district_id     SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name_en         VARCHAR(60) NOT NULL,
    name_st         VARCHAR(60) NOT NULL,
    UNIQUE KEY uq_districts_name_en (name_en)
) ENGINE=InnoDB;

CREATE TABLE police_stations (
    station_id      INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    district_id     SMALLINT UNSIGNED NOT NULL,
    name            VARCHAR(100) NOT NULL,
    CONSTRAINT fk_station_district FOREIGN KEY (district_id) REFERENCES districts(district_id),
    UNIQUE KEY uq_station_name_district (name, district_id)
) ENGINE=InnoDB;

CREATE TABLE complaint_categories (
    category_id         TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(40) NOT NULL,                 -- e.g. EXCESSIVE_FORCE
    name_en             VARCHAR(80) NOT NULL,
    name_st             VARCHAR(80) NOT NULL,
    description_en      TEXT NULL,
    description_st      TEXT NULL,
    display_order       TINYINT UNSIGNED NOT NULL DEFAULT 0,
    is_active           TINYINT(1) NOT NULL DEFAULT 1,
    UNIQUE KEY uq_category_code (code)
) ENGINE=InnoDB;

CREATE TABLE complaint_status_types (
    status_id       TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(30) NOT NULL,           -- SUBMITTED, RECEIVED, ASSIGNED, UNDER_INVESTIGATION, INFO_REQUESTED, COMPLETED, CLOSED
    label_en        VARCHAR(60) NOT NULL,
    label_st        VARCHAR(60) NOT NULL,
    color_code      VARCHAR(20) NOT NULL,           -- semantic token, e.g. 'status-orange'
    sort_order      TINYINT UNSIGNED NOT NULL,      -- position in the public timeline
    UNIQUE KEY uq_status_code (code)
) ENGINE=InnoDB;

CREATE TABLE notification_types (
    type_id         TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(40) NOT NULL,  -- COMPLAINT_SUBMITTED, COMPLAINT_RECEIVED, ASSIGNED, STATUS_CHANGED, PROGRESS_UPDATE, INFO_REQUIRED, CLOSED
    label_en        VARCHAR(80) NOT NULL,
    label_st        VARCHAR(80) NOT NULL,
    is_essential    TINYINT(1) NOT NULL DEFAULT 0,  -- essential/security notices cannot be disabled
    UNIQUE KEY uq_notiftype_code (code)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2. USERS, AUTH & DEVICES
-- ----------------------------------------------------------------------------

CREATE TABLE users (
    user_id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name           VARCHAR(120) NOT NULL,
    phone_number        VARCHAR(20)  NOT NULL,
    email               VARCHAR(150) NULL,
    password_hash       VARCHAR(255) NOT NULL,      -- bcrypt/argon2 hash, never plaintext
    district_id         SMALLINT UNSIGNED NULL,
    gender              ENUM('female','male','prefer_not_to_say') NULL,
    id_number           VARCHAR(100) NULL,
    preferred_language  ENUM('en','st') NOT NULL DEFAULT 'en',
    email_verified_at   DATETIME NULL,
    account_status      ENUM('active','suspended','deleted') NOT NULL DEFAULT 'active',
    is_guest            TINYINT(1) NOT NULL DEFAULT 0,
    role                ENUM('citizen','admin','investigator') NOT NULL DEFAULT 'citizen',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_district FOREIGN KEY (district_id) REFERENCES districts(district_id),
    UNIQUE KEY uq_users_phone (phone_number),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE user_sessions (
    session_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    token_hash      CHAR(64) NOT NULL,          -- SHA-256 of the issued token; raw token never stored
    device_label    VARCHAR(100) NULL,
    issued_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      DATETIME NOT NULL,
    revoked_at      DATETIME NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uq_session_token (token_hash),
    KEY ix_session_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE email_action_tokens (
    token_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    purpose     ENUM('verify_email', 'reset_password') NOT NULL,
    token_hash  CHAR(64) NOT NULL,
    expires_at  DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_action_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uq_email_action_hash (token_hash),
    KEY ix_email_action_user_purpose (user_id, purpose, created_at)
) ENGINE=InnoDB;

CREATE TABLE user_devices (
    device_id       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    push_token      VARCHAR(255) NOT NULL,      -- FCM registration token
    platform        ENUM('android') NOT NULL DEFAULT 'android',
    last_active_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uq_device_token (push_token)
) ENGINE=InnoDB;

CREATE TABLE notification_preferences (
    user_id                     BIGINT UNSIGNED PRIMARY KEY,
    push_enabled                TINYINT(1) NOT NULL DEFAULT 1,
    email_enabled               TINYINT(1) NOT NULL DEFAULT 1,
    status_update_enabled       TINYINT(1) NOT NULL DEFAULT 1,
    general_info_enabled        TINYINT(1) NOT NULL DEFAULT 1,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifpref_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE ai_assistant_requests (
    request_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    requested_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_request_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    KEY ix_ai_request_user_time (user_id, requested_at)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 3. COMPLAINTS AND RELATED DATA
-- ----------------------------------------------------------------------------

CREATE TABLE complaints (
    complaint_id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    local_uuid              CHAR(36) NOT NULL,          -- generated on-device; de-duplicates offline submissions
    gcr_number              VARCHAR(20) NULL,           -- assigned by the central system only on successful submission
    expected_completion_date DATE NULL,                  -- set by PCA staff and shown to the complainant
    case_priority           ENUM('low','normal','high','urgent') NOT NULL DEFAULT 'normal',
    user_id                 BIGINT UNSIGNED NOT NULL,
    complainant_name        VARCHAR(120) NULL,
    complainant_phone       VARCHAR(20) NULL,
    complainant_id_number   VARCHAR(100) NULL,
    complainant_location    VARCHAR(255) NULL,
    complainant_gender      ENUM('female','male','prefer_not_to_say') NULL,
    complainant_district_id SMALLINT UNSIGNED NULL,
    category_id             TINYINT UNSIGNED NULL,      -- NULL allowed: "I don't know the complaint type"
    category_unknown        TINYINT(1) NOT NULL DEFAULT 0,
    complaint_type          VARCHAR(120) NULL,
    region_district         VARCHAR(100) NULL,
    incident_date           DATE NOT NULL,
    incident_time           TIME NULL,
    district_id             SMALLINT UNSIGNED NULL,
    incident_location_text  VARCHAR(255) NULL,
    incident_station_name   VARCHAR(100) NULL,
    incident_station_id     INT UNSIGNED NULL,
    latitude                DECIMAL(9,6) NULL,
    longitude               DECIMAL(9,6) NULL,
    location_shared         TINYINT(1) NOT NULL DEFAULT 0,
    description             TEXT NOT NULL,
    next_of_kin_name        VARCHAR(255) NULL,
    next_of_kin_address     VARCHAR(255) NULL,
    ai_suggestion_used      TINYINT(1) NOT NULL DEFAULT 0,   -- AI only ever suggests; never overwrites the statement above
    declaration_confirmed   TINYINT(1) NOT NULL DEFAULT 0,
    current_status_id       TINYINT UNSIGNED NOT NULL,
    sync_status             ENUM('draft','pending_submission','submitted','submission_failed') NOT NULL DEFAULT 'draft',
    submitted_at            DATETIME NULL,
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaint_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_complaint_complainant_district FOREIGN KEY (complainant_district_id) REFERENCES districts(district_id),
    CONSTRAINT fk_complaint_category FOREIGN KEY (category_id) REFERENCES complaint_categories(category_id),
    CONSTRAINT fk_complaint_district FOREIGN KEY (district_id) REFERENCES districts(district_id),
    CONSTRAINT fk_complaint_station FOREIGN KEY (incident_station_id) REFERENCES police_stations(station_id),
    CONSTRAINT fk_complaint_status FOREIGN KEY (current_status_id) REFERENCES complaint_status_types(status_id),
    UNIQUE KEY uq_complaint_gcr (gcr_number),
    UNIQUE KEY uq_complaint_local_uuid (local_uuid),
    KEY ix_complaint_user (user_id),
    KEY ix_complaint_status (current_status_id)
) ENGINE=InnoDB;

-- Officer details as reported by the complainant (may be partial/unknown).
CREATE TABLE complaint_officer_info (
    complaint_id        BIGINT UNSIGNED PRIMARY KEY,
    officer_unknown      TINYINT(1) NOT NULL DEFAULT 0,
    officer_name         VARCHAR(120) NULL,
    officer_rank         VARCHAR(60) NULL,
    badge_number          VARCHAR(30) NULL,
    station_name          VARCHAR(100) NULL,
    station_id            INT UNSIGNED NULL,
    CONSTRAINT fk_officerinfo_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_officerinfo_station FOREIGN KEY (station_id) REFERENCES police_stations(station_id)
) ENGINE=InnoDB;

CREATE TABLE witnesses (
    witness_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    complaint_id    BIGINT UNSIGNED NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    contact_number  VARCHAR(20) NULL,
    CONSTRAINT fk_witness_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    KEY ix_witness_complaint (complaint_id)
) ENGINE=InnoDB;

CREATE TABLE evidence_files (
    evidence_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    complaint_id    BIGINT UNSIGNED NOT NULL,
    file_name       VARCHAR(150) NOT NULL,
    file_type       ENUM('photo','video','document','audio') NOT NULL,
    storage_path    VARCHAR(255) NULL,            -- populated once uploaded to the server
    file_size_bytes INT UNSIGNED NULL,
    checksum_sha256 CHAR(64) NULL,                -- integrity check across offline sync
    upload_status   ENUM('pending','uploading','uploaded','failed') NOT NULL DEFAULT 'pending',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_at     DATETIME NULL,
    CONSTRAINT fk_evidence_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    KEY ix_evidence_complaint (complaint_id)
) ENGINE=InnoDB;

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

-- Full audit trail of every status change (what the tracking timeline is built from).
CREATE TABLE complaint_status_history (
    history_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    complaint_id    BIGINT UNSIGNED NOT NULL,
    status_id       TINYINT UNSIGNED NOT NULL,
    message_en      VARCHAR(255) NULL,
    message_st      VARCHAR(255) NULL,
    changed_by_staff_id BIGINT UNSIGNED NULL,     -- references the separate PCA staff/case-management system
    changed_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_history_status FOREIGN KEY (status_id) REFERENCES complaint_status_types(status_id),
    KEY ix_history_complaint (complaint_id, changed_at)
) ENGINE=InnoDB;

-- Offline synchronization queue (drafts and evidence waiting to leave the device).
CREATE TABLE sync_queue (
    queue_id        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    entity_type     ENUM('complaint','evidence_file') NOT NULL,
    local_reference CHAR(36) NOT NULL,             -- local_uuid of the complaint, or evidence local id
    status          ENUM('queued','syncing','synced','failed') NOT NULL DEFAULT 'queued',
    retry_count     TINYINT UNSIGNED NOT NULL DEFAULT 0,
    last_attempt_at DATETIME NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_syncqueue_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    KEY ix_syncqueue_user_status (user_id, status)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 4. NOTIFICATIONS
-- ----------------------------------------------------------------------------

CREATE TABLE notifications (
    notification_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    complaint_id    BIGINT UNSIGNED NULL,
    type_id         TINYINT UNSIGNED NOT NULL,
    title_en        VARCHAR(150) NOT NULL,
    title_st        VARCHAR(150) NOT NULL,
    body_en         VARCHAR(300) NOT NULL,
    body_st         VARCHAR(300) NOT NULL,
    is_read         TINYINT(1) NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(complaint_id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_type FOREIGN KEY (type_id) REFERENCES notification_types(type_id),
    KEY ix_notification_user_read (user_id, is_read)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5. LEARN / KNOWLEDGE CENTRE
-- ----------------------------------------------------------------------------

CREATE TABLE educational_articles (
    article_id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    category_id         TINYINT UNSIGNED NOT NULL,
    title_en            VARCHAR(150) NOT NULL,
    title_st            VARCHAR(150) NOT NULL,
    definition_en        TEXT NOT NULL,
    definition_st        TEXT NOT NULL,
    examples_en           TEXT NULL,
    examples_st           TEXT NULL,
    when_to_report_en     TEXT NULL,
    when_to_report_st     TEXT NULL,
    useful_info_en        TEXT NULL,
    useful_info_st        TEXT NULL,
    is_published          TINYINT(1) NOT NULL DEFAULT 1,
    updated_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES complaint_categories(category_id)
) ENGINE=InnoDB;

CREATE TABLE quiz_questions (
    question_id     INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    category_id     TINYINT UNSIGNED NULL,
    question_en     VARCHAR(255) NOT NULL,
    question_st     VARCHAR(255) NOT NULL,
    explanation_en  TEXT NULL,
    explanation_st  TEXT NULL,
    is_active       TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT fk_question_category FOREIGN KEY (category_id) REFERENCES complaint_categories(category_id)
) ENGINE=InnoDB;

CREATE TABLE quiz_options (
    option_id       INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    question_id     INT UNSIGNED NOT NULL,
    option_en       VARCHAR(150) NOT NULL,
    option_st       VARCHAR(150) NOT NULL,
    is_correct      TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_option_question FOREIGN KEY (question_id) REFERENCES quiz_questions(question_id) ON DELETE CASCADE,
    KEY ix_option_question (question_id)
) ENGINE=InnoDB;

CREATE TABLE quiz_attempts (
    attempt_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    started_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME NULL,
    score           TINYINT UNSIGNED NULL,
    total_questions TINYINT UNSIGNED NULL,
    CONSTRAINT fk_attempt_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE quiz_attempt_answers (
    attempt_id          BIGINT UNSIGNED NOT NULL,
    question_id         INT UNSIGNED NOT NULL,
    selected_option_id  INT UNSIGNED NOT NULL,
    is_correct          TINYINT(1) NOT NULL,
    PRIMARY KEY (attempt_id, question_id),
    CONSTRAINT fk_answer_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(attempt_id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES quiz_questions(question_id),
    CONSTRAINT fk_answer_option FOREIGN KEY (selected_option_id) REFERENCES quiz_options(option_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 6. PCA INFORMATION ASSISTANT (rule-based / FAQ-grounded chatbot)
-- ----------------------------------------------------------------------------

CREATE TABLE assistant_conversations (
    conversation_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NULL,      -- NULL permitted: guests may use the assistant
    started_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE assistant_messages (
    message_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NOT NULL,
    sender          ENUM('user','assistant') NOT NULL,
    message_text    TEXT NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(conversation_id) ON DELETE CASCADE,
    KEY ix_message_conversation (conversation_id, created_at)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 7. STATIC PCA INFORMATION & AUDIT LOG
-- ----------------------------------------------------------------------------

CREATE TABLE pca_contact_info (
    info_id     TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    type        ENUM('phone','email','address','website') NOT NULL,
    label_en    VARCHAR(80) NOT NULL,
    label_st    VARCHAR(80) NOT NULL,
    value       VARCHAR(255) NOT NULL,
    display_order TINYINT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE faqs (
    faq_id      INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    question_en VARCHAR(255) NOT NULL,
    question_st VARCHAR(255) NOT NULL,
    answer_en   TEXT NOT NULL,
    answer_st   TEXT NOT NULL,
    display_order TINYINT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB;

-- Structured content for the "About PCA" screen (§3.18 of the design spec):
-- About the PCA, what PCA does, how to make a complaint, what happens after
-- submission, who may report, and any standing notice to the public. Each row
-- is one section, keyed so the app can request a specific one by name.
CREATE TABLE pca_info_sections (
    section_id      TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    section_key     VARCHAR(40) NOT NULL,   -- about, function, what_is_a_complaint, how_to_report, complaint_stages, who_may_report, notice
    title_en        VARCHAR(150) NOT NULL,
    title_st        VARCHAR(150) NOT NULL,
    body_en         TEXT NOT NULL,
    body_st         TEXT NOT NULL,
    display_order   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    UNIQUE KEY uq_section_key (section_key)
) ENGINE=InnoDB;

CREATE TABLE audit_log (
    log_id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NULL,
    staff_id        BIGINT UNSIGNED NULL,       -- set when the actor is a PCA staff member from the case-management system
    action          VARCHAR(60) NOT NULL,       -- e.g. LOGIN, COMPLAINT_SUBMIT, STATUS_CHANGE, EVIDENCE_UPLOAD
    entity_type     VARCHAR(40) NOT NULL,
    entity_id       BIGINT UNSIGNED NULL,
    details         VARCHAR(500) NULL,
    ip_address      VARCHAR(45) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    KEY ix_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB;

CREATE TABLE user_device_tokens (
    device_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT UNSIGNED NOT NULL,
    token_hash   CHAR(64) NOT NULL,
    device_token TEXT NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_token_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE KEY uq_device_token_hash (token_hash),
    KEY ix_device_token_user (user_id)
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------------------------------------------
-- 8. SEED DATA — reference tables only (no personal/complaint data)
-- ----------------------------------------------------------------------------

INSERT INTO complaint_status_types (code, label_en, label_st, color_code, sort_order) VALUES
('SUBMITTED',           'Pending',                    'E Emetse',                  'status-blue',   1),
('RECEIVED',            'Complaint Received',         'Tletlebo e Amohetsoe',       'status-blue',   2),
('ASSIGNED',            'Assigned for Investigation', 'E Abetsoe Bakeng sa Phuputso','status-blue',   3),
('UNDER_INVESTIGATION', 'Under Investigation',        'E ntse e Phenyekolloa',      'status-orange', 4),
('INFO_REQUESTED',      'Additional Information Required','Ho Hlokahala Tlhaiso e Eketsehileng','status-red', 5),
('COMPLETED',           'Investigation Completed',    'Phuputso e Phethiloe',       'status-green',  6),
('CLOSED',              'Case Closed',                'Nyeoe e Koetsoe',            'status-green',  7);

INSERT INTO complaint_categories (code, name_en, name_st, display_order) VALUES
('EXCESSIVE_FORCE',  'Police Brutality / Excessive Use of Force', 'Tlhekefetso ea Sepolesa', 1),
('ABUSE_AUTHORITY',  'Abuse of Authority', 'Tšebeliso mpe ea Matla', 2),
('CORRUPTION',       'Corruption / Bribery', 'Bobolu / Tjotjo', 3),
('UNLAWFUL_ARREST',  'Unlawful Arrest or Detention', 'Ts\'oaro kapa ho Koalloa ho sa Lumellanang le Molao', 4),
('HARASSMENT',       'Harassment', 'Tlhekefetso', 5),
('DISCRIMINATION',   'Discrimination', 'Khethollo', 6),
('DUTY_FAILURE',     'Failure to Perform Police Duties', 'Ho Hloleha ho Phetha Mosebetsi', 7),
('OTHER',            'Other Misconduct', 'Boitshwaro bo Bong bo Fosahetseng', 8);

INSERT INTO notification_types (code, label_en, label_st, is_essential) VALUES
('COMPLAINT_SUBMITTED', 'Complaint Submitted', 'Tletlebo e Rometsoe', 1),
('COMPLAINT_RECEIVED',  'Complaint Received', 'Tletlebo e Amohetsoe', 1),
('ASSIGNED',            'Complaint Assigned', 'Tletlebo e Abetsoe', 0),
('STATUS_CHANGED',      'Status Changed', 'Boemo bo Fetotsoe', 0),
('PROGRESS_UPDATE',     'Progress Update', 'Ntlafatso ea Tsoelo-pele', 0),
('INFO_REQUIRED',       'Additional Information Required', 'Ho Hlokahala Tlhaiso', 1),
('COMPLAINT_CLOSED',    'Complaint Closed', 'Tletlebo e Koetsoe', 1);

-- ----------------------------------------------------------------------------
-- 9. REAL PCA CONTENT — transcribed and translated from the official PCA
-- public information pamphlet ("E hlophisitsoe ke Lefapha la Litletlebo tsa
-- Sechaba ka Sepolesa: Lekala Sepolesa"). This replaces any placeholder
-- About/Contact copy — per the design spec, "Use official PCA information
-- once verified. Do not invent contact details." English wording is a plain
-- translation of the Sesotho original for parity between the two languages;
-- have PCA review it before publishing.
-- ----------------------------------------------------------------------------

INSERT INTO pca_contact_info (type, label_en, label_st, value, display_order) VALUES
('address', 'Postal Address', 'Aterese ea Poso',
 'Police Complaints Authority (PCA), Lekala Sepolesa, House No. 71 Maseru West, Qoqolosing Road, P.O. Box 866, Maseru, Lesotho',
 1),
('phone', 'Telephone', 'Nomoro ea Mohala', '22313720', 2),
('phone', 'Telephone', 'Nomoro ea Mohala', '22312007', 3);

INSERT INTO pca_info_sections (section_key, title_en, title_st, body_en, body_st, display_order) VALUES

('about',
 'What is the PCA?',
 'PCA ke eng?',
 'The PCA is the Department that deals with public complaints against the Police. Its motto is: "Foundation for peace! Respect for human rights and the rule of law."',
 'Ke Lefapha le sebetsanang le Litletlebo tsa Sechaba ka Sepolesa (PCA). Sehlomathiso sa lona ke: "Foundation for peace! Respect for human rights and the rule of law."',
 1),

('function',
 'Function of the Department',
 'Tšebetso ea Lefapha',
 'The main function of this Department is based on the Police Service Act No. 7 of 1998, section 22(3): "The Police Complaints Authority shall be responsible for investigating and reporting to the Minister on any complaint which the Minister or the Commissioner of Police has referred to this Authority for investigation."',
 'Mosebetsi o moholo oa Lefapha lena o itšetlehile ka Molao oa Sepolesa oa nomoro ea bosupa oa selemo sa 1998 (Police Service Act No. 7 of 1998), khaolo ea 22(3). "Lefapha la Litletlebo tsa Sechaba ka Sepolesa le tla ba le boikarabello ba ho batlisisa le ho tlalehela Letona ka pelaelo eo Letona kapa Mookameli oa Sepolesa ba e fileng lefapha lena hore le e batle ele pelaelo."',
 2),

('what_is_a_complaint',
 'What Can Be Reported',
 'Mefuta ea Litletlebo',
 'You may report: misconduct by a police officer or officers that violates fundamental human rights; failure to respond to or address public concerns in time; and dissatisfaction with an explanation given to you by the Police, or with the steps the Police followed regarding your complaint.',
 'Ke tšebetso e mpe ea setho kapa litho tsa Sepolesa e hatikelang litokelo tsa mantlha tsa botho. U ka tlaleha hape litaba tse amang le ho se arabe kapa ho se sebetse lipelaelo tsa sechaba ka nako. U ka tlaleha ho se khotsofale ke tlhaloso eo u e fuoang ke Sepolesa le methati eo Sepolesa se e latetseng tletlebong ea hao.',
 3),

('how_to_report',
 'How to Report',
 'Mokhoa oa ho Tlaleha',
 'If a person has a complaint about a police officer, they should submit the complaint to the Minister of Police or the Commissioner of Police, who will forward the complaint to this Department for investigation.',
 'Ha motho a na le tletlebo ka setho sa Sepolesa o lokela ho isa tletlebo ho Letona la Sepolesa kapa Mookameli oa Sepolesa bona ba tla fetisa tletlebo e joalo hore Lefapha le e batlisise.',
 4),

('complaint_stages',
 'Stages of a Complaint at the Department',
 'Methati ea Tletlebo Lefapheng',
 '1) Receipt of the complaint. 2) The Department has legal powers to investigate. 3) Once investigations are complete, the Department gives its recommendations to the Minister of Police. 4) The Minister instructs the Commissioner of Police to carry out the Department''s recommendations.',
 'Kamohelo ea tletlebo. Lefapha le ba le matla a Molao a ho batlisisa. Ha Lefapha le phethetse lipatlisiso, le fana ka likhothaletso tsa lona ho Letona la Sepolesa. Letona le tla laela Mookameli oa Sepolesa ho phethahatsa likhothaletso tsa Lefapha.',
 5),

('who_may_report',
 'Who May Report to the Department',
 'Ke Mang ea Lumelletsoeng ho Tlaleha Lefapheng',
 'The complainant, or someone representing them; Chiefs; Members of Parliament; Local Government Councillors; and community leaders generally.',
 'Molli kapa moamani le eena. Marena. Bakhethoa. Bakhethoa ba Puso ea Libaka (Councillor). Baetapele ba Sechaba ka kakaretso.',
 6),

('notice',
 'Notice',
 'Tlhokomeliso',
 'The public is free to approach the Department for guidance so a complainant can be assisted, even though the Department acts on behalf of the Minister of Police or the Commissioner of Police in taking up a matter.',
 'Sechaba se lokolohile ho atamela Lefapha molemong oa tataiso hore molli a thusoe, leha Lefapha le emela Letona la Sepolesa kapa Mookameli oa Sepolesa ho fana ka tumello.',
 7);

INSERT INTO faqs (question_en, question_st, answer_en, answer_st, display_order) VALUES

('What counts as a complaint against the Police?',
 'Ke eng se ka balloang tletlebo khahlanong le Sepolesa?',
 'Misconduct by a police officer that violates fundamental human rights; failure to respond to or address a public concern in time; or dissatisfaction with an explanation the Police gave you, or with the steps the Police followed regarding your complaint.',
 'Tšebetso e mpe ea setho sa Sepolesa e hatikelang litokelo tsa mantlha tsa botho; ho se arabe kapa ho se sebetse lipelaelo tsa sechaba ka nako; kapa ho se khotsofale ke tlhaloso eo u e fuoang ke Sepolesa kapa methati eo Sepolesa se e latetseng tletlebong ea hao.',
 1),

('Who can submit a complaint to the PCA?',
 'Ke mang ea ka romelang tletlebo ho PCA?',
 'The complainant themselves or their representative, Chiefs, Members of Parliament, Local Government Councillors, and community leaders generally.',
 'Molli kapa moamani le eena, Marena, Bakhethoa, Bakhethoa ba Puso ea Libaka (Councillor), le Baetapele ba Sechaba ka kakaretso.',
 2),

('What happens after I submit a complaint?',
 'Ho etsahalang ka mor''a hore ke romele tletlebo?',
 'The complaint is received, and the Department (which has legal powers to investigate) carries out its investigation. It then gives its recommendations to the Minister of Police, who instructs the Commissioner of Police to carry them out.',
 'Tletlebo ea amoheloa, mme Lefapha (le nang le matla a Molao a ho batlisisa) le etsa lipatlisiso. Ka mor''a moo le fana ka likhothaletso ho Letona la Sepolesa, eo a laelang Mookameli oa Sepolesa ho li phethahatsa.',
 3);
