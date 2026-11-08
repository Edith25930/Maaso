<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';
require_once __DIR__ . '/../config/email.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_error('Method not allowed', 405);
$userId = require_auth($pdo);
$stmt = $pdo->prepare('SELECT full_name, email, email_verified_at FROM users WHERE user_id = :id');
$stmt->execute(['id' => $userId]);
$user = $stmt->fetch();
if (!$user || !$user['email']) json_error('Add an email address to your account before verifying it.');
if ($user['email_verified_at'] !== null) json_ok(['verified' => true, 'email_sent' => false]);

try {
    $token = create_email_action_token($pdo, $userId, 'verify_email');
    send_email_action_link($user['email'], $user['full_name'], 'verify_email', $token);
} catch (RuntimeException $e) {
    error_log('Email verification message was not delivered.');
    json_error('Email verification could not be sent. Please try again later.', 503);
}
json_ok(['verified' => false, 'email_sent' => true]);
