<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../config/email.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_error('Method not allowed', 405);
}

$body = read_json_body();
$fullName = trim($body['full_name'] ?? '');
$phone    = trim($body['phone_number'] ?? '');
$email    = strtolower(trim((string) ($body['email'] ?? ''))) ?: null;
$districtId = isset($body['district_id']) ? (int) $body['district_id'] : null;
$idNumber = trim($body['id_number'] ?? '');
$phone = preg_replace('/\s+/', '', $phone);
$password = $body['password'] ?? '';
$confirm  = $body['confirm_password'] ?? '';
$agreed   = (bool) ($body['agreed_to_privacy_notice'] ?? false);

if ($fullName === '' || $phone === '' || $password === '' || $idNumber === '') {
    json_error('Full name, ID number, phone number and password are required.');
}
if (strlen($idNumber) > 100) {
    json_error('ID number must not exceed 100 characters.');
}
if ($email !== null && (strlen($email) > 150 || !filter_var($email, FILTER_VALIDATE_EMAIL))) {
    json_error('Enter a valid email address.');
}
if (!preg_match('/^\+[1-9]\d{6,14}$/', $phone)) {
    json_error('Enter a valid international phone number.');
}
if ($password !== $confirm) {
    json_error('Passwords do not match.');
}
if (strlen($password) < 8) {
    json_error('Password must be at least 8 characters.');
}
if (!$agreed) {
    json_error('You must agree to the privacy notice and terms.');
}

// Uniqueness checks
$phoneDigits = preg_replace('/\D/', '', $phone);
$legacyPhone = str_starts_with($phoneDigits, '266') && strlen($phoneDigits) > 8
    ? substr($phoneDigits, 3) : $phoneDigits;
$phoneCheck = $pdo->prepare(
    'SELECT user_id FROM users
     WHERE phone_number IN (:phone, :phone_digits, :legacy_phone)'
);
$phoneCheck->execute([
    'phone' => $phone,
    'phone_digits' => $phoneDigits,
    'legacy_phone' => $legacyPhone,
]);
if ($phoneCheck->fetch()) {
    json_error('An account with this phone number already exists. Please sign in.', 409);
}
if ($email !== null) {
    $emailCheck = $pdo->prepare('SELECT user_id FROM users WHERE email = :email LIMIT 1');
    $emailCheck->execute(['email' => $email]);
    if ($emailCheck->fetch()) {
        json_error('An account with this email already exists. Please sign in.', 409);
    }
}

$passwordHash = password_hash($password, PASSWORD_BCRYPT);

$pdo->beginTransaction();
try {
    $stmt = $pdo->prepare(
        "INSERT INTO users (full_name, phone_number, email, password_hash, district_id, id_number, preferred_language)
         VALUES (:full_name, :phone, :email, :password_hash, :district_id, :id_number, :lang)"
    );
    $stmt->execute([
        'full_name'      => $fullName,
        'phone'          => $phone,
        'email'          => $email,
        'password_hash'  => $passwordHash,
        'district_id'    => $districtId,
        'id_number'      => $idNumber,
        'lang'           => $body['preferred_language'] ?? 'en',
    ]);

    $userId = (int) $pdo->lastInsertId();
    $pdo->prepare("INSERT INTO notification_preferences (user_id) VALUES (:uid)")
        ->execute(['uid' => $userId]);
    $pdo->commit();
} catch (Throwable $error) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('Account registration failed during database setup: ' . $error->getMessage());
    json_error('Could not complete account setup. Please try again later.', 500);
}

$verificationEmailSent = false;
if ($email !== null) {
    try {
        $token = create_email_action_token($pdo, $userId, 'verify_email');
        send_email_action_link($email, $fullName, 'verify_email', $token);
        $verificationEmailSent = true;
    } catch (Throwable $error) {
        error_log('Registration verification email was not delivered: ' . $error->getMessage());
    }
}

json_ok([
    'user_id' => $userId,
    'email_verification_required' => $email !== null,
    'email_verification_sent' => $verificationEmailSent,
], 201);
