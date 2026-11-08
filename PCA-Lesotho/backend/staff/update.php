<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';
require_once __DIR__ . '/../notifications/deliver.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') json_error('Method not allowed', 405);
$staff = require_staff($pdo);
$body = read_json_body();
$complaintId = (int) ($body['complaint_id'] ?? 0);
$messageEn = trim($body['message_en'] ?? '');
$messageSt = trim($body['message_st'] ?? '');
$statusCode = trim($body['status_code'] ?? '');
if (!$complaintId || ($statusCode === '' && $messageEn === '')) {
    json_error('Complaint id and a status or progress message are required.');
}
if (strlen($messageEn) > 255 || strlen($messageSt) > 255) json_error('Progress messages must be 255 characters or fewer.');
if (array_key_exists('expected_completion_date', $body)) {
    $completionDate = trim((string) ($body['expected_completion_date'] ?? ''));
    if ($completionDate !== '' && (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $completionDate)
            || !checkdate((int) substr($completionDate, 5, 2), (int) substr($completionDate, 8, 2), (int) substr($completionDate, 0, 4)))) {
        json_error('Expected completion date must be a valid YYYY-MM-DD date.');
    }
} else {
    $completionDate = null;
}
if ($messageEn === '' && $statusCode !== '') {
    json_error('Add a message explaining this update to the complainant.');
}

$pdo->beginTransaction();
try {
    $scope = $staff['role'] === 'investigator'
        ? " AND EXISTS (SELECT 1 FROM complaint_assignments a WHERE a.complaint_id = c.complaint_id AND a.investigator_id = :investigator_id)"
        : '';
    $params = ['id' => $complaintId];
    if ($staff['role'] === 'investigator') $params['investigator_id'] = $staff['user_id'];
    $complaintStmt = $pdo->prepare(
        "SELECT c.user_id, c.gcr_number, c.current_status_id, st.code AS current_status,
                st.status_id
         FROM complaints c
         JOIN complaint_status_types st ON st.status_id = c.current_status_id
         WHERE c.complaint_id = :id AND c.sync_status = 'submitted'{$scope}
         FOR UPDATE"
    );
    $complaintStmt->execute($params);
    $complaint = $complaintStmt->fetch();
    if (!$complaint) {
        $pdo->rollBack();
        json_error('Complaint not found or not assigned to you.', 404);
    }

    $newStatusId = (int) $complaint['current_status_id'];
    $newStatus = $complaint['current_status'];
    $didStatusChange = false;
    if ($statusCode !== '') {
        $statusStmt = $pdo->prepare("SELECT status_id, code, label_en FROM complaint_status_types WHERE code = :code");
        $statusStmt->execute(['code' => $statusCode]);
        $status = $statusStmt->fetch();
        if (!$status) {
            $pdo->rollBack();
            json_error('Unknown complaint status.');
        }
        $newStatusId = (int) $status['status_id'];
        $newStatus = $status['code'];
        if ($newStatusId !== (int) $complaint['current_status_id']) {
            $didStatusChange = true;
            $pdo->prepare("UPDATE complaints SET current_status_id = :status WHERE complaint_id = :id")
                ->execute(['status' => $newStatusId, 'id' => $complaintId]);
        }
    }

    if (array_key_exists('expected_completion_date', $body)) {
        $pdo->prepare("UPDATE complaints SET expected_completion_date = :completion_date WHERE complaint_id = :id")
            ->execute(['completion_date' => $completionDate !== '' ? $completionDate : null, 'id' => $complaintId]);
    }

    $pdo->prepare(
        "INSERT INTO complaint_status_history
            (complaint_id, status_id, message_en, message_st, changed_by_staff_id)
         VALUES (:complaint, :status, :message_en, :message_st, :staff)"
    )->execute([
        'complaint' => $complaintId,
        'status' => $newStatusId,
        'message_en' => $messageEn,
        'message_st' => $messageSt !== '' ? $messageSt : null,
        'staff' => $staff['user_id'],
    ]);

    $typeCode = $didStatusChange && $newStatus === 'CLOSED' ? 'COMPLAINT_CLOSED'
        : ($didStatusChange ? 'STATUS_CHANGED' : 'PROGRESS_UPDATE');
    $typeStmt = $pdo->prepare("SELECT type_id FROM notification_types WHERE code = :code");
    $typeStmt->execute(['code' => $typeCode]);
    $typeId = $typeStmt->fetchColumn();
    if (!$typeId) {
        throw new RuntimeException('Required notification type is not configured.');
    }
    $notificationEn = $messageEn;
    $notificationSt = $messageSt !== '' ? $messageSt : $messageEn;
    $pdo->prepare(
        "INSERT INTO notifications (user_id, complaint_id, type_id, title_en, title_st, body_en, body_st)
         VALUES (:user, :complaint, :type, 'Complaint update', 'Ntlafatso ea tletlebo', :body_en, :body_st)"
    )->execute([
        'user' => $complaint['user_id'],
        'complaint' => $complaintId,
        'type' => $typeId,
        'body_en' => $notificationEn,
        'body_st' => $notificationSt,
    ]);
    $notificationId = (int) $pdo->lastInsertId();
    $pdo->prepare(
        "INSERT INTO audit_log (staff_id, action, entity_type, entity_id, details)
         VALUES (:staff, :action, 'complaints', :id, :details)"
    )->execute([
        'staff' => $staff['user_id'],
        'action' => $didStatusChange ? 'STATUS_CHANGE' : 'PROGRESS_UPDATE',
        'id' => $complaintId,
        'details' => 'GCR ' . $complaint['gcr_number'] . '; status ' . $newStatus,
    ]);

    $pdo->commit();
    deliver_complaint_update($pdo, $notificationId);
    json_ok(['complaint_id' => $complaintId, 'status_code' => $newStatus]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    error_log('Staff complaint update failed: ' . $e->getMessage());
    json_error('Could not save complaint update. Please try again.', 500);
}
