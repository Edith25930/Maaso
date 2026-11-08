package com.pca.lesotho.citizen.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.ui.auth.LoginActivity;

public class LanguageSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_select);

        findViewById(R.id.cardEnglish).setOnClickListener(v -> selectLanguage("en"));
        findViewById(R.id.cardSesotho).setOnClickListener(v -> selectLanguage("st"));
        findViewById(R.id.btnSkipIntro).setOnClickListener(v -> finishIntroduction());
    }

    private void selectLanguage(String code) {
        AppLanguage.set(this, code);
        startActivity(new Intent(this, OnboardingActivity.class));
        finish();
    }

    private void finishIntroduction() {
        getSharedPreferences("pca_app", MODE_PRIVATE).edit()
                .putBoolean("onboarding_complete", true)
                .commit();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
