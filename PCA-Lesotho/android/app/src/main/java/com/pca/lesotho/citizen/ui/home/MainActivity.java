package com.pca.lesotho.citizen.ui.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.messaging.FirebaseMessaging;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.DeviceTokenRegistrar;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.complaints.ComplaintsFragment;
import com.pca.lesotho.citizen.ui.learn.LearnFragment;
import com.pca.lesotho.citizen.ui.notifications.NotificationsFragment;
import com.pca.lesotho.citizen.ui.profile.ProfileFragment;

/** Hosts the bottom navigation: Home | Complaints | Learn | Notifications | Profile */
public class MainActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = new SessionManager(this);
        String role = session.getRole();
        if (session.isLoggedIn() && ("admin".equals(role) || "investigator".equals(role))) {
            startActivity(new Intent(this, com.pca.lesotho.citizen.ui.staff.StaffDashboardActivity.class));
            finish();
            return;
        }
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!granted) {
                        android.util.Log.i("MainActivity", "Push notification permission was not granted.");
                    }
                });
        setContentView(R.layout.activity_main);
        registerPushToken();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_complaints) {
                fragment = new ComplaintsFragment();
            } else if (id == R.id.nav_learn) {
                fragment = new LearnFragment();
            } else if (id == R.id.nav_notifications) {
                requestNotificationPermissionIfNeeded();
                fragment = new NotificationsFragment();
            } else {
                fragment = new ProfileFragment();
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    private void registerPushToken() {
        SessionManager session = new SessionManager(this);
        String savedToken = DeviceTokenRegistrar.getSavedToken(this);
        if (savedToken != null) DeviceTokenRegistrar.register(this, savedToken);
        if (!session.isLoggedIn() || session.isGuest()) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    android.util.Log.w("MainActivity", "Could not obtain push notification token.",
                            task.getException());
                    return;
                }
                DeviceTokenRegistrar.register(this, task.getResult());
            });
        } catch (IllegalStateException e) {
            android.util.Log.w("MainActivity", "Firebase Messaging is not configured for this build.", e);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(R.string.notification_permission_rationale)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.nav_notifications,
                        (dialog, which) -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS))
                .show();
    }
}
