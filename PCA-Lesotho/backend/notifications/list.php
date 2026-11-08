<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$userId = require_auth($pdo);
$stmt = $pdo->prepare(
    "SELECT notification_id, complaint_id, title_en, title_st, body_en, body_st, is_read, created_at
     FROM notifications WHERE user_id = :uid ORDER BY created_at DESC"
);
$stmt->execute(['uid' => $userId]);
json_ok($stmt->fetchAll());
