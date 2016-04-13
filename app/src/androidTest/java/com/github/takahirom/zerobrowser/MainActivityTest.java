package com.github.takahirom.zerobrowser;

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.takahirom.zerobrowser.view.webview.ZeroBrowserWebView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.*;

/**
 * Created by takahirom on 16/04/14.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityTest {
    @Rule public final MockWebServer server = new MockWebServer();
    @Rule
    public ActivityTestRule<MainActivity> mainActivity = new ActivityTestRule<MainActivity>(MainActivity.class);

    @Test
    public void webViewClientTest() throws InterruptedException, IOException {
        MockResponse response = new MockResponse().setBody("ABC");

        server.enqueue(response);

        final ZeroBrowserWebView webView = mainActivity.getActivity().binding.inMain.inContent.webview;

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                webView.setWebViewClient(new WebViewClient(){
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        Log.d("MainActivityTest", "finished:"+url);

                        super.onPageFinished(view, url);
                        countDownLatch.countDown();
                    }
                });
                webView.loadUrl(server.url("/").toString());
            }

        });
        if (!countDownLatch.await(3, TimeUnit.SECONDS)) {
            fail("not loaded");
        }
    }
}
