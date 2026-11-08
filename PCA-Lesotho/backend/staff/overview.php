<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';

$staff = require_staff($pdo);
$scope = '';
$params = [];
if ($staff['role'] === 'investigator') {
    $scope = ' AND a.investigator_id = :investigator_id';
    $params['investigator_id'] = $staff['user_id'];
}

$totals = $pdo->prepare(
    "SELECT COUNT(*) AS total,
            SUM(st.code NOT IN ('COMPLETED', 'CLOSED')) AS active,
            SUM(st.code IN ('COMPLETED', 'CLOSED')) AS closed,
            SUM(c.expected_completion_date < CURDATE()
                AND st.code NOT IN ('COMPLETED', 'CLOSED')) AS overdue,
            SUM(c.expected_completion_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)
                AND st.code NOT IN ('COMPLETED', 'CLOSED')) AS due_soon
     FROM complaints c
     JOIN complaint_status_types st ON st.status_id = c.current_status_id
     LEFT JOIN complaint_assignments a ON a.complaint_id = c.complaint_id
     WHERE c.sync_status = 'submitted'{$scope}"
);
$totals->execute($params);
$summary = $totals->fetch();

$status = $pdo->prepare(
    "SELECT CASE WHEN st.code = 'SUBMITTED' THEN 'Pending' ELSE st.label_en END AS label,
            COUNT(*) AS count
     FROM complaints c
     LEFT JOIN complaint_assignments a ON a.complaint_id = c.complaint_id
     JOIN complaint_status_types st ON st.status_id = c.current_status_id
     WHERE c.sync_status = 'submitted'{$scope}
     GROUP BY st.status_id ORDER BY st.sort_order"
);
$status->execute($params);
$categories = $pdo->prepare(
    "SELECT COALESCE(cat.name_en, 'Unspecified') AS label, COUNT(*) AS count
     FROM complaints c
     LEFT JOIN complaint_assignments a ON a.complaint_id = c.complaint_id
     LEFT JOIN complaint_categories cat ON cat.category_id = c.category_id
     WHERE c.sync_status = 'submitted'{$scope}
     GROUP BY cat.category_id, cat.name_en ORDER BY count DESC"
);
$categories->execute($params);

$investigators = [];
if ($staff['role'] === 'admin') {
    $investigators = $pdo->query(
        "SELECT user_id, full_name FROM users
         WHERE role = 'investigator' AND account_status = 'active'
         ORDER BY full_name"
    )->fetchAll();
}
$statusTypes = $pdo->query(
    "SELECT code, label_en FROM complaint_status_types ORDER BY sort_order"
)->fetchAll();
$categoriesList = $pdo->query(
    "SELECT category_id, name_en FROM complaint_categories WHERE is_active = 1 ORDER BY display_order"
)->fetchAll();

json_ok([
    'summary' => $summary,
    'by_status' => $status->fetchAll(),
    'by_category' => $categories->fetchAll(),
    'investigators' => $investigators,
    'statuses' => $statusTypes,
    'categories' => $categoriesList,
    'role' => $staff['role'],
]);
