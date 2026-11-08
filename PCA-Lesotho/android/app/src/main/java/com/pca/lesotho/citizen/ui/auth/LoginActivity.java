package com.pca.lesotho.citizen.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.pca.lesotho.citizen.AppLanguage;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.home.MainActivity;
import com.pca.lesotho.citizen.ui.onboarding.OnboardingActivity;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    static final String EXTRA_MESSAGE = "login_message";

    private EditText identifierInput, passwordInput;
    private ProgressBar progressBar;
    private Button signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        identifierInput = findViewById(R.id.inputIdentifier);
        passwordInput = findViewById(R.id.inputPassword);
        progressBar = findViewById(R.id.loginProgress);
        signInButton = findViewById(R.id.btnSignIn);
        TextView languageButton = findViewById(R.id.btnToggleLanguage);
        languageButton.setText(AppLanguage.isSesotho(this)
                ? R.string.switch_to_english : R.string.switch_to_sesotho);
        identifierInput.setText(getIntent().getStringExtra(RegisterActivity.EXTRA_REGISTERED_PHONE));
        String message = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        signInButton.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.btnCreateAccount).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.btnContinueAsGuest).setOnClickListener(v -> continueAsGuest());
        findViewById(R.id.btnForgotPassword).setOnClickListener(v -> requestPasswordReset());
        findViewById(R.id.btnGettingStarted).setOnClickListener(v ->
                startActivity(new Intent(this, OnboardingActivity.class)));
        findViewById(R.id.btnToggleLanguage).setOnClickListener(v ->
                AppLanguage.set(this, AppLanguage.isSesotho(this) ? "en" : "st"));
    }

    private void attemptLogin() {
        String identifier = identifierInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (identifier.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.error_fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        Map<String, Object> body = new HashMap<>();
        body.put("identifier", identifier);
        body.put("password", password);
        body.put("device_label", "android-app");

        ApiClient.getService().login(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                setLoading(false);
                JsonObject responseBody = response.body();
                if (response.isSuccessful()
                        && responseBody != null
                        && responseBody.has("success")
                        && responseBody.get("success").isJsonPrimitive()
                        && responseBody.get("success").getAsBoolean()
                        && responseBody.has("data")
                        && responseBody.get("data").isJsonObject()) {
                    JsonObject data = responseBody.getAsJsonObject("data");
                    if (!data.has("token") || !data.has("user_id")
                            || !data.has("full_name") || !data.has("phone_number")) {
                        Toast.makeText(LoginActivity.this, R.string.error_generic, Toast.LENGTH_LONG).show();
                        return;
                    }
                    SessionManager session = new SessionManager(LoginActivity.this);
                    String role = data.has("role") ? data.get("role").getAsString() : "citizen";
                    session.saveSession(
                            data.get("token").getAsString(),
                            data.get("user_id").getAsLong(),
                            data.get("full_name").getAsString(),
                            data.get("phone_number").getAsString(),
                            data.has("id_number") && !data.get("id_number").isJsonNull()
                                    ? data.get("id_number").getAsString() : "",
                            data.has("email") && !data.get("email").isJsonNull()
                                    ? data.get("email").getAsString() : "",
                            role,
                            false
                    );
                    if ("admin".equals(role) || "investigator".equals(role)) {
                        startActivity(new Intent(LoginActivity.this, com.pca.lesotho.citizen.ui.staff.StaffDashboardActivity.class));
                    } else {
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    }
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, getErrorMessage(response), Toast.LENGTH_LONG).show();
                }

            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                android.util.Log.e("LoginActivity", "Login network failure: " + t.getMessage(), t);
                Toast.makeText(LoginActivity.this, R.string.error_no_internet, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestPasswordReset() {
        EditText emailInput = new EditText(this);
        emailInput.setSingleLine(true);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setHint(R.string.register_email_optional);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.forgot_password)
                .setMessage(R.string.password_reset_prompt)
                .setView(emailInput)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.continue_label, (dialog, which) -> {
                    String email = emailInput.getText().toString().trim();
                    if (email.isEmpty()) {
                        Toast.makeText(this, R.string.error_fill_all_fields, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> body = new HashMap<>();
                    body.put("email", email);
                    ApiClient.getService().requestPasswordReset(body).enqueue(new Callback<JsonObject>() {
                        @Override
                        public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                            Toast.makeText(LoginActivity.this, response.isSuccessful()
                                    ? R.string.password_reset_requested : R.string.error_generic, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onFailure(Call<JsonObject> call, Throwable t) {
                            Toast.makeText(LoginActivity.this, R.string.error_no_internet, Toast.LENGTH_LONG).show();
                        }
                    });
                }).show();
    }

    private void continueAsGuest() {
        setLoading(true);
        ApiClient.getService().guestSession().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                setLoading(false);
                JsonObject responseBody = response.body();
                if (response.isSuccessful()
                        && responseBody != null
                        && responseBody.has("success")
                        && responseBody.get("success").isJsonPrimitive()
                        && responseBody.get("success").getAsBoolean()
                        && responseBody.has("data")
                        && responseBody.get("data").isJsonObject()) {
                    JsonObject data = responseBody.getAsJsonObject("data");
                    if (!data.has("token") || !data.has("user_id")) {
                        Toast.makeText(LoginActivity.this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SessionManager session = new SessionManager(LoginActivity.this);
                    session.saveSession(data.get("token").getAsString(), data.get("user_id").getAsLong(), "Guest", "", true);
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    Toast.makeText(LoginActivity.this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, R.string.error_no_internet, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!loading);
    }

    private String getErrorMessage(Response<JsonObject> response) {
        JsonObject errorBody = response.body();
        if (errorBody == null && response.errorBody() != null) {
            try {
                JsonElement parsed = JsonParser.parseString(response.errorBody().string());
                if (parsed.isJsonObject()) errorBody = parsed.getAsJsonObject();
            } catch (IOException | JsonParseException e) {
                android.util.Log.w("LoginActivity", "Could not read login error response.", e);
            }
        }
        if (errorBody != null && errorBody.has("error")) {
            JsonElement error = errorBody.get("error");
            if (error.isJsonPrimitive() && !error.getAsString().trim().isEmpty()) {
                String message = error.getAsString().trim();
                if (message.equalsIgnoreCase("Incorrect email/phone number or password.")) {
                    return getString(R.string.error_login_failed);
                }
                return message;
            }
        }
        return getString(R.string.error_generic);
    }
}
