<?php
require_once __DIR__ . '/../config/db.php';
require_once __DIR__ . '/../config/response.php';
require_once __DIR__ . '/../middleware/auth_check.php';
require_once __DIR__ . '/../notifications/deliver.php';
require_once __DIR__ . '/../config/sms.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_error('Method not allowed', 405);
}

$userId = require_auth($pdo);
$required = [
    'name', 'gender', 'contact', 'id_number', 'district', 'region', 'type',
    'incident_date', 'location', 'current_location', 'description',
    'next_of_kin_name', 'next_of_kin_address', 'oath',
];
foreach ($required as $field) {
    if (!isset($_POST[$field]) || trim((string) $_POST[$field]) === '') {
        json_error(ucwords(str_replace('_', ' ', $field)) . ' is required.');
    }
}
if (!in_array($_POST['gender'], ['male', 'female'], true)) {
    json_error('Select a valid gender.');
}
if (!preg_match('/^\+[1-9]\d{6,14}$/', (string) $_POST['contact'])) {
    json_error('Please enter a valid international phone number for contact info.');
}
if (!empty($_POST['witness_contacts'])
    && !preg_match('/^\+[1-9]\d{6,14}$/', (string) $_POST['witness_contacts'])) {
    json_error('Please enter a valid international phone number for witness contacts.');
}
if (strlen((string) $_POST['id_number']) > 100) {
    json_error('ID number must not exceed 100 characters.');
}
if ((string) $_POST['oath'] !== '1') {
    json_error('The oath must be accepted before submission.');
}

$incidentDateTime = DateTime::createFromFormat('!Y-m-d H:i', trim((string) $_POST['incident_date']));
if (!$incidentDateTime || $incidentDateTime->format('Y-m-d H:i') !== trim((string) $_POST['incident_date'])) {
    json_error('Select a valid incident date and time.');
}

$district = trim((string) $_POST['district']);
$districtRows = $pdo->query('SELECT district_id, name_en FROM districts')->fetchAll(PDO::FETCH_ASSOC);
$districtId = null;
$normalize = static function (string $value): string {
    return strtolower(preg_replace('/[^a-z0-9]/i', '', $value));
};
foreach ($districtRows as $districtRow) {
    if ($normalize($districtRow['name_en']) === $normalize($district)) {
        $districtId = (int) $districtRow['district_id'];
        break;
    }
}
$districtRegionLetters = [
    'mokhotlong' => 'N', 'leribe' => 'N', 'bothabothe' => 'N',
    'berea' => 'C', 'maseru' => 'C', 'thabatseka' => 'C',
    'mafeteng' => 'S', 'mohaleshoek' => 'S', 'qachasnek' => 'S', 'quthing' => 'S',
];
$regionLetter = $districtRegionLetters[$normalize($district)] ?? 'X';
$regionDistrict = $regionLetter . ' - ' . $district;

