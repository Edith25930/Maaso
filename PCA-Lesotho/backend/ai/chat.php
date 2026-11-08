<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_error('Method not allowed', 405);
}

$userId = require_auth($pdo);
$apiKey = getenv('GEMINI_API_KEY');
$localAiConfigPath = __DIR__ . '/../config/ai.local.php';
$localAiConfig = is_file($localAiConfigPath) ? require $localAiConfigPath : [];
if (!is_array($localAiConfig)) {
    error_log('The local AI assistant configuration must return an array.');
    json_error('The live assistant configuration is invalid.', 503);
}
if (!is_string($apiKey) || trim($apiKey) === '') {
    $apiKey = $localAiConfig['api_key'] ?? '';
}
if (!is_string($apiKey) || trim($apiKey) === '') {
    json_error('The live assistant is not configured yet. Please try again later.', 503);
}
$apiKey = trim($apiKey);
if (!function_exists('curl_init')) {
    json_error('The live assistant is unavailable on this server.', 503);
}

$body = read_json_body();
$messages = $body['messages'] ?? null;
$language = ($body['language'] ?? 'en') === 'st' ? 'Sesotho' : 'English';
if (!is_array($messages) || count($messages) < 1 || count($messages) > 12) {
    json_error('Send between 1 and 12 chat messages.');
}

$validatedMessages = [];
$totalLength = 0;
foreach ($messages as $message) {
    if (!is_array($message)
        || !in_array($message['role'] ?? null, ['user', 'assistant'], true)
        || !is_string($message['content'] ?? null)) {
        json_error('The chat messages are invalid.');
    }
    $content = trim($message['content']);
    $contentLength = function_exists('mb_strlen')
        ? mb_strlen($content, 'UTF-8')
        : strlen($content);
    if ($content === '' || $contentLength > 2000) {
        json_error('Each message must contain 1 to 2,000 characters.');
    }
    $totalLength += $contentLength;
    if ($totalLength > 8000) {
        json_error('This conversation is too long. Start a new chat.');
    }
    $validatedMessages[] = ['role' => $message['role'], 'content' => $content];
}
if (end($validatedMessages)['role'] !== 'user') {
    json_error('Send a message to continue the conversation.');
}

$recent = $pdo->prepare(
    'SELECT COUNT(*) FROM ai_assistant_requests
     WHERE user_id = :uid AND requested_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE)'
);
$recent->execute(['uid' => $userId]);
if ((int) $recent->fetchColumn() >= 10) {
    json_error('You are sending messages too quickly. Please wait a moment.', 429);
}
$daily = $pdo->prepare(
    'SELECT COUNT(*) FROM ai_assistant_requests
     WHERE user_id = :uid AND requested_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)'
);
$daily->execute(['uid' => $userId]);
if ((int) $daily->fetchColumn() >= 50) {
    json_error('You have reached today’s assistant limit. Please try again tomorrow.', 429);
}
$pdo->prepare('INSERT INTO ai_assistant_requests (user_id) VALUES (:uid)')
    ->execute(['uid' => $userId]);

$systemPrompt = "You are a careful writing assistant for people preparing a complaint to the Police Complaints Authority of Lesotho. Respond in {$language}. Help the person describe events clearly in their own words. Ask one relevant follow-up question at a time when useful. Never invent, embellish, or assume facts. Do not decide whether misconduct occurred, whether a complaint is valid, what legal outcome is likely, or how the PCA should classify or resolve it. Do not present yourself as a lawyer or as the PCA. Avoid asking for names, ID numbers, phone numbers, addresses, or other unnecessary identifying details; encourage describing people by role instead. If the person describes immediate danger, advise them to contact local emergency services or a trusted person. Be respectful, neutral, concise, and supportive.";
$model = getenv('GEMINI_MODEL');
if (!is_string($model) || trim($model) === '') {
    $model = $localAiConfig['model'] ?? 'gemini-3.5-flash-lite';
}
if (!is_string($model) || trim($model) === '') {
    $model = 'gemini-3.5-flash-lite';
}
$requestJson = json_encode([
    'systemInstruction' => [
        'parts' => [['text' => $systemPrompt]],
    ],
    'contents' => array_map(
        static function (array $message): array {
            return [
                'role' => $message['role'] === 'assistant' ? 'model' : 'user',
                'parts' => [['text' => $message['content']]],
            ];
        },
        $validatedMessages
    ),
    'generationConfig' => ['maxOutputTokens' => 700],
], JSON_UNESCAPED_UNICODE);
if ($requestJson === false) {
    json_error('Could not prepare the assistant request.', 500);
}

$curl = curl_init(
    'https://generativelanguage.googleapis.com/v1beta/models/'
    . rawurlencode(trim($model))
    . ':generateContent'
);
curl_setopt_array($curl, [
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => $requestJson,
    CURLOPT_HTTPHEADER => [
        'x-goog-api-key: ' . $apiKey,
        'Content-Type: application/json',
    ],
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_CONNECTTIMEOUT => 10,
    CURLOPT_TIMEOUT => 45,
]);
$response = curl_exec($curl);
$curlError = curl_error($curl);
$httpStatus = (int) curl_getinfo($curl, CURLINFO_HTTP_CODE);
curl_close($curl);

if ($response === false) {
    error_log('Gemini assistant request failed: ' . $curlError);
    json_error('The assistant could not connect. Please try again.', 502);
}
if ($httpStatus < 200 || $httpStatus >= 300) {
    error_log('Gemini assistant returned HTTP ' . $httpStatus);
    json_error(
        $httpStatus === 429
            ? 'The assistant is busy. Please wait a moment and try again.'
            : 'The assistant could not reply right now. Please try again later.',
        502
    );
}

$result = json_decode($response, true);
$replyParts = $result['candidates'][0]['content']['parts'] ?? null;
$reply = '';
if (is_array($replyParts)) {
    foreach ($replyParts as $part) {
        if (is_array($part) && is_string($part['text'] ?? null)) {
            $reply .= $part['text'];
        }
    }
}
if (trim($reply) === '') {
    error_log('Gemini assistant returned an empty or invalid response.');
    json_error('The assistant returned an empty reply. Please try again.', 502);
}

json_ok(['reply' => trim($reply)]);
