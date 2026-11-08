package com.pca.lesotho.citizen.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.i18n.phonenumbers.NumberParseException;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.ApiClient;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.home.MainActivity;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";
    static final String EXTRA_REGISTERED_PHONE = "registered_phone";

    private EditText fullNameInput, idNumberInput, phoneInput, emailInput, passwordInput, confirmInput;
    private CheckBox agreeCheckbox;
    private ProgressBar progressBar;
    private Button createAccountButton;
    private Spinner countryCodeSpinner;
    private final List<CountryOption> countries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        fullNameInput = findViewById(R.id.inputFullName);
        idNumberInput = findViewById(R.id.inputIdNumber);
        phoneInput = findViewById(R.id.inputPhone);
        emailInput = findViewById(R.id.inputEmail);
        passwordInput = findViewById(R.id.inputPassword);
        confirmInput = findViewById(R.id.inputConfirmPassword);
        agreeCheckbox = findViewById(R.id.checkboxAgree);
        progressBar = findViewById(R.id.registerProgress);
        createAccountButton = findViewById(R.id.btnCreateAccount);
        countryCodeSpinner = findViewById(R.id.spinnerCountryCode);
        setupCountryCodes();

        createAccountButton.setOnClickListener(v -> attemptRegister());
        findViewById(R.id.btnBackToSignIn).setOnClickListener(v -> finish());
    }

    private void setupCountryCodes() {
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        for (String region : phoneUtil.getSupportedRegions()) {
            int callingCode = phoneUtil.getCountryCodeForRegion(region);
            if (callingCode > 0) {
                countries.add(new CountryOption(region,
                        new Locale("", region).getDisplayCountry(), "+" + callingCode));
            }
        }
        countries.sort(Comparator.comparing(country -> country.name, String.CASE_INSENSITIVE_ORDER));
        ArrayAdapter<CountryOption> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, countries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countryCodeSpinner.setAdapter(adapter);
        for (int i = 0; i < countries.size(); i++) {
            if ("LS".equals(countries.get(i).region)) {
                countryCodeSpinner.setSelection(i);
                break;
            }
        }
    }

    private void attemptRegister() {
        String fullName = fullNameInput.getText().toString().trim();
        String idNumber = idNumberInput.getText().toString().trim();
        String nationalPhone = phoneInput.getText().toString().replaceAll("\\D", "");
        CountryOption country = (CountryOption) countryCodeSpinner.getSelectedItem();
        if (nationalPhone.isEmpty()) {
            phoneInput.setError(getString(R.string.error_fill_all_fields));
            return;
        }
        String phone;
        try {
            PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed =
                    phoneUtil.parse(nationalPhone, country.region);
            if (!phoneUtil.isPossibleNumber(parsed)) {
                phoneInput.setError(getString(R.string.register_phone_length));
                return;
            }
            phone = phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            phoneInput.setError(getString(R.string.register_phone_length));
            return;
        }
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirm = confirmInput.getText().toString();

        if (fullName.isEmpty() || idNumber.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.error_fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirm)) {
            Toast.makeText(this, R.string.error_passwords_dont_match, Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 8) {
            Toast.makeText(this, R.string.error_password_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!agreeCheckbox.isChecked()) {
            Toast.makeText(this, R.string.error_must_agree_privacy, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        Map<String, Object> body = new HashMap<>();
        body.put("full_name", fullName);
        body.put("id_number", idNumber);
        body.put("phone_number", phone);
        if (!email.isEmpty()) body.put("email", email);
        body.put("password", password);
        body.put("confirm_password", confirm);
        body.put("agreed_to_privacy_notice", true);

        ApiClient.getService().register(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JsonObject responseBody = response.body();
                if (response.isSuccessful()
                        && responseBody != null
                        && responseBody.has("success")
                        && responseBody.get("success").isJsonPrimitive()
                        && responseBody.getAsJsonPrimitive("success").isBoolean()
                        && responseBody.get("success").getAsBoolean()) {
                    boolean verificationEmailSent = !email.isEmpty()
                            && responseBody.has("data")
                            && responseBody.getAsJsonObject("data").has("email_verification_sent")
                            && responseBody.getAsJsonObject("data").get("email_verification_sent").getAsBoolean();
                    signInAfterRegistration(phone, password, email.isEmpty(), verificationEmailSent);
                } else {
                    setLoading(false);
                    if (response.code() == 409) {
                        String message = getErrorMessage(response);
                        String identifier = message.toLowerCase(Locale.ROOT).contains("email")
                                && !message.toLowerCase(Locale.ROOT).contains("phone")
                                ? email : phone;
                        goToSignIn(identifier, getString(R.string.account_already_exists_sign_in));
                    } else {
                        Toast.makeText(RegisterActivity.this, getErrorMessage(response), Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                Log.e(TAG, "Registration failed due to network error: " + t.getMessage(), t);
                Toast.makeText(RegisterActivity.this, R.string.error_no_internet, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void signInAfterRegistration(String phone, String password,
                                         boolean noEmail, boolean verificationEmailSent) {
        Map<String, Object> body = new HashMap<>();
        body.put("identifier", phone);
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
                        && responseBody.getAsJsonPrimitive("success").isBoolean()
                        && responseBody.getAsJsonPrimitive("success").getAsBoolean()
                        && responseBody.has("data")
                        && responseBody.get("data").isJsonObject()) {
                    JsonObject data = responseBody.getAsJsonObject("data");
                    if (data.has("token") && data.has("user_id")
                            && data.has("full_name") && data.has("phone_number")) {
                        new SessionManager(RegisterActivity.this).saveSession(
                                data.get("token").getAsString(),
                                data.get("user_id").getAsLong(),
                                data.get("full_name").getAsString(),
                                data.get("phone_number").getAsString(),
                                data.has("id_number") && !data.get("id_number").isJsonNull()
                                        ? data.get("id_number").getAsString() : "",
                                data.has("email") && !data.get("email").isJsonNull()
                                        ? data.get("email").getAsString() : "",
                                data.has("role") ? data.get("role").getAsString() : "citizen",
                                false
                        );
                        int message = noEmail ? R.string.account_created_signed_in
                                : (verificationEmailSent ? R.string.verification_email_sent
                                : R.string.verification_email_not_sent);
                        Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
                        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                        finish();
                        return;
                    }
                }
                openSignIn(phone);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.w(TAG, "Account created but automatic sign-in failed", t);
                setLoading(false);
                openSignIn(phone);
            }
        });
    }

    private void openSignIn(String phone) {
        Toast.makeText(this, R.string.account_created_please_sign_in, Toast.LENGTH_LONG).show();
        goToSignIn(phone, null);
    }

    private void goToSignIn(String phone, String message) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(EXTRA_REGISTERED_PHONE, phone);
        if (message != null) intent.putExtra(LoginActivity.EXTRA_MESSAGE, message);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        createAccountButton.setEnabled(!loading);
    }

    private String getErrorMessage(Response<JsonObject> response) {
        JsonObject errorBody = response.body();
        if (errorBody == null && response.errorBody() != null) {
            try {
                JsonElement parsed = JsonParser.parseString(response.errorBody().string());
                if (parsed.isJsonObject()) {
                    errorBody = parsed.getAsJsonObject();
                }
            } catch (IOException | JsonParseException e) {
                Log.w(TAG, "Could not read registration error response", e);
            }
        }

        if (errorBody != null && errorBody.has("error")) {
            JsonElement error = errorBody.get("error");
            if (error.isJsonPrimitive()) {
                String message = error.getAsString().trim();
                if (!message.isEmpty()) {
                    return message;
                }
            }
        }
        return getString(R.string.error_generic);
    }

    private static final class CountryOption {
        final String region;
        final String name;
        final String callingCode;

        CountryOption(String region, String name, String callingCode) {
            this.region = region;
            this.name = name;
            this.callingCode = callingCode;
        }

        @Override
        public String toString() {
            return name + " (" + callingCode + ")";
        }
    }
}
