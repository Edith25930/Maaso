<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';

// Public — guests and offline-cache priming both use this.
$stmt = $pdo->query(
    "SELECT a.article_id, a.category_id, cat.name_en AS category_name_en, cat.name_st AS category_name_st,
            a.title_en, a.title_st, a.definition_en, a.definition_st,
            a.examples_en, a.examples_st, a.when_to_report_en, a.when_to_report_st,
            a.useful_info_en, a.useful_info_st
     FROM educational_articles a
     JOIN complaint_categories cat ON cat.category_id = a.category_id
     WHERE a.is_published = 1
     ORDER BY cat.display_order"
);
json_ok($stmt->fetchAll());
