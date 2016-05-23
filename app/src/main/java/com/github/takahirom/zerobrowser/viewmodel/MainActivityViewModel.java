/*
 * Copyright 2016 takahirom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.takahirom.zerobrowser.viewmodel;

import android.util.Patterns;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

import com.github.takahirom.zerobrowser.MainActivity;
import com.github.takahirom.zerobrowser.view.webview.WebViewTab;

public class MainActivityViewModel {
    private final WebViewTab webViewTab;
    private final MainActivity activity;

    public MainActivityViewModel(MainActivity activity, WebViewTab webViewTab) {
        this.webViewTab = webViewTab;
        this.activity = activity;
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
        if (Patterns.WEB_URL.matcher(query).matches()) {
            webViewTab.loadUrl(query,null);
            return;
        }
        webViewTab.loadUrl("http://google.com/search?q=" + query, null);
    }

    public void onPageFinished(WebView view, String url) {
        activity.setSearchText(url);
    }

    public void onProgressChanged(int newProgress) {
        activity.setWebProgress(newProgress);
    }
}
