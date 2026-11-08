<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';
require_once __DIR__ . '/../notifications/deliver.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_error('Method not allowed', 405);
$staff = require_staff($pdo, ['admin']);
$body = read_json_body();
$complaintId = (int) ($body['complaint_id'] ?? 0);
$investigatorId = isset($body['investigator_id']) && $body['investigator_id'] !== ''
    ? (int) $body['investigator_id'] : 0;
if (!$complaintId) json_error('Missing complaint id.');
if ($investigatorId) {
    $check = $pdo->prepare(
        "SELECT user_id FROM users
         WHERE user_id = :id AND role = 'investigator' AND account_status = 'active'"
    );
    $check->execute(['id' => $investigatorId]);
    if (!$check->fetchColumn()) json_error('Select an active investigator.');
}

$pdo->beginTransaction();
$notificationId = 0;
try {
    $complaint = $pdo->prepare(
        "SELECT user_id, gcr_number FROM complaints
         WHERE complaint_id = :id AND sync_status = 'submitted' FOR UPDATE"
    );
    $complaint->execute(['id' => $complaintId]);
    $row = $complaint->fetch();
    if (!$row) {
        $pdo->rollBack();
        json_error('Complaint not found.', 404);
    }
    $currentAssignment = $pdo->prepare(
        'SELECT investigator_id FROM complaint_assignments WHERE complaint_id = :id FOR UPDATE'
    );
    $currentAssignment->execute(['id' => $complaintId]);
    $currentInvestigatorId = $currentAssignment->fetchColumn();
    $assignmentChanged = $investigatorId
        ? ($currentInvestigatorId === false || (int) $currentInvestigatorId !== $investigatorId)
        : $currentInvestigatorId !== false;

    if ($investigatorId) {
        $pdo->prepare(
            "INSERT INTO complaint_assignments (complaint_id, investigator_id, assigned_by)
             VALUES (:complaint, :investigator, :admin)
             ON DUPLICATE KEY UPDATE investigator_id = VALUES(investigator_id),
                                     assigned_by = VALUES(assigned_by),
                                     assigned_at = CURRENT_TIMESTAMP"
        )->execute([
            'complaint' => $complaintId,
            'investigator' => $investigatorId,
            'admin' => $staff['user_id'],
        ]);
        $assignment = $pdo->prepare("SELECT full_name FROM users WHERE user_id = :id");
        $assignment->execute(['id' => $investigatorId]);
        $description = 'Assigned to ' . $assignment->fetchColumn();
        if ($assignmentChanged) {
            $typeStmt = $pdo->prepare("SELECT type_id FROM notification_types WHERE code = 'ASSIGNED'");
            $typeStmt->execute();
            $typeId = $typeStmt->fetchColumn();
            if (!$typeId) throw new RuntimeException('Required assignment notification type is not configured.');
            $pdo->prepare(
                "INSERT INTO notifications (user_id, complaint_id, type_id, title_en, title_st, body_en, body_st)
                 VALUES (:user, :complaint, :type, 'Complaint assigned', 'Tletlebo e abetsoe',
                         'Your complaint has been assigned for investigation.', 'Tletlebo ea hao e abetsoe bakeng sa lipatlisiso.')"
            )->execute([
                'user' => $row['user_id'],
                'complaint' => $complaintId,
                'type' => $typeId,
            ]);
            $notificationId = (int) $pdo->lastInsertId();
        }
    } else {
        $pdo->prepare("DELETE FROM complaint_assignments WHERE complaint_id = :id")
            ->execute(['id' => $complaintId]);
        $description = 'Investigator assignment removed';
    }

    $pdo->prepare(
        "INSERT INTO audit_log (staff_id, action, entity_type, entity_id, details)
         VALUES (:staff, 'CASE_ASSIGNMENT', 'complaints', :id, :details)"
    )->execute([
        'staff' => $staff['user_id'],
        'id' => $complaintId,
        'details' => 'GCR ' . $row['gcr_number'] . '; ' . $description,
    ]);
    $pdo->commit();
    if ($notificationId > 0) deliver_complaint_update($pdo, $notificationId);
    json_ok(['complaint_id' => $complaintId, 'investigator_id' => $investigatorId ?: null]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('Staff complaint assignment failed: ' . $e->getMessage());
    json_error('Could not assign complaint. Please try again.', 500);
}
