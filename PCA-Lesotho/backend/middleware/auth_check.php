<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';

// Validates the Bearer token against user_sessions.token_hash and returns
// the authenticated user_id, or sends a 401 JSON error and exits.
function require_auth(PDO $pdo): int {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';

    if (!preg_match('/Bearer\s+(\S+)/i', $authHeader, $m)) {
        json_error('Missing or invalid Authorization header', 401);
    }

    $rawToken = $m[1];
    $tokenHash = hash('sha256', $rawToken);

    $stmt = $pdo->prepare(
        "SELECT s.user_id FROM user_sessions s
         JOIN users u ON u.user_id = s.user_id
         WHERE s.token_hash = :hash
           AND s.revoked_at IS NULL
           AND s.expires_at > NOW()
           AND u.account_status = 'active'"
    );
    $stmt->execute(['hash' => $tokenHash]);
    $row = $stmt->fetch();

    if (!$row) {
        json_error('Session expired or invalid. Please sign in again.', 401);
    }

    return (int) $row['user_id'];
}

function require_staff(PDO $pdo, array $allowedRoles = ['admin', 'investigator']): array {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';
    if (!preg_match('/Bearer\s+(\S+)/i', $authHeader, $m)) {
        json_error('Missing or invalid Authorization header', 401);
    }

    $tokenHash = hash('sha256', $m[1]);
    $stmt = $pdo->prepare(
        "SELECT u.user_id, u.role, u.full_name
         FROM user_sessions s
         JOIN users u ON u.user_id = s.user_id
         WHERE s.token_hash = :hash AND s.revoked_at IS NULL
           AND s.expires_at > NOW() AND u.account_status = 'active'"
    );
    $stmt->execute(['hash' => $tokenHash]);
    $staff = $stmt->fetch();
    if (!$staff) {
        json_error('Session expired or invalid. Please sign in again.', 401);
    }
    if (!in_array($staff['role'], $allowedRoles, true)) {
        json_error('You are not authorized to access staff case management.', 403);
    }
    $staff['user_id'] = (int) $staff['user_id'];
    return $staff;
}
