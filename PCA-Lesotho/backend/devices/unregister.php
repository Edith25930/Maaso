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

$pdo->prepare("DELETE FROM user_device_tokens WHERE user_id = :user AND token_hash = :hash")
    ->execute(['user' => $userId, 'hash' => hash('sha256', $deviceToken)]);
json_ok(['unregistered' => true]);
