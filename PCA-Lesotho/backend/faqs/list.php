<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';

$stmt = $pdo->query("SELECT faq_id, question_en, question_st, answer_en, answer_st FROM faqs ORDER BY display_order");
json_ok($stmt->fetchAll());
