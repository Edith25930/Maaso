<?php
require_once __DIR__ . '/../config/email.php';
require_once __DIR__ . '/../config/push.php';

function deliver_complaint_update(PDO $pdo, int $notificationId): void {
    try {
        $stmt = $pdo->prepare(
            "SELECT n.user_id, n.complaint_id, t.is_essential, u.full_name, u.email, u.email_verified_at,
                    COALESCE(p.push_enabled, 1) AS push_enabled,
                    COALESCE(p.email_enabled, 1) AS email_enabled,
                    COALESCE(p.status_update_enabled, 1) AS status_update_enabled
             FROM notifications n
             JOIN notification_types t ON t.type_id = n.type_id
             JOIN users u ON u.user_id = n.user_id
             LEFT JOIN notification_preferences p ON p.user_id = n.user_id
             WHERE n.notification_id = :id"
        );
        $stmt->execute(['id' => $notificationId]);
        $notification = $stmt->fetch();
    } catch (PDOException $e) {
        error_log('Complaint notification delivery failed while loading its preferences.');
        return;
    }
    if (!$notification) {
        error_log('Complaint notification delivery skipped: notification record was not found.');
        return;
    }
    $essential = (int) $notification['is_essential'] === 1;
    if (!$essential && !(int) $notification['status_update_enabled']) return;

    if ($essential || (int) $notification['push_enabled']) {
        try {
            $account = firebase_service_account();
            if ($account !== null) {
                $devices = $pdo->prepare(
                    'SELECT device_id, token_hash, device_token FROM user_device_tokens WHERE user_id = :user'
                );
                $devices->execute(['user' => $notification['user_id']]);
                $deviceRows = $devices->fetchAll();
                if ($deviceRows) {
                    $accessToken = firebase_access_token($account);
                    foreach ($deviceRows as $device) {
                        try {
                            if (!send_firebase_complaint_update(
                                $account,
                                $accessToken,
                                $device['device_token'],
                                (int) $notification['complaint_id']
                            )) {
                                $pdo->prepare('DELETE FROM user_device_tokens WHERE device_id = :id')
                                    ->execute(['id' => $device['device_id']]);
                            }
                        } catch (RuntimeException | PDOException $e) {
                            error_log('A complaint push notification could not be delivered to one device: ' . $e->getMessage());
                        }
                    }
                }
            } else {
                error_log('Complaint push delivery skipped because Firebase is not configured.');
            }
        } catch (RuntimeException | PDOException $e) {
            error_log('Complaint push notification delivery failed: ' . $e->getMessage());
        }
    }

    if (($essential || (int) $notification['email_enabled']) && !empty($notification['email'])
            && !empty($notification['email_verified_at'])) {
        try {
            $baseUrl = rtrim(trim(pca_email_setting('PCA_PUBLIC_BASE_URL', 'public_base_url')), '/');
            $parts = $baseUrl !== '' ? parse_url($baseUrl) : false;
            if (!$parts || ($parts['scheme'] ?? '') !== 'https' || empty($parts['host'])
                    || isset($parts['user']) || isset($parts['pass'])
                    || isset($parts['query']) || isset($parts['fragment'])) {
                throw new RuntimeException('The public HTTPS base URL is not configured.');
            }
            $safeName = htmlspecialchars($notification['full_name'], ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
            $html = '<!doctype html><html lang="en"><meta charset="utf-8"><body style="font:16px Arial,sans-serif;color:#17243a">'
                . '<h2>Police Complaints Authority — Lesotho</h2><p>Hello ' . $safeName . ',</p>'
                . '<p>There is a new update to your complaint. Sign in to the PCA mobile app to view it.</p>'
                . '<p lang="st">Ho na le ntlafatso e ncha ka tletlebo ea hao. Kena ho app ea PCA ho e sheba.</p></body></html>';
            send_smtp_email($notification['email'], $notification['full_name'],
                'A new update is available for your PCA complaint', $html);
        } catch (RuntimeException $e) {
            error_log('Complaint update email delivery failed: ' . $e->getMessage());
        }
    }
}
