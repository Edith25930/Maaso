<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$staff = require_staff($pdo);
$id = (int) ($_GET['id'] ?? 0);
if (!$id) json_error('Missing complaint id.');

$scope = $staff['role'] === 'investigator' ? ' AND a.investigator_id = :investigator_id' : '';
$params = ['id' => $id];
if ($staff['role'] === 'investigator') $params['investigator_id'] = $staff['user_id'];
$stmt = $pdo->prepare(
    "SELECT c.*, COALESCE(NULLIF(c.complainant_name, ''), u.full_name) AS complainant_display_name,
            COALESCE(NULLIF(c.complainant_phone, ''), u.phone_number) AS complainant_display_phone,
            u.email AS complainant_email, COALESCE(c.complaint_type, cat.name_en) AS category_name,
            d.name_en AS district_name, cd.name_en AS complainant_district,
            st.code AS status_code,
            CASE WHEN st.code = 'SUBMITTED' THEN 'Pending' ELSE st.label_en END AS status_label,
            a.investigator_id, investigator.full_name AS investigator_name,
            oi.officer_unknown, oi.officer_name, oi.officer_rank, oi.badge_number,
            oi.station_name AS officer_station_name
     FROM complaints c
     JOIN users u ON u.user_id = c.user_id
     JOIN complaint_status_types st ON st.status_id = c.current_status_id
     LEFT JOIN complaint_categories cat ON cat.category_id = c.category_id
     LEFT JOIN districts d ON d.district_id = c.district_id
     LEFT JOIN districts cd ON cd.district_id = c.complainant_district_id
     LEFT JOIN complaint_assignments a ON a.complaint_id = c.complaint_id
     LEFT JOIN users investigator ON investigator.user_id = a.investigator_id
     LEFT JOIN complaint_officer_info oi ON oi.complaint_id = c.complaint_id
     WHERE c.complaint_id = :id AND c.sync_status = 'submitted'{$scope}"
);
$stmt->execute($params);
$complaint = $stmt->fetch();
if (!$complaint) json_error('Complaint not found.', 404);

$witnesses = $pdo->prepare("SELECT full_name, contact_number FROM witnesses WHERE complaint_id = :id");
$witnesses->execute(['id' => $id]);
$evidence = $pdo->prepare(
    "SELECT evidence_id, file_name, file_type, file_size_bytes, upload_status
     FROM evidence_files WHERE complaint_id = :id ORDER BY evidence_id"
);
$evidence->execute(['id' => $id]);
$timeline = $pdo->prepare(
    "SELECT h.changed_at, st.code AS status_code,
            CASE WHEN st.code = 'SUBMITTED' THEN 'Pending' ELSE st.label_en END AS status_label,
            h.message_en, h.message_st, u.full_name AS updated_by
     FROM complaint_status_history h
     JOIN complaint_status_types st ON st.status_id = h.status_id
     LEFT JOIN users u ON u.user_id = h.changed_by_staff_id
     WHERE h.complaint_id = :id ORDER BY h.changed_at"
);
$timeline->execute(['id' => $id]);

json_ok([
    'complaint' => $complaint,
    'witnesses' => $witnesses->fetchAll(),
    'evidence' => $evidence->fetchAll(),
    'timeline' => $timeline->fetchAll(),
]);
