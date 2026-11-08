<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$userId = require_auth($pdo);
$headers = getallheaders();
preg_match('/Bearer\s+(\S+)/i', $headers['Authorization'] ?? '', $m);
$tokenHash = hash('sha256', $m[1] ?? '');

$pdo->prepare("UPDATE user_sessions SET revoked_at = NOW() WHERE token_hash = :hash AND user_id = :uid")
    ->execute(['hash' => $tokenHash, 'uid' => $userId]);

json_ok(['message' => 'Logged out']);
