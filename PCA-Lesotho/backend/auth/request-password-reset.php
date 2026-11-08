<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../config/email.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_error('Method not allowed', 405);
$body = read_json_body();
$email = strtolower(trim((string) ($body['email'] ?? '')));
if ($email === '' || strlen($email) > 150 || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
    json_error('Enter a valid email address.');
}

$stmt = $pdo->prepare(
    "SELECT user_id, full_name, email FROM users
     WHERE email = :email AND email_verified_at IS NOT NULL AND account_status = 'active'
     LIMIT 1"
);
$stmt->execute(['email' => $email]);
$user = $stmt->fetch();
if ($user) {
    try {
        $token = create_email_action_token($pdo, (int) $user['user_id'], 'reset_password');
        send_email_action_link($user['email'], $user['full_name'], 'reset_password', $token);
    } catch (RuntimeException $e) {
        error_log('Password reset email was not delivered.');
    }
}

json_ok(['message' => 'If an active account with a verified email exists, password reset instructions will be sent.']);
