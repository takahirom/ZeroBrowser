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
