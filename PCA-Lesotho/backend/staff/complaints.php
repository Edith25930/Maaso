<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$staff = require_staff($pdo);
$page = max(1, (int) ($_GET['page'] ?? 1));
$perPage = min(100, max(10, (int) ($_GET['per_page'] ?? 20)));
$where = ["c.sync_status = 'submitted'"];
$params = [];

if ($staff['role'] === 'investigator') {
    $where[] = 'a.investigator_id = :assigned_investigator';
    $params['assigned_investigator'] = $staff['user_id'];
}
if (!empty($_GET['status'])) {
    $where[] = 'st.code = :status_code';
    $params['status_code'] = trim($_GET['status']);
}
if (!empty($_GET['category'])) {
    $where[] = 'c.category_id = :category_id';
    $params['category_id'] = (int) $_GET['category'];
}
if (!empty($_GET['priority'])) {
    $priority = trim($_GET['priority']);
    if (!in_array($priority, ['low', 'normal', 'high', 'urgent'], true)) {
        json_error('Invalid case priority.');
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
        json_error('Invalid due-date queue.');
    }
}
if (!empty($_GET['from'])) {
    $date = DateTime::createFromFormat('!Y-m-d', $_GET['from']);
    if (!$date || $date->format('Y-m-d') !== $_GET['from']) json_error('Invalid start date.');
    $where[] = 'c.submitted_at >= :date_from';
    $params['date_from'] = $date->format('Y-m-d 00:00:00');
}
if (!empty($_GET['to'])) {
    $date = DateTime::createFromFormat('!Y-m-d', $_GET['to']);
    if (!$date || $date->format('Y-m-d') !== $_GET['to']) json_error('Invalid end date.');
    $where[] = 'c.submitted_at < :date_to';
    $params['date_to'] = $date->modify('+1 day')->format('Y-m-d 00:00:00');
}
if (!empty($_GET['q'])) {
    $needle = '%' . trim($_GET['q']) . '%';
    $where[] = '(c.gcr_number LIKE :search_gcr OR u.full_name LIKE :search_name OR u.phone_number LIKE :search_phone)';
    $params['search_gcr'] = $needle;
    $params['search_name'] = $needle;
    $params['search_phone'] = $needle;
}

$whereSql = implode(' AND ', $where);
$count = $pdo->prepare(
    "SELECT COUNT(*) FROM complaints c
     JOIN users u ON u.user_id = c.user_id
     JOIN complaint_status_types st ON st.status_id = c.current_status_id
     LEFT JOIN complaint_assignments a ON a.complaint_id = c.complaint_id
     WHERE {$whereSql}"
);
$count->execute($params);
$total = (int) $count->fetchColumn();

$stmt = $pdo->prepare(
    "SELECT c.complaint_id, c.gcr_number, c.incident_date, c.submitted_at,
            c.updated_at, cat.name_en AS category_name, st.code AS status_code,
            c.case_priority, c.expected_completion_date,
            CASE
                WHEN c.expected_completion_date < CURDATE()
                     AND st.code NOT IN ('COMPLETED', 'CLOSED') THEN 'Overdue'
                WHEN c.expected_completion_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)
                     AND st.code NOT IN ('COMPLETED', 'CLOSED') THEN 'Due soon'
                ELSE ''
            END AS due_label,
            CASE WHEN st.code = 'SUBMITTED' THEN 'Pending' ELSE st.label_en END AS status_label,
            COALESCE(NULLIF(c.complainant_name, ''), u.full_name) AS complainant_name,
            a.investigator_id, investigator.full_name AS investigator_name
     FROM complaints c
     JOIN users u ON u.user_id = c.user_id
     JOIN complaint_status_types st ON st.status_id = c.current_status_id
     LEFT JOIN complaint_categories cat ON cat.category_id = c.category_id
     LEFT JOIN complaint_assignments a ON a.complaint_id = c.complaint_id
     LEFT JOIN users investigator ON investigator.user_id = a.investigator_id
     WHERE {$whereSql}
     ORDER BY " . (!empty($_GET['due_window'])
        ? "c.expected_completion_date ASC, FIELD(c.case_priority, 'urgent', 'high', 'normal', 'low'), c.submitted_at DESC"
        : "c.submitted_at DESC, c.complaint_id DESC") . "
     LIMIT :limit OFFSET :offset"
);
foreach ($params as $key => $value) $stmt->bindValue(':' . $key, $value);
$stmt->bindValue(':limit', $perPage, PDO::PARAM_INT);
$stmt->bindValue(':offset', ($page - 1) * $perPage, PDO::PARAM_INT);
$stmt->execute();

json_ok([
    'items' => $stmt->fetchAll(),
    'total' => $total,
    'page' => $page,
    'per_page' => $perPage,
    'pages' => (int) ceil($total / $perPage),
]);