$maxFileBytes = 200 * 1024 * 1024;
$allowedMimeTypes = [
    'image/jpeg' => 'photo', 'image/png' => 'photo', 'image/webp' => 'photo',
    'image/gif' => 'photo', 'image/heic' => 'photo', 'image/heif' => 'photo',
    'image/bmp' => 'photo', 'image/tiff' => 'photo', 'image/avif' => 'photo',
    'video/mp4' => 'video', 'video/3gpp' => 'video', 'video/quicktime' => 'video',
    'video/webm' => 'video', 'video/x-matroska' => 'video',
    'video/x-msvideo' => 'video', 'video/x-ms-wmv' => 'video', 'video/x-flv' => 'video',
    'application/pdf' => 'document', 'text/plain' => 'document',
    'application/msword' => 'document',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document' => 'document',
    'application/rtf' => 'document', 'application/zip' => 'document',
    'application/x-zip-compressed' => 'document',
    'audio/mpeg' => 'audio', 'audio/mp4' => 'audio', 'audio/ogg' => 'audio',
    'audio/wav' => 'audio', 'audio/x-wav' => 'audio', 'audio/3gpp' => 'audio',
    'audio/aac' => 'audio', 'audio/flac' => 'audio', 'audio/x-m4a' => 'audio',
    'audio/amr' => 'audio', 'audio/x-ms-wma' => 'audio',
];
$uploadedFiles = [];
$addUpload = static function (array $upload, string $formKey, bool $imageOnly = false) use (
    &$uploadedFiles, $maxFileBytes, $allowedMimeTypes
): void {
    $name = basename((string) ($upload['name'] ?? $formKey));
    $error = (int) ($upload['error'] ?? UPLOAD_ERR_NO_FILE);
    if ($error === UPLOAD_ERR_NO_FILE && $formKey === 'evidence') return;
    if ($error !== UPLOAD_ERR_OK) {
        if ($error === UPLOAD_ERR_INI_SIZE || $error === UPLOAD_ERR_FORM_SIZE) {
            json_error($name . ' exceeds the server upload limit.');
        }
        json_error($name . ' could not be uploaded. Please try again.');
    }
    $size = (int) ($upload['size'] ?? 0);
    if ($size <= 0) json_error($name . ' is empty.');
    if ($size > $maxFileBytes) json_error($name . ' exceeds the 200 MB per-file limit.');
    $tmpPath = (string) ($upload['tmp_name'] ?? '');
    if ($tmpPath === '' || !is_uploaded_file($tmpPath)) json_error($name . ' is not a valid upload.');

    $mime = (new finfo(FILEINFO_MIME_TYPE))->file($tmpPath);
    if ($imageOnly && (!is_string($mime) || strpos($mime, 'image/') !== 0)) {
        json_error('The ID photo must be an image.');
    }
    if (!isset($allowedMimeTypes[$mime])) {
        json_error($name . ' has an unsupported file format.');
    }
    $safeName = preg_replace('/[^A-Za-z0-9._-]/', '_', $name);
    $uploadedFiles[] = [
        'tmp_path' => $tmpPath,
        'name' => substr($safeName ?: 'evidence', 0, 150),
        'type' => $allowedMimeTypes[$mime],
        'mime' => $mime,
        'size' => $size,
    ];
};

if (!isset($_FILES['id_photo'])) json_error('An ID photo is required.');
$addUpload($_FILES['id_photo'], 'id_photo', true);
if (isset($_FILES['evidence'])) {
    $incoming = $_FILES['evidence'];
    $names = is_array($incoming['name']) ? $incoming['name'] : [$incoming['name']];
    foreach ($names as $index => $name) {
        $addUpload([
            'name' => $name,
            'type' => is_array($incoming['type']) ? $incoming['type'][$index] : $incoming['type'],
            'tmp_name' => is_array($incoming['tmp_name']) ? $incoming['tmp_name'][$index] : $incoming['tmp_name'],
            'error' => is_array($incoming['error']) ? $incoming['error'][$index] : $incoming['error'],
            'size' => is_array($incoming['size']) ? $incoming['size'][$index] : $incoming['size'],
        ], 'evidence');
    }
}

$storageDir = getenv('PCA_EVIDENCE_DIR') ?: __DIR__ . '/../storage/evidence';
if (!is_dir($storageDir) && !mkdir($storageDir, 0700, true) && !is_dir($storageDir)) {
    json_error('Evidence storage is unavailable. Please try again later.', 500);
}

