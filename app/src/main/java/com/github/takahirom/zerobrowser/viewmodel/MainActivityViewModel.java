package com.github.takahirom.zerobrowser.viewmodel;

import android.view.View;

import com.github.takahirom.zerobrowser.webview.WebViewTab;

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
}
