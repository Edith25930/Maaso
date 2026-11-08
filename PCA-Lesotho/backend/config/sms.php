<?php

$smsLocalPath = __DIR__ . '/sms.local.php';
$smsLocalConfig = is_file($smsLocalPath) ? require $smsLocalPath : [];
if (!is_array($smsLocalConfig)) {
    throw new RuntimeException('The local SMS configuration must return an array.');
}

function pca_sms_setting(string $environmentName, string $configKey): string {
    global $smsLocalConfig;
    $environmentValue = getenv($environmentName);
    if ($environmentValue !== false && $environmentValue !== '') return trim((string) $environmentValue);
    return trim((string) ($smsLocalConfig[$configKey] ?? ''));
}

function send_complaint_confirmation_sms(PDO $pdo, int $userId, string $gcrNumber): void {
    $username = pca_sms_setting('PCA_AT_USERNAME', 'username');
    $apiKey = pca_sms_setting('PCA_AT_API_KEY', 'api_key');
    $senderId = pca_sms_setting('PCA_AT_SENDER_ID', 'sender_id');

    $stmt = $pdo->prepare('SELECT phone_number FROM users WHERE user_id = :user_id');
    $stmt->execute(['user_id' => $userId]);
    $phoneNumber = $stmt->fetchColumn();
    if (!is_string($phoneNumber) || !preg_match('/^\+[1-9]\d{6,14}$/', $phoneNumber)) {
        throw new RuntimeException('The account phone number is unavailable for SMS delivery.');
    }

    $message = 'PCA Lesotho: Your complaint has been received. GCR: '
        . $gcrNumber . '. Keep this reference for future enquiries.';

    // Mode 1: FREE Local Logging Mode (Default if no credentials provided or username is 'log')
    if ($username === '' || $username === 'log' || $apiKey === '') {
        $logEntry = sprintf(
            "[%s] FREE SMS LOG -> To: %s | Message: %s\n",
            date('Y-m-d H:i:s'),
            $phoneNumber,
            $message
        );
        $storageDir = __DIR__ . '/../storage';
        if (!is_dir($storageDir)) {
            @mkdir($storageDir, 0755, true);
        }
        @file_put_contents($storageDir . '/sms_log.txt', $logEntry, FILE_APPEND);
        error_log("FREE SMS LOG: Sent to {$phoneNumber}: {$message}");
        return;
    }

    // Mode 2: Africa's Talking API (Supports both FREE Sandbox 'sandbox' and Live Production)
    if (!function_exists('curl_init')) {
        throw new RuntimeException('PHP cURL is required for SMS delivery.');
    }

    $isSandbox = strtolower($username) === 'sandbox';
    $apiUrl = $isSandbox
        ? 'https://api.sandbox.africastalking.com/version1/messaging'
        : 'https://api.africastalking.com/version1/messaging';

    $form = [
        'username' => $username,
        'to' => $phoneNumber,
        'message' => $message,
    ];
    if ($senderId !== '') $form['from'] = $senderId;

    $curl = curl_init($apiUrl);
    curl_setopt_array($curl, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => http_build_query($form),
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CONNECTTIMEOUT => 10,
        CURLOPT_TIMEOUT => 20,
        CURLOPT_SSL_VERIFYPEER => true,
        CURLOPT_SSL_VERIFYHOST => 2,
        CURLOPT_HTTPHEADER => [
            'apiKey: ' . $apiKey,
            'Accept: application/json',
            'Content-Type: application/x-www-form-urlencoded',
        ],
    ]);
    $response = curl_exec($curl);
    $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
    $curlError = curl_error($curl);
    curl_close($curl);

    if ($response === false) {
        throw new RuntimeException('Africa\'s Talking SMS request failed: ' . $curlError);
    }
    if ($status < 200 || $status >= 300) {
        throw new RuntimeException('Africa\'s Talking rejected the SMS request (HTTP ' . $status . ').');
    }

    $data = json_decode($response, true);
    $recipients = $data['SMSMessageData']['Recipients'] ?? null;
    if (!is_array($recipients) || count($recipients) < 1
            || !in_array($recipients[0]['status'] ?? '', ['Success', 'Sent'], true)) {
        throw new RuntimeException('Africa\'s Talking did not accept the complaint confirmation SMS.');
    }
}
