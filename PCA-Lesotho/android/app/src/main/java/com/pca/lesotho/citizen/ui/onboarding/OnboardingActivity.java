package com.pca.lesotho.citizen.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.home.MainActivity;
import com.pca.lesotho.citizen.ui.auth.LoginActivity;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private TextView next;
    private TextView pageIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        pager = findViewById(R.id.onboardingPager);
        pager.setAdapter(new OnboardingPagerAdapter());

        findViewById(R.id.btnSkip).setOnClickListener(v -> finishOnboarding());

        next = findViewById(R.id.btnGetStarted);
        pageIndicator = findViewById(R.id.textPageIndicator);
        updatePageControls(0);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageControls(position);
            }
        });
        next.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < OnboardingPagerAdapter.PAGE_COUNT - 1) {
                pager.setCurrentItem(current + 1);
            } else {
                finishOnboarding();
            }
        });
    }

    private void updatePageControls(int position) {
        pageIndicator.setText(getString(R.string.onboarding_page, position + 1,
                OnboardingPagerAdapter.PAGE_COUNT));
        next.setText(position == OnboardingPagerAdapter.PAGE_COUNT - 1
                ? R.string.get_started : R.string.continue_label);
    }

    private void finishOnboarding() {
        getSharedPreferences("pca_app", MODE_PRIVATE).edit()
                .putBoolean("onboarding_complete", true)
                .commit();
        Intent intent = new Intent(this, new SessionManager(this).isLoggedIn()
                ? MainActivity.class : LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
