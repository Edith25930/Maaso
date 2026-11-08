package com.pca.lesotho.citizen.ui.staff;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pca.lesotho.citizen.R;
import com.pca.lesotho.citizen.network.SessionManager;
import com.pca.lesotho.citizen.ui.auth.LoginActivity;

import retrofit2.Response;

/** Small helpers shared by the staff screens. */
final class StaffUtil {
    private StaffUtil() {}

    static boolean isStaff(SessionManager session) {
        String role = session.getRole();
        return session.isLoggedIn() && !session.isGuest()
                && ("admin".equals(role) || "investigator".equals(role));
    }

    /** Null-safe string read from a JSON object. */
    static String str(JsonObject o, String key) {
        if (o == null || !o.has(key)) return "";
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    static int num(JsonObject o, String key) {
        String v = str(o, key);
        if (v.isEmpty()) return 0;
        try { return (int) Double.parseDouble(v); } catch (NumberFormatException e) { return 0; }
    }

    /** Returns the "data" object of a successful response, or null (and shows a message). */
    static JsonObject dataOrNull(Context context, Response<JsonObject> response) {
        if (response.isSuccessful() && response.body() != null
                && response.body().has("success") && response.body().get("success").getAsBoolean()) {
            JsonElement data = response.body().get("data");
            return data != null && data.isJsonObject() ? data.getAsJsonObject() : new JsonObject();
        }
        String message = context.getString(R.string.staff_error_generic);
        try {
            if (response.errorBody() != null) {
                JsonObject err = com.google.gson.JsonParser
                        .parseString(response.errorBody().string()).getAsJsonObject();
                if (err.has("error")) message = err.get("error").getAsString();
            }
        } catch (Exception ignored) { }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        if (response.code() == 401) {
            new SessionManager(context).clear();
            Intent intent = new Intent(context, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        }
        return null;
    }

    static String priorityLabel(String code) {
        if (code == null || code.isEmpty()) return "Normal";
        return Character.toUpperCase(code.charAt(0)) + code.substring(1);
    }
}
