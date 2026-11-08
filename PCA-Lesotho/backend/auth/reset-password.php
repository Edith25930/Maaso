<?php
require_once __DIR__ . '/../config/db.php';

function render_reset_page(string $message, string $token = '', int $status = 200): void {
    http_response_code($status);
    header('Content-Type: text/html; charset=utf-8');
    header('Cache-Control: no-store');
    header('Referrer-Policy: no-referrer');
    header('X-Content-Type-Options: nosniff');
    header('X-Frame-Options: DENY');
    header('Content-Security-Policy: default-src \'none\'; style-src \'unsafe-inline\'; form-action \'self\'; frame-ancestors \'none\'');
    $safeMessage = htmlspecialchars($message, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    echo '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
        . '<title>Reset PCA password</title><body style="font:16px Arial,sans-serif;margin:3rem auto;max-width:34rem;padding:1rem">'
        . '<h1>Police Complaints Authority</h1><p>' . $safeMessage . '</p>';
    if ($token !== '') {
        $safeToken = htmlspecialchars($token, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
        echo '<form method="post"><input type="hidden" name="token" value="' . $safeToken . '">'
            . '<label>New password <input type="password" name="password" minlength="8" autocomplete="new-password" required></label><br><br>'
            . '<label>Confirm password <input type="password" name="confirm_password" minlength="8" autocomplete="new-password" required></label><br><br>'
            . '<button type="submit">Reset password</button></form>';
    }
    echo '</body></html>';
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    $token = trim((string) ($_GET['token'] ?? ''));
    if (!preg_match('/^[a-f0-9]{64}$/', $token)) {
        render_reset_page('This password reset link is invalid or has expired.', '', 400);
    }
    $stmt = $pdo->prepare(
        "SELECT token_id FROM email_action_tokens
         WHERE token_hash = :hash AND purpose = 'reset_password'
           AND consumed_at IS NULL AND expires_at > NOW()"
    );
    $stmt->execute(['hash' => hash('sha256', $token)]);
    if (!$stmt->fetchColumn()) render_reset_page('This password reset link is invalid or has expired.', '', 400);
    render_reset_page('Choose a new password. It must contain at least 8 characters.', $token);
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    header('Allow: GET, POST');
    render_reset_page('Method not allowed.', '', 405);
}

$token = trim((string) ($_POST['token'] ?? ''));
$password = (string) ($_POST['password'] ?? '');
$confirmation = (string) ($_POST['confirm_password'] ?? '');
if (!preg_match('/^[a-f0-9]{64}$/', $token)) render_reset_page('This password reset link is invalid or has expired.', '', 400);
if (strlen($password) < 8 || $password !== $confirmation) {
    render_reset_page('Passwords must match and contain at least 8 characters.', $token, 400);
}

$pdo->beginTransaction();
try {
    $stmt = $pdo->prepare(
        "SELECT token_id, user_id FROM email_action_tokens
         WHERE token_hash = :hash AND purpose = 'reset_password'
           AND consumed_at IS NULL AND expires_at > NOW()
         FOR UPDATE"
    );
    $stmt->execute(['hash' => hash('sha256', $token)]);
    $action = $stmt->fetch();
    if (!$action) {
        $pdo->rollBack();
        render_reset_page('This password reset link is invalid or has expired.', '', 400);
    }
    $hash = password_hash($password, PASSWORD_BCRYPT);
    if ($hash === false) throw new RuntimeException('Password hashing failed.');
    $pdo->prepare('UPDATE users SET password_hash = :hash WHERE user_id = :user')
        ->execute(['hash' => $hash, 'user' => $action['user_id']]);
    $pdo->prepare('UPDATE user_sessions SET revoked_at = NOW() WHERE user_id = :user AND revoked_at IS NULL')
        ->execute(['user' => $action['user_id']]);
    $pdo->prepare('UPDATE email_action_tokens SET consumed_at = NOW() WHERE token_id = :id')
        ->execute(['id' => $action['token_id']]);
    $pdo->commit();
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('Password reset could not be completed.');
    render_reset_page('Password reset could not be completed. Please try again later.', $token, 500);
}

render_reset_page('Your password has been changed. Sign in again from the PCA mobile app.');
