<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_error('Method not allowed', 405);
$userId = require_auth($pdo);
$body = read_json_body();
$deviceToken = trim((string) ($body['device_token'] ?? ''));
if (strlen($deviceToken) < 20 || strlen($deviceToken) > 4096) {
    json_error('Invalid device token.');
}

$tokenHash = hash('sha256', $deviceToken);
$stmt = $pdo->prepare(
    "INSERT INTO user_device_tokens (user_id, token_hash, device_token)
     VALUES (:user, :hash, :token)
     ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), device_token = VALUES(device_token),
                             updated_at = CURRENT_TIMESTAMP"
);
$stmt->execute(['user' => $userId, 'hash' => $tokenHash, 'token' => $deviceToken]);
json_ok(['registered' => true]);
