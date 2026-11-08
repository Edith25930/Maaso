<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$userId = require_auth($pdo);
$body = read_json_body();
$id = (int) ($body['notification_id'] ?? 0);

if ($id) {
    $pdo->prepare("UPDATE notifications SET is_read = 1 WHERE notification_id = :id AND user_id = :uid")
        ->execute(['id' => $id, 'uid' => $userId]);
} else {
    $pdo->prepare("UPDATE notifications SET is_read = 1 WHERE user_id = :uid")->execute(['uid' => $userId]);
}
json_ok(['message' => 'Updated']);