$storedPaths = [];
$pdo->beginTransaction();
try {
    $submittedStatus = $pdo->query("SELECT status_id FROM complaint_status_types WHERE code = 'SUBMITTED'")->fetchColumn();
    if (!$submittedStatus) throw new RuntimeException('Pending complaint status is not configured.');

    $availableColumns = array_column($pdo->query('SHOW COLUMNS FROM complaints')->fetchAll(), 'Field');
    $hasColumn = static fn(string $column): bool => in_array($column, $availableColumns, true);
    $categoryStmt = $pdo->prepare('SELECT category_id FROM complaint_categories WHERE name_en = :name LIMIT 1');
    $categoryStmt->execute(['name' => trim((string) $_POST['type'])]);
    $categoryId = $categoryStmt->fetchColumn();
    $localUuid = sprintf('%s-%s-%s-%s-%s',
        bin2hex(random_bytes(4)), bin2hex(random_bytes(2)), bin2hex(random_bytes(2)),
        bin2hex(random_bytes(2)), bin2hex(random_bytes(6)));

    $complaintType = trim((string) $_POST['type']);
    $description = trim((string) $_POST['description']);
    $legacyDetails = [];
    if (!$hasColumn('complaint_type')) $legacyDetails[] = 'Complaint type: ' . $complaintType;
    if (!$hasColumn('region_district')) $legacyDetails[] = 'Region and district: ' . $regionDistrict;
    if (!$hasColumn('next_of_kin_name')) {
        $legacyDetails[] = 'Next of kin name: ' . trim((string) $_POST['next_of_kin_name']);
    }
    if (!$hasColumn('next_of_kin_address')) {
        $legacyDetails[] = 'Next of kin address: ' . trim((string) $_POST['next_of_kin_address']);
    }
    if ($legacyDetails) {
        $description .= "\n\n[Additional complaint details]\n" . implode("\n", $legacyDetails);
    }

    $complaintValues = [
        'local_uuid' => $localUuid,
        'user_id' => $userId,
        'name' => trim((string) $_POST['name']),
        'contact' => trim((string) $_POST['contact']),
        'id_number' => trim((string) $_POST['id_number']),
        'current_location' => trim((string) $_POST['current_location']),
        'gender' => $_POST['gender'],
        'district_id' => $districtId,
        'category_id' => $categoryId ?: null,
        'category_unknown' => $categoryId ? 0 : 1,
        'incident_date' => $incidentDateTime->format('Y-m-d'),
        'incident_time' => $incidentDateTime->format('H:i:s'),
        'location' => trim((string) $_POST['location']),
        'description' => $description,
        'status_id' => $submittedStatus,
    ];
    $complaintColumns = [
        'local_uuid' => 'local_uuid',
        'user_id' => 'user_id',
        'name' => 'complainant_name',
        'contact' => 'complainant_phone',
        'id_number' => 'complainant_id_number',
        'current_location' => 'complainant_location',
        'gender' => 'complainant_gender',
        'district_id' => 'district_id',
        'category_id' => 'category_id',
        'category_unknown' => 'category_unknown',
        'incident_date' => 'incident_date',
        'incident_time' => 'incident_time',
        'location' => 'incident_location_text',
        'description' => 'description',
        'status_id' => 'current_status_id',
    ];
    if ($hasColumn('complaint_type')) {
        $complaintColumns['type'] = 'complaint_type';
        $complaintValues['type'] = $complaintType;
    }
    if ($hasColumn('region_district')) {
        $complaintColumns['region_district'] = 'region_district';
        $complaintValues['region_district'] = $regionDistrict;
    }
    if ($hasColumn('next_of_kin_name') && $hasColumn('next_of_kin_address')) {
        $complaintColumns['next_of_kin_name'] = 'next_of_kin_name';
        $complaintColumns['next_of_kin_address'] = 'next_of_kin_address';
        $complaintValues['next_of_kin_name'] = trim((string) $_POST['next_of_kin_name']);
        $complaintValues['next_of_kin_address'] = trim((string) $_POST['next_of_kin_address']);
    }
    $columns = array_values($complaintColumns);
    $placeholders = array_map(static fn(string $key): string => ':' . $key, array_keys($complaintColumns));
    $columns[] = 'declaration_confirmed';
    $placeholders[] = '1';
    $columns[] = 'sync_status';
    $placeholders[] = "'submitted'";
    $columns[] = 'submitted_at';
    $placeholders[] = 'NOW()';
    $stmt = $pdo->prepare(
        'INSERT INTO complaints (' . implode(', ', $columns) . ') VALUES (' . implode(', ', $placeholders) . ')'
    );
    $stmt->execute($complaintValues);
    $complaintId = (int) $pdo->lastInsertId();
    $year = date('Y');
    $gcr = sprintf('%s-%s-%04d', $regionLetter, $year, $complaintId);
    $pdo->prepare('UPDATE complaints SET gcr_number = :gcr WHERE complaint_id = :id')
        ->execute(['gcr' => $gcr, 'id' => $complaintId]);

    $officers = trim((string) ($_POST['officers'] ?? ''));
    if ($officers !== '') {
        $pdo->prepare(
            'INSERT INTO complaint_officer_info
                (complaint_id, officer_unknown, officer_name)
             VALUES (:complaint_id, 0, :officers)'
        )->execute(['complaint_id' => $complaintId, 'officers' => substr($officers, 0, 120)]);
    }
    $witnessName = trim((string) ($_POST['witness_name'] ?? ''));
    $witnessContacts = trim((string) ($_POST['witness_contacts'] ?? ''));
    if ($witnessName !== '') {
        $pdo->prepare(
            'INSERT INTO witnesses (complaint_id, full_name, contact_number)
             VALUES (:complaint_id, :name, :contacts)'
        )->execute([
            'complaint_id' => $complaintId,
            'name' => substr($witnessName, 0, 120),
            'contacts' => $witnessContacts ?: null,
        ]);
    }

    foreach ($uploadedFiles as $file) {
        $extension = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
        $storedName = bin2hex(random_bytes(24)) . ($extension ? '.' . $extension : '');
        $destination = rtrim($storageDir, DIRECTORY_SEPARATOR) . DIRECTORY_SEPARATOR . $storedName;
        if (!move_uploaded_file($file['tmp_path'], $destination)) {
            throw new RuntimeException('Could not store uploaded file ' . $file['name'] . '.');
        }
        $storedPaths[] = $destination;
        if (!chmod($destination, 0600)) throw new RuntimeException('Could not secure an uploaded file.');
        $pdo->prepare(
            "INSERT INTO evidence_files
                (complaint_id, file_name, file_type, storage_path, file_size_bytes,
                 checksum_sha256, upload_status, uploaded_at)
             VALUES (:complaint, :name, :type, :path, :size, :checksum, 'uploaded', NOW())"
        )->execute([
            'complaint' => $complaintId,
            'name' => $file['name'],
            'type' => $file['type'],
            'path' => $storedName,
            'size' => $file['size'],
            'checksum' => hash_file('sha256', $destination),
        ]);
    }

    $pdo->prepare(
        "INSERT INTO complaint_status_history (complaint_id, status_id, message_en, message_st)
         VALUES (:complaint_id, :status_id, 'Your complaint is pending.', 'Tletlebo ea hau e emetse.')"
    )->execute(['complaint_id' => $complaintId, 'status_id' => $submittedStatus]);

    $notificationType = $pdo->query(
        "SELECT type_id FROM notification_types WHERE code = 'COMPLAINT_SUBMITTED'"
    )->fetchColumn();
    $pdo->prepare(
        "INSERT INTO notifications (user_id, complaint_id, type_id, title_en, title_st, body_en, body_st)
         VALUES (:user_id, :complaint_id, :type_id, 'Complaint Submitted', 'Tletlebo e Rometsoe',
                 :body_en, :body_st)"
    )->execute([
        'user_id' => $userId,
        'complaint_id' => $complaintId,
        'type_id' => $notificationType,
        'body_en' => "Your complaint {$gcr} has been successfully submitted.",
        'body_st' => "Tletlebo ea hau {$gcr} e rometsoe ka katleho.",
    ]);
    $notificationId = (int) $pdo->lastInsertId();
    $pdo->prepare(
        "INSERT INTO audit_log (user_id, action, entity_type, entity_id, details)
         VALUES (:user_id, 'COMPLAINT_SUBMIT', 'complaints', :complaint_id, :gcr)"
    )->execute(['user_id' => $userId, 'complaint_id' => $complaintId, 'gcr' => $gcr]);

    $pdo->commit();
    deliver_complaint_update($pdo, $notificationId);
    try {
        send_complaint_confirmation_sms($pdo, $userId, $gcr);
    } catch (RuntimeException | PDOException $error) {
        error_log('Complaint confirmation SMS delivery failed: ' . $error->getMessage());
    }
    json_ok(['complaint_id' => $complaintId, 'gcr_number' => $gcr], 201);
} catch (Throwable $error) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    foreach ($storedPaths as $storedPath) {
        if (is_file($storedPath) && !unlink($storedPath)) {
            error_log('Could not remove evidence file after failed complaint submission: ' . $storedPath);
        }
    }
    error_log('Complaint submission failed: ' . $error->getMessage());
    json_error('Could not submit complaint. Please try again.', 500);
}
