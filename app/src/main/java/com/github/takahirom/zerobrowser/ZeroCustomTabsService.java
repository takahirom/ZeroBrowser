/*
 * Copyright [2016] [takahirom]
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

import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsService;
import android.support.customtabs.CustomTabsSessionToken;
import android.widget.Toast;

import java.util.List;

/**
 * Created by takahirom on 2016/05/21.
 */

public class ZeroCustomTabsService extends CustomTabsService {
    @Override
    protected boolean warmup(long l) {
        return false;
    }

    @Override
    protected boolean newSession(CustomTabsSessionToken customTabsSessionToken) {
        Toast.makeText(this, customTabsSessionToken.toString(), Toast.LENGTH_SHORT).show();
        return false;
    }

    @Override
    protected boolean mayLaunchUrl(CustomTabsSessionToken customTabsSessionToken, Uri uri, Bundle bundle, List<Bundle> list) {
        Toast.makeText(this, uri.toString(), Toast.LENGTH_SHORT).show();
        return false;
    }

    @Override
    protected Bundle extraCommand(String s, Bundle bundle) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        return null;
    }

    @Override
    protected boolean updateVisuals(CustomTabsSessionToken customTabsSessionToken, Bundle bundle) {
        Toast.makeText(this, "customTabsSessionToken:" + customTabsSessionToken, Toast.LENGTH_SHORT).show();
        return false;
    }
}
