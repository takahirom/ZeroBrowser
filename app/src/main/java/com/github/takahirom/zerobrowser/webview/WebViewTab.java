package com.github.takahirom.zerobrowser.webview;

import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebView;

public class WebViewTab {

    private final WebView webView;

    public WebViewTab(WebView webView) {
        this.webView = webView;
    }

    public void init() {
        webView.setScrollbarFadingEnabled(true);
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setMapTrackballToArrowKeys(false); // use trackball directly
        // Enable the built-in zoom
        webView.getSettings().setBuiltInZoomControls(true);
        final PackageManager pm = webView.getContext().getPackageManager();
        boolean supportsMultiTouch =
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)
                        || pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH_MULTITOUCH_DISTINCT);
        webView.getSettings().setDisplayZoomControls(!supportsMultiTouch);

        // Add this WebView to the settings observer list and update the
        // settings
        final BrowserSettings s = BrowserSettings.getInstance();
        s.startManagingSettings(webView.getSettings());

        CookieManager cookieManager = CookieManager.getInstance();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, cookieManager.acceptCookie());
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            // Remote Web Debugging is always enabled, where available.
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    public void loadHome() {
        webView.loadUrl(BrowserSettings.getInstance().getHomePage());
    }
}
