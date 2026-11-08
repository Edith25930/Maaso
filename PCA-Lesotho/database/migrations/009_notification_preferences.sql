CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id                     BIGINT UNSIGNED PRIMARY KEY,
    push_enabled                TINYINT(1) NOT NULL DEFAULT 1,
    email_enabled               TINYINT(1) NOT NULL DEFAULT 1,
    status_update_enabled       TINYINT(1) NOT NULL DEFAULT 1,
    general_info_enabled        TINYINT(1) NOT NULL DEFAULT 1,
    updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifpref_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT IGNORE INTO notification_preferences (user_id)
SELECT user_id FROM users;
