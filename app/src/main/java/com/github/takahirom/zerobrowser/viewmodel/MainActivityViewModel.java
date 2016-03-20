package com.github.takahirom.zerobrowser.viewmodel;

import android.view.View;

import com.github.takahirom.zerobrowser.view.webview.WebViewTab;

/**
 * Created by takahirom on 16/03/12.
 */
public class MainActivityViewModel {
    private final WebViewTab webViewTab;

    public MainActivityViewModel(WebViewTab webViewTab) {
        this.webViewTab = webViewTab;
    }

    public void onClickBack(View v) {
    }

    /**
     * @return isHandled
     */
    public boolean onBackPressed() {
        if (webViewTab.canGoBack()) {
            webViewTab.goBack();
            return true;
        }
        return false;
    }
}
