<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_error('Method not allowed', 405);
$staff = require_staff($pdo);
$body = read_json_body();
$complaintId = (int) ($body['complaint_id'] ?? 0);
$priority = $body['priority'] ?? null;

if (!$complaintId) json_error('Missing complaint id.');
if (!is_string($priority) || !in_array($priority, ['low', 'normal', 'high', 'urgent'], true)) {
    json_error('Select a valid case priority.');
}

$scope = $staff['role'] === 'investigator'
    ? ' AND EXISTS (SELECT 1 FROM complaint_assignments a WHERE a.complaint_id = c.complaint_id AND a.investigator_id = :investigator_id)'
    : '';
$params = ['id' => $complaintId];
if ($staff['role'] === 'investigator') $params['investigator_id'] = $staff['user_id'];

$pdo->beginTransaction();
try {
    $complaintStmt = $pdo->prepare(
        "SELECT c.gcr_number, c.case_priority
         FROM complaints c
         WHERE c.complaint_id = :id AND c.sync_status = 'submitted'{$scope}
         FOR UPDATE"
    );
    $complaintStmt->execute($params);
    $complaint = $complaintStmt->fetch();
    if (!$complaint) {
        $pdo->rollBack();
        json_error('Complaint not found or not assigned to you.', 404);
    }

    if ($complaint['case_priority'] !== $priority) {
        $pdo->prepare("UPDATE complaints SET case_priority = :priority WHERE complaint_id = :id")
            ->execute(['priority' => $priority, 'id' => $complaintId]);
        $pdo->prepare(
            "INSERT INTO audit_log (staff_id, action, entity_type, entity_id, details)
             VALUES (:staff, 'CASE_PRIORITY_CHANGE', 'complaints', :id, :details)"
        )->execute([
            'staff' => $staff['user_id'],
            'id' => $complaintId,
            'details' => 'GCR ' . $complaint['gcr_number'] . '; priority changed from '
                . $complaint['case_priority'] . ' to ' . $priority,
        ]);
    }

    $pdo->commit();
    json_ok(['complaint_id' => $complaintId, 'priority' => $priority]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('Staff complaint priority update failed: ' . $e->getMessage());
    json_error('Could not save case priority. Please try again.', 500);
}
