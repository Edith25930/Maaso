package com.pca.lesotho.citizen.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.home.MainActivity;
import com.pca.lesotho.citizen.ui.onboarding.LanguageSelectActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, SPLASH_DELAY_MS);
    }

    private void routeNext() {
        SessionManager session = new SessionManager(this);
        boolean onboardingDone = getSharedPreferences("pca_app", MODE_PRIVATE)
                .getBoolean("onboarding_complete", false);

        Intent intent;
        if (session.isLoggedIn()) {
            String role = session.getRole();
            if ("admin".equals(role) || "investigator".equals(role)) {
                intent = new Intent(this, com.pca.lesotho.citizen.ui.staff.StaffDashboardActivity.class);
            } else {
                intent = new Intent(this, MainActivity.class);
            }
        } else if (onboardingDone) {
            intent = new Intent(this, com.pca.lesotho.citizen.ui.auth.LoginActivity.class);
        } else {
            intent = new Intent(this, LanguageSelectActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
