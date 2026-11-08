<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$userId = require_auth($pdo);
$filter = $_GET['status'] ?? 'all'; // all | active | resolved

$sql = "SELECT c.complaint_id, c.gcr_number, c.incident_date, c.created_at, c.updated_at,
               COALESCE(NULLIF(c.complainant_name, ''), u.full_name) AS complainant_name,
               COALESCE(c.complaint_type, cat.name_en) AS category_name_en,
               COALESCE(c.complaint_type, cat.name_st) AS category_name_st,
               st.code AS status_code,
               CASE WHEN st.code = 'SUBMITTED' THEN 'Pending' ELSE st.label_en END AS status_label_en,
               CASE WHEN st.code = 'SUBMITTED' THEN 'E emetse' ELSE st.label_st END AS status_label_st,
               st.color_code, c.expected_completion_date,
               (SELECT h.message_en FROM complaint_status_history h
                WHERE h.complaint_id = c.complaint_id AND h.message_en <> ''
                ORDER BY h.changed_at DESC LIMIT 1) AS latest_update_en,
               (SELECT h.message_st FROM complaint_status_history h
                WHERE h.complaint_id = c.complaint_id AND h.message_st <> ''
                ORDER BY h.changed_at DESC LIMIT 1) AS latest_update_st
        FROM complaints c
        LEFT JOIN complaint_categories cat ON cat.category_id = c.category_id
        JOIN complaint_status_types st ON st.status_id = c.current_status_id
        JOIN users u ON u.user_id = c.user_id
        WHERE c.user_id = :uid AND c.sync_status = 'submitted'";

if ($filter === 'active') {
    $sql .= " AND st.code NOT IN ('COMPLETED', 'CLOSED')";
} elseif ($filter === 'resolved') {
    $sql .= " AND st.code IN ('COMPLETED', 'CLOSED')";
}
$sql .= " ORDER BY c.updated_at DESC";

$stmt = $pdo->prepare($sql);
$stmt->execute(['uid' => $userId]);
json_ok($stmt->fetchAll());
