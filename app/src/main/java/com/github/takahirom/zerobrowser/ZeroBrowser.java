package com.github.takahirom.zerobrowser;

import android.app.Application;
import android.webkit.CookieSyncManager;

import com.github.takahirom.zerobrowser.view.webview.BrowserSettings;

public class ZeroBrowser extends Application{
    @Override
    public void onCreate() {
        super.onCreate();
        CookieSyncManager.createInstance(this);
        BrowserSettings.initialize(getApplicationContext());
    }
}
