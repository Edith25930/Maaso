package com.pca.lesotho.citizen.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;

import java.util.Collections;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DeviceTokenRegistrar {
    private static final String PREFS = "pca_push";
    private static final String TOKEN_KEY = "device_token";

    private DeviceTokenRegistrar() {}

    public static void register(Context context, String deviceToken) {
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(TOKEN_KEY, deviceToken).apply();
        SessionManager session = new SessionManager(appContext);
        if (!session.isLoggedIn() || session.isGuest()) return;

        ApiClient.getService().registerDeviceToken(ApiClient.bearer(session.getToken()),
                Collections.singletonMap("device_token", deviceToken))
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (!response.isSuccessful()) {
                            android.util.Log.w("DeviceTokenRegistrar",
                                    "Device notification token registration failed: HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable error) {
                        android.util.Log.w("DeviceTokenRegistrar",
                                "Device notification token registration failed.", error);
                    }
                });
    }

    public static String getSavedToken(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TOKEN_KEY, null);
    }

    public static void unregister(Context context, String bearerToken) {
        String deviceToken = getSavedToken(context);
        if (deviceToken == null || bearerToken == null) return;
        ApiClient.getService().unregisterDeviceToken(bearerToken,
                Collections.singletonMap("device_token", deviceToken))
                .enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (!response.isSuccessful()) {
                            android.util.Log.w("DeviceTokenRegistrar",
                                    "Device notification token removal failed: HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable error) {
                        android.util.Log.w("DeviceTokenRegistrar",
                                "Device notification token removal failed.", error);
                    }
                });
    }
}
