package com.pca.lesotho.citizen.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/** Stores the bearer token and profile in Android Keystore-backed encrypted preferences. */
public class SessionManager {
    private static final String LEGACY_PREFS = "pca_session";
    private static final String PREFS = "pca_session_encrypted";
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    appContext,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            migrateLegacySession(appContext, prefs);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Could not initialize encrypted session storage.", e);
        }
    }

    private static void migrateLegacySession(Context context, SharedPreferences encrypted) {
        SharedPreferences legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        if (!legacy.contains("token")) return;
        if (encrypted.contains("token")) {
            if (!legacy.edit().clear().commit()) {
                throw new IllegalStateException("Could not remove the legacy session copy.");
            }
            return;
        }

        SharedPreferences.Editor editor = encrypted.edit();
        for (java.util.Map.Entry<String, ?> entry : legacy.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
        }
        if (!editor.commit() || !legacy.edit().clear().commit()) {
            throw new IllegalStateException("Could not securely migrate the saved session.");
        }
    }

    public void saveSession(String token, long userId, String fullName, String phoneNumber, boolean isGuest) {
        saveSession(token, userId, fullName, phoneNumber, "", "", "citizen", isGuest);
    }

    public void saveSession(String token, long userId, String fullName, String phoneNumber,
                            String idNumber, boolean isGuest) {
        saveSession(token, userId, fullName, phoneNumber, idNumber, "", "citizen", isGuest);
    }

    public void saveSession(String token, long userId, String fullName, String phoneNumber,
                            String idNumber, String role, boolean isGuest) {
        saveSession(token, userId, fullName, phoneNumber, idNumber, "", role, isGuest);
    }

    public void saveSession(String token, long userId, String fullName, String phoneNumber,
                            String idNumber, String email, String role, boolean isGuest) {
        prefs.edit()
                .putString("token", token)
                .putLong("user_id", userId)
                .putString("full_name", fullName)
                .putString("phone_number", phoneNumber)
                .putString("id_number", idNumber)
                .putString("email", email)
                .putString("role", role)
                .putString("complaint_prefill_name", fullName)
                .putString("complaint_prefill_id_number", idNumber)
                .putBoolean("complaint_prefill_cleared", false)
                .putBoolean("is_guest", isGuest)
                .apply();
    }

    public String getToken() { return prefs.getString("token", null); }
    public long getUserId() { return prefs.getLong("user_id", 0L); }
    public boolean isLoggedIn() { return getToken() != null; }
    public boolean isGuest() { return prefs.getBoolean("is_guest", false); }
    public String getRole() { return prefs.getString("role", "citizen"); }
    public String getFullName() { return prefs.getString("full_name", ""); }
    public String getPhoneNumber() { return prefs.getString("phone_number", ""); }
    public String getIdNumber() { return prefs.getString("id_number", ""); }
    public String getEmail() { return prefs.getString("email", ""); }
    public String getComplaintPrefillName() {
        return prefs.getBoolean("complaint_prefill_cleared", false)
                ? "" : prefs.getString("complaint_prefill_name", getFullName());
    }
    public String getComplaintPrefillIdNumber() {
        return prefs.getBoolean("complaint_prefill_cleared", false)
                ? "" : prefs.getString("complaint_prefill_id_number", getIdNumber());
    }

    public void clearComplaintPrefill() {
        prefs.edit()
                .remove("complaint_prefill_name")
                .remove("complaint_prefill_id_number")
                .putBoolean("complaint_prefill_cleared", true)
                .apply();
    }

    public void clear() { prefs.edit().clear().apply(); }
}
