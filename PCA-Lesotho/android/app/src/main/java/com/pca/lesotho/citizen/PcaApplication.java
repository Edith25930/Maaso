package com.pca.lesotho.citizen;

import android.app.Application;

public class PcaApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppLanguage.applySaved(this);
    }
}
