<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$staff = require_staff($pdo);
$evidenceId = (int) ($_GET['id'] ?? 0);
if (!$evidenceId) {
    http_response_code(400);
    exit('Missing evidence id.');
}
$scope = $staff['role'] === 'investigator' ? ' AND a.investigator_id = :investigator_id' : '';
$params = ['evidence' => $evidenceId];
if ($staff['role'] === 'investigator') $params['investigator_id'] = $staff['user_id'];
$stmt = $pdo->prepare(
    "SELECT e.file_name, e.storage_path
     FROM evidence_files e
     JOIN complaints c ON c.complaint_id = e.complaint_id
     LEFT JOIN complaint_assignments a ON a.complaint_id = c.complaint_id
     WHERE e.evidence_id = :evidence AND c.sync_status = 'submitted'{$scope}"
);
$stmt->execute($params);
$file = $stmt->fetch();
if (!$file) {
    http_response_code(404);
    exit('Evidence not found.');
}
$storageDir = getenv('PCA_EVIDENCE_DIR') ?: __DIR__ . '/../storage/evidence';
$base = realpath($storageDir);
$path = $base ? realpath($base . DIRECTORY_SEPARATOR . $file['storage_path']) : false;
if (!$base || !$path || !str_starts_with($path, $base . DIRECTORY_SEPARATOR) || !is_file($path)) {
    http_response_code(404);
    exit('Evidence file is unavailable.');
}
$mime = (new finfo(FILEINFO_MIME_TYPE))->file($path) ?: 'application/octet-stream';
$name = preg_replace('/[^A-Za-z0-9._-]/', '_', basename($file['file_name']));
header('Content-Type: ' . $mime);
header('Content-Length: ' . filesize($path));
header('Content-Disposition: attachment; filename="' . $name . '"');
header('X-Content-Type-Options: nosniff');
readfile($path);
