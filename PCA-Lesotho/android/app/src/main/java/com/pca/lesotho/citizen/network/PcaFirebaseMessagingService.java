package com.pca.lesotho.citizen.network;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.ui.complaints.ComplaintDetailActivity;
import com.pca.lesotho.citizen.ui.home.MainActivity;

import java.util.Map;

/**
 * Receives push notifications for: complaint received, assigned, status changed,
 * progress update, additional info required, complaint closed.
 *
 * Per the design spec, push is a convenience layer only — every event is also
 * persisted server-side in `notifications` and fetched via
 * ApiService.getNotifications() on next app open/sync, so nothing is lost if a
 * push message is missed. Notification previews stay generic and never include
 * confidential complaint detail.
 */
public class PcaFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        SharedPreferences language = getSharedPreferences("pca_app", MODE_PRIVATE);
        boolean sesotho = "st".equals(language.getString("preferred_language", "en"));
        String title = getString(sesotho ? R.string.notification_push_title_st
                : R.string.notification_push_title);
        String body = getString(sesotho ? R.string.notification_push_body_st
                : R.string.notification_push_body);

        Intent intent = new Intent(this, MainActivity.class);
        Map<String, String> data = remoteMessage.getData();
        try {
            long complaintId = Long.parseLong(data.getOrDefault("complaint_id", ""));
            if (complaintId > 0) {
                intent = new Intent(this, ComplaintDetailActivity.class);
                intent.putExtra("complaint_id", complaintId);
            }
        } catch (NumberFormatException e) {
            android.util.Log.w("PcaMessaging", "Push message contained an invalid complaint reference.", e);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                (int) (System.currentTimeMillis() & 0x7fffffff), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "complaint_updates",
                    getString(R.string.notification_push_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.notification_push_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, "complaint_updates")
                .setSmallIcon(R.drawable.ic_nav_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        try {
            NotificationManagerCompat.from(this).notify(
                    (int) (System.currentTimeMillis() & 0x7fffffff), notification.build());
        } catch (SecurityException e) {
            android.util.Log.w("PcaMessaging", "Notification permission is not available.", e);
        }
    }

    @Override
    public void onNewToken(String token) {
        DeviceTokenRegistrar.register(this, token);
    }
}
