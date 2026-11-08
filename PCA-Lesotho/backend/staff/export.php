<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$staff = require_staff($pdo);
$where = ["c.sync_status = 'submitted'"];
$params = [];
if ($staff['role'] === 'investigator') {
    $where[] = 'a.investigator_id = :investigator_id';
    $params['investigator_id'] = $staff['user_id'];
}
if (!empty($_GET['status'])) {
    $where[] = 'st.code = :status';
    $params['status'] = trim($_GET['status']);
}
if (!empty($_GET['category'])) {
    $where[] = 'c.category_id = :category';
    $params['category'] = (int) $_GET['category'];
}
if (!empty($_GET['priority'])) {
    $priority = trim($_GET['priority']);
    if (!in_array($priority, ['low', 'normal', 'high', 'urgent'], true)) {
        http_response_code(400);
        exit('Invalid case priority.');
    }
    $where[] = 'c.case_priority = :case_priority';
    $params['case_priority'] = $priority;
}
if (!empty($_GET['due_window'])) {
    $dueWindow = trim($_GET['due_window']);
    $activeCase = "st.code NOT IN ('COMPLETED', 'CLOSED')";
    if ($dueWindow === 'overdue') {
        $where[] = "c.expected_completion_date < CURDATE() AND {$activeCase}";
    } elseif ($dueWindow === 'due_soon') {
        $where[] = "c.expected_completion_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY) AND {$activeCase}";
    } else {
        http_response_code(400);
        exit('Invalid due-date queue.');
    }
}
if (!empty($_GET['from'])) {
    $date = DateTime::createFromFormat('!Y-m-d', $_GET['from']);
    if (!$date || $date->format('Y-m-d') !== $_GET['from']) {
        http_response_code(400);
        exit('Invalid start date.');
    }
    $where[] = 'c.submitted_at >= :date_from';
    $params['date_from'] = $date->format('Y-m-d 00:00:00');
}
if (!empty($_GET['to'])) {
    $date = DateTime::createFromFormat('!Y-m-d', $_GET['to']);
    if (!$date || $date->format('Y-m-d') !== $_GET['to']) {
        http_response_code(400);
        exit('Invalid end date.');
    }
    $where[] = 'c.submitted_at < :date_to';
    $params['date_to'] = $date->modify('+1 day')->format('Y-m-d 00:00:00');
}
if (!empty($_GET['q'])) {
    $needle = '%' . trim($_GET['q']) . '%';
    $where[] = '(c.gcr_number LIKE :gcr OR u.full_name LIKE :name OR u.phone_number LIKE :phone)';
    $params['gcr'] = $needle;
    $params['name'] = $needle;
    $params['phone'] = $needle;
}
$stmt = $pdo->prepare(
    "SELECT c.gcr_number, COALESCE(NULLIF(c.complainant_name, ''), u.full_name),
            COALESCE(NULLIF(c.complainant_phone, ''), u.phone_number), u.email,
            cat.name_en AS category,
            CASE WHEN st.code = 'SUBMITTED' THEN 'Pending' ELSE st.label_en END AS status,
            c.case_priority, c.expected_completion_date, c.incident_date,
            c.submitted_at, investigator.full_name AS investigator
     FROM complaints c
     JOIN users u ON u.user_id = c.user_id
     JOIN complaint_status_types st ON st.status_id = c.current_status_id
     LEFT JOIN complaint_categories cat ON cat.category_id = c.category_id
     LEFT JOIN complaint_assignments a ON a.complaint_id = c.complaint_id
     LEFT JOIN users investigator ON investigator.user_id = a.investigator_id
     WHERE " . implode(' AND ', $where) . "
     ORDER BY " . (!empty($_GET['due_window'])
        ? "c.expected_completion_date ASC, FIELD(c.case_priority, 'urgent', 'high', 'normal', 'low'), c.submitted_at DESC"
        : "c.submitted_at DESC")
);
$stmt->execute($params);
header('Content-Type: text/csv; charset=utf-8');
header('Content-Disposition: attachment; filename="pca-complaints-' . date('Y-m-d') . '.csv"');
$out = fopen('php://output', 'w');
fputcsv($out, ['GCR number', 'Complainant', 'Phone', 'Email', 'Category', 'Status', 'Priority', 'Expected completion date', 'Incident date', 'Submitted at', 'Investigator']);
while ($row = $stmt->fetch(PDO::FETCH_NUM)) {
    foreach ($row as &$cell) {
        if (is_string($cell) && preg_match('/^[=+\-@]/', $cell)) $cell = "'" . $cell;
    }
    unset($cell);
    fputcsv($out, $row);
}
fclose($out);
