<?php
require_once __DIR__ . '/../config/db.php';

function render_verification_result(string $message, int $status, string $token = ''): void {
    http_response_code($status);
    header('Content-Type: text/html; charset=utf-8');
    header('Cache-Control: no-store');
    header('Referrer-Policy: no-referrer');
    header('X-Content-Type-Options: nosniff');
    header('Content-Security-Policy: default-src \'none\'; style-src \'unsafe-inline\'; form-action \'self\'; frame-ancestors \'none\'');
    echo '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
        . '<title>PCA email verification</title><body style="font:18px Arial,sans-serif;margin:3rem auto;max-width:38rem;padding:1rem">'
        . '<h1>Police Complaints Authority</h1><p>' . htmlspecialchars($message, ENT_QUOTES, 'UTF-8') . '</p>';
    if ($token !== '') {
        $safeToken = htmlspecialchars($token, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
        echo '<form method="post"><input type="hidden" name="token" value="' . $safeToken . '">'
            . '<button type="submit">Verify email address</button></form>';
    }
    echo '</body></html>';
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    $token = trim((string) ($_GET['token'] ?? ''));
    if (!preg_match('/^[a-f0-9]{64}$/', $token)) {
        render_verification_result('This verification link is invalid or has expired.', 400);
    }
    $stmt = $pdo->prepare(
        "SELECT token_id FROM email_action_tokens
         WHERE token_hash = :hash AND purpose = 'verify_email'
           AND consumed_at IS NULL AND expires_at > NOW()"
    );
    $stmt->execute(['hash' => hash('sha256', $token)]);
    if (!$stmt->fetchColumn()) {
        render_verification_result('This verification link is invalid or has expired.', 400);
    }
    render_verification_result('Confirm your email address to complete verification.', 200, $token);
}
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    header('Allow: GET, POST');
    render_verification_result('Method not allowed.', 405);
}

$token = trim((string) ($_POST['token'] ?? ''));
if (!preg_match('/^[a-f0-9]{64}$/', $token)) render_verification_result('This verification link is invalid or has expired.', 400);

$pdo->beginTransaction();
try {
    $stmt = $pdo->prepare(
        "SELECT token_id, user_id FROM email_action_tokens
         WHERE token_hash = :hash AND purpose = 'verify_email'
           AND consumed_at IS NULL AND expires_at > NOW()
         FOR UPDATE"
    );
    $stmt->execute(['hash' => hash('sha256', $token)]);
    $action = $stmt->fetch();
    if (!$action) {
        $pdo->rollBack();
        render_verification_result('This verification link is invalid or has expired.', 400);
    }
    $pdo->prepare('UPDATE users SET email_verified_at = COALESCE(email_verified_at, NOW()) WHERE user_id = :user')
        ->execute(['user' => $action['user_id']]);
    $pdo->prepare('UPDATE email_action_tokens SET consumed_at = NOW() WHERE token_id = :id')
        ->execute(['id' => $action['token_id']]);
    $pdo->commit();
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('Email verification could not be completed.');
    render_verification_result('Verification could not be completed. Please try again later.', 500);
}

render_verification_result('Your email address is verified. You can return to the PCA mobile app.', 200);
