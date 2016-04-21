package com.github.takahirom.zerobrowser.viewmodel;

import android.view.View;
import android.widget.Toast;

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
        webViewTab.goBack();
    }

    public void onClickForward(View v) {
        webViewTab.goForward();
    }

    public void onClickBookmark(View v) {
        Toast.makeText(v.getContext(), "not implemented now", Toast.LENGTH_SHORT).show();
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

    public void onSubmitQuery(String query) {
        webViewTab.loadUrl("http://google.com?q=" + query, null);
    }
}
