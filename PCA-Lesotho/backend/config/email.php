<?php

$emailLocalPath = __DIR__ . '/email.local.php';
$emailLocalConfig = is_file($emailLocalPath) ? require $emailLocalPath : [];
if (!is_array($emailLocalConfig)) {
    throw new RuntimeException('The local email configuration must return an array.');
}

function pca_email_setting(string $environmentName, string $configKey, string $default = ''): string {
    global $emailLocalConfig;
    $environmentValue = getenv($environmentName);
    if ($environmentValue !== false && $environmentValue !== '') return (string) $environmentValue;
    return (string) ($emailLocalConfig[$configKey] ?? $default);
}

function send_smtp_email(string $to, string $recipientName, string $subject, string $htmlBody): void {
    $host = trim(pca_email_setting('PCA_SMTP_HOST', 'smtp_host'));
    $port = (int) pca_email_setting('PCA_SMTP_PORT', 'smtp_port', '587');
    $username = pca_email_setting('PCA_SMTP_USERNAME', 'smtp_username');
    $password = pca_email_setting('PCA_SMTP_PASSWORD', 'smtp_password');
    $encryption = strtolower(trim(pca_email_setting('PCA_SMTP_ENCRYPTION', 'smtp_encryption', 'tls')));
    $fromEmail = trim(pca_email_setting('PCA_SMTP_FROM_EMAIL', 'from_email'));
    $fromName = trim(pca_email_setting('PCA_SMTP_FROM_NAME', 'from_name', 'Police Complaints Authority'));

    if ($host === '' || $fromEmail === '' || !filter_var($fromEmail, FILTER_VALIDATE_EMAIL)
            || $port < 1 || $port > 65535 || !in_array($encryption, ['tls', 'ssl'], true)) {
        throw new RuntimeException('Email delivery is not configured.');
    }

    $baseUrl = rtrim(trim(pca_email_setting('PCA_PUBLIC_BASE_URL', 'public_base_url')), '/');
    $parts = $baseUrl !== '' ? parse_url($baseUrl) : false;
    if (!$parts || ($parts['scheme'] ?? '') !== 'https' || empty($parts['host'])
            || isset($parts['user']) || isset($parts['pass'])
            || isset($parts['query']) || isset($parts['fragment'])) {
        throw new RuntimeException('The public HTTPS base URL is not configured.');
    }
    if (!is_file(__DIR__ . '/../vendor/autoload.php')) {
        throw new RuntimeException('The email library is not installed on the server.');
    }

    require_once __DIR__ . '/../vendor/autoload.php';
    $mailer = new PHPMailer\PHPMailer\PHPMailer(true);
    $mailer->isSMTP();
    $mailer->Host = $host;
    $mailer->Port = $port;
    $mailer->SMTPAuth = $username !== '';
    if ($mailer->SMTPAuth) {
        $mailer->Username = $username;
        $mailer->Password = $password;
    }
    try {
        $mailer->SMTPSecure = $encryption === 'ssl'
            ? PHPMailer\PHPMailer\PHPMailer::ENCRYPTION_SMTPS
            : PHPMailer\PHPMailer\PHPMailer::ENCRYPTION_STARTTLS;
        $mailer->Timeout = 15;
        $mailer->CharSet = PHPMailer\PHPMailer\PHPMailer::CHARSET_UTF8;
        $mailer->setFrom($fromEmail, $fromName);
        $mailer->addAddress($to, $recipientName);
        $mailer->isHTML(true);
        $mailer->Subject = $subject;
        $mailer->Body = $htmlBody;
        $mailer->AltBody = trim(preg_replace('/\s+/', ' ', strip_tags(str_replace(['<br>', '<br/>', '<br />'], "\n", $htmlBody))));
        $mailer->send();
    } catch (PHPMailer\PHPMailer\Exception $e) {
        throw new RuntimeException('The SMTP server did not accept the email.');
    }
}

function create_email_action_token(PDO $pdo, int $userId, string $purpose): string {
    if (!in_array($purpose, ['verify_email', 'reset_password'], true)) {
        throw new InvalidArgumentException('Unsupported email action.');
    }

    $rawToken = bin2hex(random_bytes(32));
    $pdo->beginTransaction();
    try {
        $pdo->prepare('SELECT user_id FROM users WHERE user_id = :user FOR UPDATE')
            ->execute(['user' => $userId]);
        $recent = $pdo->prepare(
            'SELECT token_id FROM email_action_tokens
             WHERE user_id = :user AND purpose = :purpose
               AND created_at > DATE_SUB(NOW(), INTERVAL 60 SECOND)
             LIMIT 1'
        );
        $recent->execute(['user' => $userId, 'purpose' => $purpose]);
        if ($recent->fetchColumn()) {
            $pdo->rollBack();
            throw new RuntimeException('Please wait before requesting another email.');
        }
        $pdo->prepare(
            'UPDATE email_action_tokens SET consumed_at = NOW()
             WHERE user_id = :user AND purpose = :purpose AND consumed_at IS NULL'
        )->execute(['user' => $userId, 'purpose' => $purpose]);
        $expiresInMinutes = $purpose === 'reset_password' ? 30 : 60;
        $pdo->prepare(
            'INSERT INTO email_action_tokens (user_id, purpose, token_hash, expires_at)
             VALUES (:user, :purpose, :hash, DATE_ADD(NOW(), INTERVAL ' . $expiresInMinutes . ' MINUTE))'
        )->execute([
                'user' => $userId,
                'purpose' => $purpose,
                'hash' => hash('sha256', $rawToken),
        ]);
        $pdo->commit();
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $e;
    }
    return $rawToken;
}

function send_email_action_link(string $email, string $fullName, string $purpose, string $token): void {
    $baseUrl = rtrim(trim(pca_email_setting('PCA_PUBLIC_BASE_URL', 'public_base_url')), '/');
    $route = $purpose === 'verify_email' ? 'auth/verify-email.php' : 'auth/reset-password.php';
    $url = $baseUrl . '/' . $route . '?token=' . rawurlencode($token);
    $safeName = htmlspecialchars($fullName, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    $safeUrl = htmlspecialchars($url, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    $isVerify = $purpose === 'verify_email';
    $subject = $isVerify ? 'Welcome to PCA — verify your email address' : 'Reset your PCA account password';
    $actionEn = $isVerify ? 'Verify email address' : 'Choose a new password';
    $actionSt = $isVerify ? 'Netefatsa aterese ea lengolo-tsoibila' : 'Khetha phasewete e ncha';
    $expires = $isVerify ? 'This link expires in 60 minutes.' : 'This link expires in 30 minutes.';
    $html = '<!doctype html><html lang="en"><meta charset="utf-8"><body style="font:16px Arial,sans-serif;color:#17243a">'
        . '<h2>Police Complaints Authority — Lesotho</h2><p>Hello ' . $safeName . ',</p>'
        . ($isVerify
            ? '<p>Welcome to the PCA mobile app. Your account has been created; verify your email address to keep your account details up to date.</p>'
            : '<p>Follow this link to ' . strtolower($actionEn) . ' for your PCA account.</p>')
        . '<p><a href="' . $safeUrl . '">' . $actionEn . '</a></p>'
        . '<p>' . $expires . ' If you did not request this, you can ignore this email.</p>'
        . '<hr><p lang="st">' . $actionSt . '. Haeba u sa kopa sena, hlokomoloha lengolo lena.</p></body></html>';
    send_smtp_email($email, $fullName, $subject, $html);
}
