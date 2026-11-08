<?php
// Database connection (PDO, prepared statements throughout the API).
// Real credentials belong in environment variables, never in source control.

$localConfigPath = __DIR__ . '/database.local.php';
$localConfig = is_file($localConfigPath) ? require $localConfigPath : [];
if (!is_array($localConfig)) {
    throw new RuntimeException('The local database configuration must return an array.');
}

$DB_HOST = getenv('PCA_DB_HOST') ?: ($localConfig['host'] ?? '127.0.0.1');
$DB_NAME = getenv('PCA_DB_NAME') ?: ($localConfig['name'] ?? ($localConfig['dbname'] ?? 'pca_lesotho'));
$DB_USER = getenv('PCA_DB_USER') ?: ($localConfig['user'] ?? 'root');
$DB_PASS = getenv('PCA_DB_PASS') ?: (isset($localConfig['password']) ? $localConfig['password'] : ($localConfig['pass'] ?? ''));

try {
    $pdo = new PDO(
        "mysql:host={$DB_HOST};dbname={$DB_NAME};charset=utf8mb4",
        $DB_USER,
        $DB_PASS,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]
    );
} catch (PDOException $e) {
    http_response_code(500);
    header('Content-Type: application/json');
    echo json_encode(['error' => 'Database connection failed']);
    exit;
}
