<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';

// Public — powers the "About PCA" screen (§3.18). Content is real, official
// text transcribed from the PCA public information pamphlet — see
// database/pca_mobile_schema.sql, section 9, for provenance notes.
$stmt = $pdo->query(
    "SELECT section_key, title_en, title_st, body_en, body_st
     FROM pca_info_sections ORDER BY display_order"
);
json_ok($stmt->fetchAll());
