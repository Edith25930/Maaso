<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';

// Guests get a short-lived token tied to a placeholder guest user row,
// scoped by the API layer to read-only endpoints (Learn, PCA info, FAQs).
$rawToken  = bin2hex(random_bytes(32));
$tokenHash = hash('sha256', $rawToken);

$stmt = $pdo->prepare("INSERT INTO users (full_name, phone_number, password_hash, is_guest) VALUES ('Guest', CONCAT('guest-', UUID()), '', 1)");
$stmt->execute();
$guestId = (int) $pdo->lastInsertId();

$pdo->prepare(
    "INSERT INTO user_sessions (user_id, token_hash, device_label, expires_at)
     VALUES (:uid, :hash, 'guest-session', DATE_ADD(NOW(), INTERVAL 1 DAY))"
)->execute(['uid' => $guestId, 'hash' => $tokenHash]);

json_ok(['token' => $rawToken, 'user_id' => $guestId, 'is_guest' => true]);
