<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_error('Method not allowed', 405);
}

$body = read_json_body();
$identifier = trim($body['identifier'] ?? ''); // email OR phone
$password   = $body['password'] ?? '';

if ($identifier === '' || $password === '') {
    json_error('Incorrect email/phone number or password.', 401);
}

$phoneDigits = preg_replace('/\D/', '', $identifier);
$legacyPhone = str_starts_with($phoneDigits, '266') && strlen($phoneDigits) > 8
    ? substr($phoneDigits, 3) : $phoneDigits;
$internationalPhone = preg_match('/^\d{8}$/', $phoneDigits)
    ? '+266' . $phoneDigits
    : (str_starts_with($phoneDigits, '266') ? '+' . $phoneDigits : $identifier);
$stmt = $pdo->prepare(
    "SELECT user_id, full_name, phone_number, email, id_number, password_hash, account_status, role
     FROM users
     WHERE phone_number IN (:phone, :phone_digits, :legacy_phone, :international_phone)
        OR email = :email
     LIMIT 1"
);
$stmt->execute([
    'phone' => $identifier,
    'phone_digits' => $phoneDigits,
    'legacy_phone' => $legacyPhone,
    'international_phone' => $internationalPhone,
    'email' => $identifier,
]);
$user = $stmt->fetch();

// Deliberately generic failure message — never reveal which field was wrong.
if (!$user || !password_verify($password, $user['password_hash'])) {
    json_error('Incorrect email/phone number or password.', 401);
}
if ($user['account_status'] !== 'active') {
    json_error('This account is not active. Please contact PCA support.', 403);
}

// Issue a bearer token; only its SHA-256 hash is stored.
$rawToken  = bin2hex(random_bytes(32));
$tokenHash = hash('sha256', $rawToken);
$expiresAt = (new DateTime('+30 days'))->format('Y-m-d H:i:s');

$pdo->prepare(
    "INSERT INTO user_sessions (user_id, token_hash, device_label, expires_at)
     VALUES (:uid, :hash, :device, :expires)"
)->execute([
    'uid'     => $user['user_id'],
    'hash'    => $tokenHash,
    'device'  => $body['device_label'] ?? 'android-app',
    'expires' => $expiresAt,
]);

json_ok([
    'token'   => $rawToken,
    'user_id' => (int) $user['user_id'],
    'full_name' => $user['full_name'],
    'phone_number' => $user['phone_number'],
    'email' => $user['email'],
    'id_number' => $user['id_number'],
    'role' => $user['role'],
]);
