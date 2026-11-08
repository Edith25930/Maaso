<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';

$stmt = $pdo->query("SELECT district_id, name_en, name_st FROM districts ORDER BY name_en");
json_ok($stmt->fetchAll());
