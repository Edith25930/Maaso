CREATE TABLE IF NOT EXISTS email_action_tokens (
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
