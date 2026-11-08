<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$userId = require_auth($pdo);
$complaintId = (int) ($_GET['id'] ?? 0);
if (!$complaintId) json_error('Missing complaint id.');

$stmt = $pdo->prepare(
    "SELECT c.*, COALESCE(c.complaint_type, cat.name_en) AS category_name_en,
            COALESCE(c.complaint_type, cat.name_st) AS category_name_st,
            st.code AS status_code,
            CASE WHEN st.code = 'SUBMITTED' THEN 'Pending' ELSE st.label_en END AS status_label_en,
            CASE WHEN st.code = 'SUBMITTED' THEN 'E emetse' ELSE st.label_st END AS status_label_st
     FROM complaints c
     LEFT JOIN complaint_categories cat ON cat.category_id = c.category_id
     JOIN complaint_status_types st ON st.status_id = c.current_status_id
     WHERE c.complaint_id = :id AND c.user_id = :uid"
);
$stmt->execute(['id' => $complaintId, 'uid' => $userId]);
$complaint = $stmt->fetch();
if (!$complaint) json_error('Complaint not found.', 404);

$timeline = $pdo->prepare(
    "SELECT h.changed_at, st.code,
            CASE WHEN st.code = 'SUBMITTED' THEN 'Pending' ELSE st.label_en END AS label_en,
            CASE WHEN st.code = 'SUBMITTED' THEN 'E emetse' ELSE st.label_st END AS label_st,
            h.message_en, h.message_st
     FROM complaint_status_history h
     JOIN complaint_status_types st ON st.status_id = h.status_id
     WHERE h.complaint_id = :id ORDER BY h.changed_at ASC"
);
$timeline->execute(['id' => $complaintId]);

$evidence = $pdo->prepare(
    "SELECT evidence_id, file_name, file_type, upload_status FROM evidence_files WHERE complaint_id = :id"
);
$evidence->execute(['id' => $complaintId]);

json_ok([
    'complaint' => $complaint,
    'timeline' => $timeline->fetchAll(),
    'evidence' => $evidence->fetchAll(),
]);
