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
