<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';

// Public — guests can see categories while starting a draft.
$stmt = $pdo->query(
    "SELECT category_id, code, name_en, name_st, description_en, description_st
     FROM complaint_categories WHERE is_active = 1 ORDER BY display_order"
);
json_ok($stmt->fetchAll());
