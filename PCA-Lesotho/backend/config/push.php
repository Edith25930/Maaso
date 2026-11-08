<?php

function base64url_encode(string $value): string {
    return rtrim(strtr(base64_encode($value), '+/', '-_'), '=');
}

function firebase_service_account(): ?array {
    $localPath = __DIR__ . '/push.local.php';
    $localConfig = is_file($localPath) ? require $localPath : [];
    if (!is_array($localConfig)) {
        throw new RuntimeException('The local Firebase configuration must return an array.');
    }
    $projectId = trim((string) (getenv('PCA_FCM_PROJECT_ID') ?: ($localConfig['project_id'] ?? '')));
    $accountPath = trim((string) (getenv('PCA_FCM_SERVICE_ACCOUNT') ?: ($localConfig['service_account_path'] ?? '')));
    if ($projectId === '' && $accountPath === '') return null;
    if ($projectId === '' || $accountPath === '') {
        throw new RuntimeException('Firebase push configuration is incomplete.');
    }

    $path = realpath($accountPath);
    if (!$path || !is_file($path) || !is_readable($path)) {
        throw new RuntimeException('Firebase service-account file is unavailable.');
    }
    $account = json_decode((string) file_get_contents($path), true);
    if (!is_array($account)
            || ($account['type'] ?? '') !== 'service_account'
            || ($account['project_id'] ?? '') !== $projectId
            || empty($account['client_email'])
            || empty($account['private_key'])
            || ($account['token_uri'] ?? '') !== 'https://oauth2.googleapis.com/token') {
        throw new RuntimeException('Firebase service-account configuration is invalid.');
    }
    return $account;
}

function firebase_access_token(array $account): string {
    if (!function_exists('curl_init') || !function_exists('openssl_sign')) {
        throw new RuntimeException('PHP cURL and OpenSSL extensions are required for Firebase push.');
    }
    $issuedAt = time();
    $header = base64url_encode((string) json_encode(['alg' => 'RS256', 'typ' => 'JWT']));
    $claims = base64url_encode((string) json_encode([
        'iss' => $account['client_email'],
        'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
        'aud' => $account['token_uri'],
        'iat' => $issuedAt,
        'exp' => $issuedAt + 3600,
    ]));
    $unsigned = $header . '.' . $claims;
    $key = openssl_pkey_get_private($account['private_key']);
    if ($key === false || !openssl_sign($unsigned, $signature, $key, OPENSSL_ALGO_SHA256)) {
        throw new RuntimeException('Firebase service-account key could not sign a token.');
    }
    $assertion = $unsigned . '.' . base64url_encode($signature);
    $curl = curl_init($account['token_uri']);
    curl_setopt_array($curl, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => http_build_query([
            'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            'assertion' => $assertion,
        ]),
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 15,
        CURLOPT_HTTPHEADER => ['Content-Type: application/x-www-form-urlencoded'],
    ]);
    $response = curl_exec($curl);
    $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    $curlError = curl_error($curl);
    curl_close($curl);
    if ($response === false || $status < 200 || $status >= 300) {
        throw new RuntimeException($response === false
            ? 'Firebase access-token request failed: ' . $curlError
            : 'Firebase access-token request was rejected.');
    }
    $data = json_decode($response, true);
    if (!is_array($data) || empty($data['access_token'])) {
        throw new RuntimeException('Firebase access-token response was invalid.');
    }
    return $data['access_token'];
}

function send_firebase_complaint_update(array $account, string $accessToken, string $deviceToken, int $complaintId): bool {
    $projectId = $account['project_id'];
    $url = 'https://fcm.googleapis.com/v1/projects/' . rawurlencode($projectId) . '/messages:send';
    $payload = json_encode([
        'message' => [
            'token' => $deviceToken,
            'data' => ['complaint_id' => (string) $complaintId],
            'android' => ['priority' => 'HIGH'],
        ],
    ], JSON_UNESCAPED_SLASHES);
    if ($payload === false) throw new RuntimeException('Firebase message could not be encoded.');

    $curl = curl_init($url);
    curl_setopt_array($curl, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $payload,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 15,
        CURLOPT_HTTPHEADER => [
            'Authorization: Bearer ' . $accessToken,
            'Content-Type: application/json',
        ],
    ]);
    $response = curl_exec($curl);
    $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    $curlError = curl_error($curl);
    curl_close($curl);
    if ($response === false) throw new RuntimeException('Firebase message request failed: ' . $curlError);
    if ($status >= 200 && $status < 300) return true;

    $error = json_decode($response, true);
    if ($status === 404 && ($error['error']['details'][0]['errorCode'] ?? '') === 'UNREGISTERED') {
        return false;
    }
    throw new RuntimeException('Firebase rejected a push message (HTTP ' . $status . ').');
}
