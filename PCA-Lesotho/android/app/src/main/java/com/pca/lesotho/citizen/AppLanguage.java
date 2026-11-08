package com.pca.lesotho.citizen;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class AppLanguage {
    private static final String PREFERENCES = "pca_app";
    private static final String LANGUAGE_KEY = "preferred_language";

    private AppLanguage() { }

    public static String get(Context context) {
        return "st".equals(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(LANGUAGE_KEY, "en")) ? "st" : "en";
    }

    public static boolean isSesotho(Context context) {
        return "st".equals(get(context));
    }

    public static void set(Context context, String language) {
        String selected = "st".equals(language) ? "st" : "en";
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(LANGUAGE_KEY, selected)
                .apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected));
    }

    public static void applySaved(Context context) {
        String selected = get(context);
        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        if (current.isEmpty() || !selected.equals(current.get(0).getLanguage())) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected));
        }
    }
}
