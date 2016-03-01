package com.github.takahirom.zerobrowser.webview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewStub;
import android.webkit.ClientCertRequest;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebHistoryItem;
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.github.takahirom.zerobrowser.R;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

public class WebViewTab implements WebView.PictureListener {

    private WebView webView;

    public WebViewTab(WebView webView, Bundle state) {
        this.webView = webView;
        mCaptureWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.tab_thumbnail_width);
        mCaptureHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.tab_thumbnail_height);
//        updateShouldCaptureThumbnails();
        restoreState(state);
        setWebView(webView);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message m) {
                switch (m.what) {
                    case MSG_CAPTURE:
                        capture();
                        break;
                }
            }
        };
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


    // Log Tag
    private static final String LOGTAG = "Tab";
    private static final boolean LOGD_ENABLED = true;
    // Special case the logtag for messages for the Console to make it easier to
    // filter them and match the logtag used for these messages in older versions
    // of the browser.
    private static final String CONSOLE_LOGTAG = "browser";

    private static final int MSG_CAPTURE = 42;
    private static final int CAPTURE_DELAY = 100;
    private static final int INITIAL_PROGRESS = 5;

    private static Bitmap sDefaultFavicon;

    private static Paint sAlphaPaint = new Paint();
    static {
        sAlphaPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        sAlphaPaint.setColor(Color.TRANSPARENT);
    }

    public enum SecurityState {
        // The page's main resource does not use SSL. Note that we use this
        // state irrespective of the SSL authentication state of sub-resources.
        SECURITY_STATE_NOT_SECURE,
        // The page's main resource uses SSL and the certificate is good. The
        // same is true of all sub-resources.
        SECURITY_STATE_SECURE,
        // The page's main resource uses SSL and the certificate is good, but
        // some sub-resources either do not use SSL or have problems with their
        // certificates.
        SECURITY_STATE_MIXED,
        // The page's main resource uses SSL but there is a problem with its
        // certificate.
        SECURITY_STATE_BAD_CERTIFICATE,
    }

    Context mContext;

    // The tab ID
    private long mId = -1;

    // not not supported
//    // The Geolocation permissions prompt
//    private GeolocationPermissionsPrompt mGeolocationPermissionsPrompt;
//    // The permissions prompt
//    private PermissionsPrompt mPermissionsPrompt;
    // Main WebView wrapper
    private View mContainer;
    // Saved bundle for when we are running low on memory. It contains the
    // information needed to restore the WebView if the user goes back to the
    // tab.
    private Bundle mSavedState;
    // If true, the tab is in the foreground of the current activity.
    private boolean mInForeground;
    // If true, the tab is in page loading state (after onPageStarted,
    // before onPageFinsihed)
    private boolean mInPageLoad;
    private boolean mDisableOverrideUrlLoading;
    // The last reported progress of the current page
    private int mPageLoadProgress;
    // The time the load started, used to find load page time
    private long mLoadStartTime;
    // Application identifier used to find tabs that another application wants
    // to reuse.
    private String mAppId;
    // flag to indicate if tab should be closed on back
    private boolean mCloseOnBack;

    private BrowserSettings mSettings;
    private int mCaptureWidth;
    private int mCaptureHeight;
    private Bitmap mCapture;
    private Handler mHandler;
    private boolean mUpdateThumbnail;

    /**
     * See {@link #clearBackStackWhenItemAdded(String)}.
     */
    private Pattern mClearHistoryUrlPattern;

    private static synchronized Bitmap getDefaultFavicon(Context context) {
        if (sDefaultFavicon == null) {
            sDefaultFavicon = BitmapFactory.decodeResource(
                    context.getResources(), R.drawable.app_web_browser_sm);
        }
        return sDefaultFavicon;
    }

    // All the state needed for a page
    protected static class PageState {
        String mUrl;
        String mOriginalUrl;
        String mTitle;
        SecurityState mSecurityState;
        // This is non-null only when mSecurityState is SECURITY_STATE_BAD_CERTIFICATE.
        SslError mSslCertificateError;
        Bitmap mFavicon;
        boolean mIsBookmarkedSite;
        boolean mIncognito;

        PageState(Context c, boolean incognito) {
            mIncognito = incognito;
            if (mIncognito) {
                mOriginalUrl = mUrl = "browser:incognito";
                mTitle = "incognito tab";
            } else {
                mOriginalUrl = mUrl = "";
                mTitle = "new tab";
            }
            mSecurityState = SecurityState.SECURITY_STATE_NOT_SECURE;
        }

        PageState(Context c, boolean incognito, String url, Bitmap favicon) {
            mIncognito = incognito;
            mOriginalUrl = mUrl = url;
            if (URLUtil.isHttpsUrl(url)) {
                mSecurityState = SecurityState.SECURITY_STATE_SECURE;
            } else {
                mSecurityState = SecurityState.SECURITY_STATE_NOT_SECURE;
            }
            mFavicon = favicon;
        }

    }

    // The current/loading page's state
    protected PageState mCurrentState;

    // Used for saving and restoring each Tab
    static final String ID = "ID";
    static final String CURRURL = "currentUrl";
    static final String CURRTITLE = "currentTitle";
    static final String PARENTTAB = "parentTab";
    static final String APPID = "appid";
    static final String INCOGNITO = "privateBrowsingEnabled";
    static final String USERAGENT = "useragent";
    static final String CLOSEFLAG = "closeOnBack";

    // Container class for the next error dialog that needs to be displayed
    private class ErrorDialog {
        public final int mTitle;
        public final String mDescription;
        public final int mError;
        ErrorDialog(int title, String desc, int error) {
            mTitle = title;
            mDescription = desc;
            mError = error;
        }
    }

    private void processNextError() {
        if (mQueuedErrors == null) {
            return;
        }
        // The first one is currently displayed so just remove it.
        mQueuedErrors.removeFirst();
        if (mQueuedErrors.size() == 0) {
            mQueuedErrors = null;
            return;
        }
        showError(mQueuedErrors.getFirst());
    }

    private DialogInterface.OnDismissListener mDialogListener =
            new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface d) {
                    processNextError();
                }
            };
    private LinkedList<ErrorDialog> mQueuedErrors;

    private void queueError(int err, String desc) {
        if (mQueuedErrors == null) {
            mQueuedErrors = new LinkedList<ErrorDialog>();
        }
        for (ErrorDialog d : mQueuedErrors) {
            if (d.mError == err) {
                // Already saw a similar error, ignore the new one.
                return;
            }
        }
//        ErrorDialog errDialog = new ErrorDialog(
//                err == WebViewClient.ERROR_FILE_NOT_FOUND ?
//                        R.string.browserFrameFileErrorLabel :
//                        R.string.browserFrameNetworkErrorLabel,
//                desc, err);
        Toast.makeText(mContext, desc, Toast.LENGTH_SHORT).show();
//        mQueuedErrors.addLast(errDialog);
//
//        // Show the dialog now if the queue was empty and it is in foreground
//        if (mQueuedErrors.size() == 1 && mInForeground) {
//            showError(errDialog);
//        }
    }

    private void showError(ErrorDialog errDialog) {
        if (mInForeground) {
            AlertDialog d = new AlertDialog.Builder(mContext)
                    .setTitle(errDialog.mTitle)
                    .setMessage(errDialog.mDescription)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            d.setOnDismissListener(mDialogListener);
            d.show();
        }
    }

    // -------------------------------------------------------------------------
    // WebViewClient implementation for the main WebView
    // -------------------------------------------------------------------------

    private final WebViewClient mWebViewClient = new WebViewClient() {
        private Message mDontResend;
        private Message mResend;

        private boolean providersDiffer(String url, String otherUrl) {
            Uri uri1 = Uri.parse(url);
            Uri uri2 = Uri.parse(otherUrl);
            return !uri1.getEncodedAuthority().equals(uri2.getEncodedAuthority());
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            mInPageLoad = true;
            mUpdateThumbnail = true;
            mPageLoadProgress = INITIAL_PROGRESS;
            mCurrentState = new PageState(mContext,
                    view.isPrivateBrowsingEnabled(), url, favicon);
            mLoadStartTime = SystemClock.uptimeMillis();

            // finally update the UI in the activity if it is in the foreground
//            mWebViewController.onPageStarted(Tab.this, view, favicon);

        }

        @Override
        public void onPageFinished(WebView view, String url) {
            mDisableOverrideUrlLoading = false;
            if (!isPrivateBrowsingEnabled()) {
//                LogTag.logPageFinishedLoading(
//                        url, SystemClock.uptimeMillis() - mLoadStartTime);
            }
            syncCurrentState(view, url);
//            mWebViewController.onPageFinished(Tab.this);
        }

        // return true if want to hijack the url to let another app to handle it
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
//            if (!mDisableOverrideUrlLoading && mInForeground) {
//                return mWebViewController.shouldOverrideUrlLoading(Tab.this,
//                        view, url);
//            } else {
            return false;
//            }
        }

        /**
         * Updates the security state. This method is called when we discover
         * another resource to be loaded for this page (for example,
         * javascript). While we update the security state, we do not update
         * the lock icon until we are done loading, as it is slightly more
         * secure this way.
         */
        @Override
        public void onLoadResource(WebView view, String url) {
            if (url != null && url.length() > 0) {
                // It is only if the page claims to be secure that we may have
                // to update the security state:
                if (mCurrentState.mSecurityState == SecurityState.SECURITY_STATE_SECURE) {
                    // If NOT a 'safe' url, change the state to mixed content!
                    if (!(URLUtil.isHttpsUrl(url) || URLUtil.isDataUrl(url)
                            || URLUtil.isAboutUrl(url))) {
                        mCurrentState.mSecurityState = SecurityState.SECURITY_STATE_MIXED;
                    }
                }
            }
        }

        /**
         * Show a dialog informing the user of the network error reported by
         * WebCore if it is in the foreground.
         */
        @Override
        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            if (errorCode != WebViewClient.ERROR_HOST_LOOKUP &&
                    errorCode != WebViewClient.ERROR_CONNECT &&
                    errorCode != WebViewClient.ERROR_BAD_URL &&
                    errorCode != WebViewClient.ERROR_UNSUPPORTED_SCHEME &&
                    errorCode != WebViewClient.ERROR_FILE) {
                queueError(errorCode, description);

                // Don't log URLs when in private browsing mode
                if (!isPrivateBrowsingEnabled()) {
                    Log.e(LOGTAG, "onReceivedError " + errorCode + " " + failingUrl
                            + " " + description);
                }
            }
        }

        /**
         * Check with the user if it is ok to resend POST data as the page they
         * are trying to navigate to is the result of a POST.
         */
        @Override
        public void onFormResubmission(WebView view, final Message dontResend,
                                       final Message resend) {
            if (!mInForeground) {
                dontResend.sendToTarget();
                return;
            }
            if (mDontResend != null) {
                Log.w(LOGTAG, "onFormResubmission should not be called again "
                        + "while dialog is still up");
                dontResend.sendToTarget();
                return;
            }
            mDontResend = dontResend;
            mResend = resend;
            new AlertDialog.Builder(mContext).setTitle(
                    R.string.browserFrameFormResubmitLabel).setMessage(
                    R.string.browserFrameFormResubmitMessage)
                                             .setPositiveButton(R.string.ok,
                                                     new DialogInterface.OnClickListener() {
                                                         public void onClick(DialogInterface dialog,
                                                                             int which) {
                                                             if (mResend != null) {
                                                                 mResend.sendToTarget();
                                                                 mResend = null;
                                                                 mDontResend = null;
                                                             }
                                                         }
                                                     }).setNegativeButton(R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,
                                            int which) {
                            if (mDontResend != null) {
                                mDontResend.sendToTarget();
                                mResend = null;
                                mDontResend = null;
                            }
                        }
                    }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    if (mDontResend != null) {
                        mDontResend.sendToTarget();
                        mResend = null;
                        mDontResend = null;
                    }
                }
            }).show();
        }

        /**
         * Insert the url into the visited history database.
         * @param url The url to be inserted.
         * @param isReload True if this url is being reloaded.
         * FIXME: Not sure what to do when reloading the page.
         */
        @Override
        public void doUpdateVisitedHistory(WebView view, String url,
                                           boolean isReload) {
//            mWebViewController.doUpdateVisitedHistory(Tab.this, isReload);
        }

        /**
         * Displays SSL error(s) dialog to the user.
         */
        @Override
        public void onReceivedSslError(final WebView view,
                                       final SslErrorHandler handler, final SslError error) {
            if (!mInForeground) {
                handler.cancel();
                setSecurityState(SecurityState.SECURITY_STATE_NOT_SECURE);
                return;
            }
            if (mSettings.showSecurityWarnings()) {
                new AlertDialog.Builder(mContext)
                        .setTitle(R.string.security_warning)
                        .setMessage(R.string.ssl_warnings_header)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setPositiveButton(R.string.ssl_continue,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                        handler.proceed();
                                        handleProceededAfterSslError(error);
                                    }
                                })
                        .setNeutralButton(R.string.view_certificate,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int whichButton) {
//                                        mWebViewController.showSslCertificateOnError(
//                                                view, handler, error);
                                    }
                                })
                        .setNegativeButton(R.string.ssl_go_back,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int whichButton) {
                                        dialog.cancel();
                                    }
                                })
                        .setOnCancelListener(
                                new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        handler.cancel();
                                        setSecurityState(SecurityState.SECURITY_STATE_NOT_SECURE);
//                                        mWebViewController.onUserCanceledSsl(Tab.this);
                                    }
                                })
                        .show();
            } else {
                handler.proceed();
                handleProceededAfterSslError(error);
            }
        }

        /**
         * Displays client certificate request to the user.
         */
//        @Override
//        public void onReceivedClientCertRequest(final WebView view,
//                                                final ClientCertRequest request) {
//            if (!mInForeground) {
//                request.ignore();
//                return;
//            }
//            KeyChain.choosePrivateKeyAlias(
//                    webView.getContext(), new KeyChainAliasCallback() {
//                        @Override public void alias(String alias) {
//                            if (alias == null) {
//                                request.cancel();
//                                return;
//                            }
//                            new KeyChainLookup(mContext, request, alias).execute();
//                        }
//                    }, request.getKeyTypes(), request.getPrincipals(), request.getHost(),
//                    request.getPort(), null);
//        }

        /**
         * Handles an HTTP authentication request.
         *
         * @param handler The authentication handler
         * @param host The host
         * @param realm The realm
         */
        @Override
        public void onReceivedHttpAuthRequest(WebView view,
                                              final HttpAuthHandler handler, final String host,
                                              final String realm) {
//            mWebViewController.onReceivedHttpAuthRequest(Tab.this, view, handler, host, realm);
        }

//        @Override
//        public WebResourceResponse shouldInterceptRequest(WebView view,
//                                                          String url) {
//            return HomeProvider.shouldInterceptRequest(mContext, url);
//        }

//        @Override
//        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
//            if (!mInForeground) {
//                return false;
//            }
//            return false;
////            return mWebViewController.shouldOverrideKeyEvent(event);
//        }

        @Override
        public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
            if (!mInForeground) {
                return;
            }
        }

        @Override
        public void onReceivedLoginRequest(WebView view, String realm,
                                           String account, String args) {
//            new DeviceAccountLogin(mWebViewController.getActivity(), view, Tab.this, mWebViewController)
//                    .handleLogin(realm, account, args);
        }

    };

    private void syncCurrentState(WebView view, String url) {
        // Sync state (in case of stop/timeout)
        mCurrentState.mUrl = view.getUrl();
        if (mCurrentState.mUrl == null) {
            mCurrentState.mUrl = "";
        }
        mCurrentState.mOriginalUrl = view.getOriginalUrl();
        mCurrentState.mTitle = view.getTitle();
        mCurrentState.mFavicon = view.getFavicon();
        if (!URLUtil.isHttpsUrl(mCurrentState.mUrl)) {
            // In case we stop when loading an HTTPS page from an HTTP page
            // but before a provisional load occurred
            mCurrentState.mSecurityState = SecurityState.SECURITY_STATE_NOT_SECURE;
            mCurrentState.mSslCertificateError = null;
        }
        mCurrentState.mIncognito = view.isPrivateBrowsingEnabled();
    }


    // -------------------------------------------------------------------------
    // WebChromeClient implementation for the main WebView
    // -------------------------------------------------------------------------

    private final WebChromeClient mWebChromeClient = new WebChromeClient() {
        // Helper method to create a new tab or sub window.
        private void createWindow(final boolean dialog, final Message msg) {
            WebView.WebViewTransport transport =
                    (WebView.WebViewTransport) msg.obj;
            transport.setWebView(webView);
//            if (dialog) {
//                createSubWindow();
//                mWebViewController.attachSubWindow(Tab.this);
//            transport.setWebView(mSubView);
//            } else {
//                final Tab newTab = mWebViewController.openTab(null,
//                        Tab.this, true, true);
//                transport.setWebView(newTab.getWebView());
//            }
//            msg.sendToTarget();
        }

        @Override
        public boolean onCreateWindow(WebView view, final boolean dialog,
                                      final boolean userGesture, final Message resultMsg) {
            // only allow new window or sub window for the foreground case
            if (!mInForeground) {
                return false;
            }
//            // Short-circuit if we can't create any more tabs or sub windows.
//            if (dialog && mSubView != null) {
//                new AlertDialog.Builder(mContext)
//                        .setTitle(R.string.too_many_subwindows_dialog_title)
//                        .setIconAttribute(android.R.attr.alertDialogIcon)
//                        .setMessage(R.string.too_many_subwindows_dialog_message)
//                        .setPositiveButton(R.string.ok, null)
//                        .show();
//                return false;
//            } else if (!mWebViewController.getTabControl().canCreateNewTab()) {
//                new AlertDialog.Builder(mContext)
//                        .setTitle(R.string.too_many_windows_dialog_title)
//                        .setIconAttribute(android.R.attr.alertDialogIcon)
//                        .setMessage(R.string.too_many_windows_dialog_message)
//                        .setPositiveButton(R.string.ok, null)
//                        .show();
//                return false;
//            }

            // Short-circuit if this was a user gesture.
            if (userGesture) {
                createWindow(dialog, resultMsg);
                return true;
            }

            // Allow the popup and create the appropriate window.
            final AlertDialog.OnClickListener allowListener =
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface d,
                                            int which) {
                            createWindow(dialog, resultMsg);
                        }
                    };

            // Block the popup by returning a null WebView.
            final AlertDialog.OnClickListener blockListener =
                    new AlertDialog.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            resultMsg.sendToTarget();
                        }
                    };

            // Build a confirmation dialog to display to the user.
            final AlertDialog d =
                    new AlertDialog.Builder(mContext)
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setMessage(R.string.popup_window_attempt)
                            .setPositiveButton(R.string.allow, allowListener)
                            .setNegativeButton(R.string.block, blockListener)
                            .setCancelable(false)
                            .create();

            // Show the confirmation dialog.
            d.show();
            return true;
        }

        @Override
        public void onRequestFocus(WebView view) {
            if (!mInForeground) {
//                mWebViewController.switchToTab(Tab.this);
            }
        }

        @Override
        public void onCloseWindow(WebView window) {
//            if (mParent != null) {
//                // JavaScript can only close popup window.
//                if (mInForeground) {
////                    mWebViewController.switchToTab(mParent);
//                }
//
//            }
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message,
                                 JsResult result) {
            return false;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message,
                                   JsResult result) {
            return false;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message,
                                  String defaultValue, JsPromptResult result) {
            return false;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            mPageLoadProgress = newProgress;
            if (newProgress == 100) {
                mInPageLoad = false;
            }
            if (mUpdateThumbnail && newProgress == 100) {
                mUpdateThumbnail = false;
            }
        }

        @Override
        public void onReceivedTitle(WebView view, final String title) {
            mCurrentState.mTitle = title;
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            mCurrentState.mFavicon = icon;
        }

        @Override
        public void onReceivedTouchIconUrl(WebView view, String url,
                                           boolean precomposed) {
            final ContentResolver cr = mContext.getContentResolver();
//            // Let precomposed icons take precedence over non-composed
//            // icons.
//            if (precomposed && mTouchIconLoader != null) {
//                mTouchIconLoader.cancel(false);
//                mTouchIconLoader = null;
//            }
//            // Have only one async task at a time.
//            if (mTouchIconLoader == null) {
//                mTouchIconLoader = new DownloadTouchIcon(Tab.this, cr, view);
//                mTouchIconLoader.execute(url);
//            }
        }

        @Override
        public void onShowCustomView(View view,
                                     WebChromeClient.CustomViewCallback callback) {
//            Activity activity = mWebViewController.getActivity();
//            if (activity != null) {
//                onShowCustomView(view, activity.getRequestedOrientation(), callback);
//            }
        }

        @Override
        public void onShowCustomView(View view, int requestedOrientation,
                                     WebChromeClient.CustomViewCallback callback) {

//                    requestedOrientation, callback);
        }

        @Override
        public void onHideCustomView() {

        }

        /**
         * The origin has exceeded its database quota.
         * @param url the URL that exceeded the quota
         * @param databaseIdentifier the identifier of the database on which the
         *            transaction that caused the quota overflow was run
         * @param currentQuota the current quota for the origin.
         * @param estimatedSize the estimated size of the database.
         * @param totalUsedQuota is the sum of all origins' quota.
         * @param quotaUpdater The callback to run when a decision to allow or
         *            deny quota has been made. Don't forget to call this!
         */
        @Override
        public void onExceededDatabaseQuota(String url,
                                            String databaseIdentifier, long currentQuota, long estimatedSize,
                                            long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
            mSettings.getWebStorageSizeManager()
                     .onExceededDatabaseQuota(url, databaseIdentifier,
                             currentQuota, estimatedSize, totalUsedQuota,
                             quotaUpdater);
        }

        /**
         * The Application Cache has exceeded its max size.
         * @param spaceNeeded is the amount of disk space that would be needed
         *            in order for the last appcache operation to succeed.
         * @param totalUsedQuota is the sum of all origins' quota.
         * @param quotaUpdater A callback to inform the WebCore thread that a
         *            new app cache size is available. This callback must always
         *            be executed at some point to ensure that the sleeping
         *            WebCore thread is woken up.
         */
        @Override
        public void onReachedMaxAppCacheSize(long spaceNeeded,
                                             long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
            mSettings.getWebStorageSizeManager()
                     .onReachedMaxAppCacheSize(spaceNeeded, totalUsedQuota,
                             quotaUpdater);
        }

        /**
         * Instructs the browser to show a prompt to ask the user to set the
         * Geolocation permission state for the specified origin.
         * @param origin The origin for which Geolocation permissions are
         *     requested.
         * @param callback The callback to call once the user has set the
         *     Geolocation permission state.
         */
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                                                       GeolocationPermissions.Callback callback) {
            if (mInForeground) {
//                getGeolocationPermissionsPrompt().show(origin, callback);
            }
        }

        /**
         * Instructs the browser to hide the Geolocation permissions prompt.
         */
        @Override
        public void onGeolocationPermissionsHidePrompt() {
//            if (mInForeground && mGeolocationPermissionsPrompt != null) {
//                mGeolocationPermissionsPrompt.hide();
//            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            if (!mInForeground) return;
//            getPermissionsPrompt().show(request);
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
//            if (mInForeground && mPermissionsPrompt != null) {
//                mPermissionsPrompt.hide();
//            }
        }

        /* Adds a JavaScript error message to the system log and if the JS
         * console is enabled in the about:debug options, to that console
         * also.
         * @param consoleMessage the message object.
         */
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {

            // Don't log console messages in private browsing mode
            if (isPrivateBrowsingEnabled()) return true;

            String message = "Console: " + consoleMessage.message() + " "
                    + consoleMessage.sourceId() +  ":"
                    + consoleMessage.lineNumber();

            switch (consoleMessage.messageLevel()) {
                case TIP:
                    Log.v(CONSOLE_LOGTAG, message);
                    break;
                case LOG:
                    Log.i(CONSOLE_LOGTAG, message);
                    break;
                case WARNING:
                    Log.w(CONSOLE_LOGTAG, message);
                    break;
                case ERROR:
                    Log.e(CONSOLE_LOGTAG, message);
                    break;
                case DEBUG:
                    Log.d(CONSOLE_LOGTAG, message);
                    break;
            }

            return true;
        }

        /**
         * Ask the browser for an icon to represent a <video> element.
         * This icon will be used if the Web page did not specify a poster attribute.
         * @return Bitmap The icon or null if no such icon is available.
         */
        @Override
        public Bitmap getDefaultVideoPoster() {
            if (mInForeground) {

            }
            return null;
        }

        /**
         * Ask the host application for a custom progress view to show while
         * a <video> is loading.
         * @return View The progress view.
         */
        @Override
        public View getVideoLoadingProgressView() {
            if (mInForeground) {

            }
            return null;
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (mInForeground) {
//                mWebViewController.showFileChooser(callback, params);
                return true;
            } else {
                return false;
            }
        }

        /**
         * Deliver a list of already-visited URLs
         */
        @Override
        public void getVisitedHistory(final ValueCallback<String[]> callback) {

        }

    };

    public boolean shouldUpdateThumbnail() {
        return mUpdateThumbnail;
    }
//
//    /**
//     * This is used to get a new ID when the tab has been preloaded, before it is displayed and
//     * added to TabControl. Preloaded tabs can be created before restoreInstanceState, leading
//     * to overlapping IDs between the preloaded and restored tabs.
//     */
//    public void refreshIdAfterPreload() {
//        mId = TabControl.getNextId();
//    }
//
//    public void updateShouldCaptureThumbnails() {
//        if (mWebViewController.shouldCaptureThumbnails()) {
//            synchronized (Tab.this) {
//                if (mCapture == null) {
//                    mCapture = Bitmap.createBitmap(mCaptureWidth, mCaptureHeight,
//                            Bitmap.Config.RGB_565);
//                    mCapture.eraseColor(Color.WHITE);
//                    if (mInForeground) {
//                        postCapture();
//                    }
//                }
//            }
//        } else {
//            synchronized (Tab.this) {
//                mCapture = null;
//                deleteThumbnail();
//            }
//        }
//    }

    public long getId() {
        return mId;
    }

    void setWebView(WebView w) {
        setWebView(w, true);
    }

    /**
     * Sets the WebView for this tab, correctly removing the old WebView from
     * the container view.
     */
    void setWebView(WebView w, boolean restore) {
        if (webView == w) {
            return;
        }

        // If the WebView is changing, the page will be reloaded, so any ongoing
        // Geolocation permission requests are void.
//        if (mGeolocationPermissionsPrompt != null) {
//            mGeolocationPermissionsPrompt.hide();
//        }

//        if (mPermissionsPrompt != null) {
//            mPermissionsPrompt.hide();
//        }

//        mWebViewController.onSetWebView(this, w);

        if (webView != null) {
            webView.setPictureListener(null);
            if (w != null) {
                syncCurrentState(w, null);
            } else {
                mCurrentState = new PageState(mContext, false);
            }
        }
        // set the new one
        webView = w;
        // attach the WebViewClient, WebChromeClient and DownloadListener
        if (webView != null) {
            webView.setWebViewClient(mWebViewClient);
            webView.setWebChromeClient(mWebChromeClient);
            // Attach DownloadManager so that downloads can start in an active
            // or a non-active window. This can happen when going to a site that
            // does a redirect after a period of time. The user could have
            // switched to another tab while waiting for the download to start.
//            webView.setDownloadListener(mDownloadListener);
//            TabControl tc = mWebViewController.getTabControl();
//            if (tc != null && tc.getOnThumbnailUpdatedListener() != null) {
                webView.setPictureListener(this);
//            }
            if (restore && (mSavedState != null)) {
                restoreUserAgent();
                WebBackForwardList restoredState
                        = webView.restoreState(mSavedState);
                if (restoredState == null || restoredState.getSize() == 0) {
                    Log.w(LOGTAG, "Failed to restore WebView state!");
                    loadUrl(mCurrentState.mOriginalUrl, null);
                }
                mSavedState = null;
            }
        }
    }

    /**
     * Destroy the tab's main WebView and subWindow if any
     */
    void destroy() {
        if (webView != null) {
            WebView webView = getWebView();
            setWebView(null);
            webView.destroy();
        }
    }


    void resume() {
        if (webView != null) {
            setupHwAcceleration(webView);
            webView.onResume();
        }
    }

    private void setupHwAcceleration(View web) {
        if (web == null) return;
        BrowserSettings settings = BrowserSettings.getInstance();
        if (settings.isHardwareAccelerated()) {
            web.setLayerType(View.LAYER_TYPE_NONE, null);
        } else {
            web.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    void pause() {
        if (webView != null) {
            webView.onPause();
        }
    }

    void putInForeground() {
        if (mInForeground) {
            return;
        }
        mInForeground = true;
        resume();
//        Activity activity = mWebViewController.getActivity();
        webView.setOnCreateContextMenuListener(((View.OnCreateContextMenuListener) webView.getContext()));
//        if (mSubView != null) {
//            mSubView.setOnCreateContextMenuListener(activity);
//        }
        // Show the pending error dialog if the queue is not empty
        if (mQueuedErrors != null && mQueuedErrors.size() >  0) {
            showError(mQueuedErrors.getFirst());
        }
//        mWebViewController.bookmarkedStatusHasChanged(this);
    }

    void putInBackground() {
        if (!mInForeground) {
            return;
        }
        capture();
        mInForeground = false;
        pause();
        webView.setOnCreateContextMenuListener(null);
//        if (mSubView != null) {
//            mSubView.setOnCreateContextMenuListener(null);
//        }
    }

    boolean inForeground() {
        return mInForeground;
    }

    /**
     * Return the top window of this tab; either the subwindow if it is not
     * null or the main window.
     * @return The top window of this tab.
     */
    WebView getTopWindow() {
//        if (mSubView != null) {
//            return mSubView;
//        }
        return webView;
    }

    /**
     * Return the main window of this tab. Note: if a tab is freed in the
     * background, this can return null. It is only guaranteed to be
     * non-null for the current tab.
     * @return The main WebView of this tab.
     */
    WebView getWebView() {
        return webView;
    }

    void setViewContainer(View container) {
        mContainer = container;
    }

    View getViewContainer() {
        return mContainer;
    }

    /**
     * Return whether private browsing is enabled for the main window of
     * this tab.
     * @return True if private browsing is enabled.
     */
    boolean isPrivateBrowsingEnabled() {
        return mCurrentState.mIncognito;
    }

    /**
     * @return The geolocation permissions prompt for this tab.
     */
//    GeolocationPermissionsPrompt getGeolocationPermissionsPrompt() {
//        if (mGeolocationPermissionsPrompt == null) {
//            ViewStub stub = (ViewStub) mContainer
//                    .findViewById(R.id.geolocation_permissions_prompt);
//            mGeolocationPermissionsPrompt = (GeolocationPermissionsPrompt) stub
//                    .inflate();
//        }
//        return mGeolocationPermissionsPrompt;
//    }
//
//    /**
//     * @return The permissions prompt for this tab.
//     */
//    PermissionsPrompt getPermissionsPrompt() {
//        if (mPermissionsPrompt == null) {
//            ViewStub stub = (ViewStub) mContainer
//                    .findViewById(R.id.permissions_prompt);
//            mPermissionsPrompt = (PermissionsPrompt) stub.inflate();
//        }
//        return mPermissionsPrompt;
//    }

    /**
     * @return The application id string
     */
    String getAppId() {
        return mAppId;
    }

    /**
     * Set the application id string
     * @param id
     */
    void setAppId(String id) {
        mAppId = id;
    }

    boolean closeOnBack() {
        return mCloseOnBack;
    }

    void setCloseOnBack(boolean close) {
        mCloseOnBack = close;
    }

//    String getUrl() {
//        return UrlUtils.filteredUrl(mCurrentState.mUrl);
//    }
//
//    String getOriginalUrl() {
//        if (mCurrentState.mOriginalUrl == null) {
//            return getUrl();
//        }
//        return UrlUtils.filteredUrl(mCurrentState.mOriginalUrl);
//    }

    /**
     * Get the title of this tab.
     */
    String getTitle() {
        if (mCurrentState.mTitle == null && mInPageLoad) {
            return mContext.getString(R.string.title_bar_loading);
        }
        return mCurrentState.mTitle;
    }

    /**
     * Get the favicon of this tab.
     */
    Bitmap getFavicon() {
        if (mCurrentState.mFavicon != null) {
            return mCurrentState.mFavicon;
        }
        return getDefaultFavicon(mContext);
    }

    public boolean isBookmarkedSite() {
        return mCurrentState.mIsBookmarkedSite;
    }


    /**
     * Sets the security state, clears the SSL certificate error and informs
     * the controller.
     */
    private void setSecurityState(SecurityState securityState) {
        mCurrentState.mSecurityState = securityState;
        mCurrentState.mSslCertificateError = null;
//        mWebViewController.onUpdatedSecurityState(this);
    }

    /**
     * @return The tab's security state.
     */
    SecurityState getSecurityState() {
        return mCurrentState.mSecurityState;
    }

    /**
     * Gets the SSL certificate error, if any, for the page's main resource.
     * This is only non-null when the security state is
     * SECURITY_STATE_BAD_CERTIFICATE.
     */
    SslError getSslCertificateError() {
        return mCurrentState.mSslCertificateError;
    }

    int getLoadProgress() {
        if (mInPageLoad) {
            return mPageLoadProgress;
        }
        return 100;
    }

    /**
     * @return TRUE if onPageStarted is called while onPageFinished is not
     *         called yet.
     */
    boolean inPageLoad() {
        return mInPageLoad;
    }

    /**
     * @return The Bundle with the tab's state if it can be saved, otherwise null
     */
    public Bundle saveState() {
        // If the WebView is null it means we ran low on memory and we already
        // stored the saved state in mSavedState.
        if (webView == null) {
            return mSavedState;
        }

        if (TextUtils.isEmpty(mCurrentState.mUrl)) {
            return null;
        }

        mSavedState = new Bundle();
        WebBackForwardList savedList = webView.saveState(mSavedState);
        if (savedList == null || savedList.getSize() == 0) {
            Log.w(LOGTAG, "Failed to save back/forward list for "
                    + mCurrentState.mUrl);
        }

        mSavedState.putLong(ID, mId);
        mSavedState.putString(CURRURL, mCurrentState.mUrl);
        mSavedState.putString(CURRTITLE, mCurrentState.mTitle);
        mSavedState.putBoolean(INCOGNITO, webView.isPrivateBrowsingEnabled());
        if (mAppId != null) {
            mSavedState.putString(APPID, mAppId);
        }
        mSavedState.putBoolean(CLOSEFLAG, mCloseOnBack);
        // Remember the parent tab so the relationship can be restored.
        mSavedState.putBoolean(USERAGENT,
                mSettings.hasDesktopUseragent(getWebView()));
        return mSavedState;
    }

    /*
     * Restore the state of the tab.
     */
    private void restoreState(Bundle b) {
        mSavedState = b;
        if (mSavedState == null) {
            return;
        }
        // Restore the internal state even if the WebView fails to restore.
        // This will maintain the app id, original url and close-on-exit values.
        mId = b.getLong(ID);
        mAppId = b.getString(APPID);
        mCloseOnBack = b.getBoolean(CLOSEFLAG);
        restoreUserAgent();
        String url = b.getString(CURRURL);
        String title = b.getString(CURRTITLE);
        boolean incognito = b.getBoolean(INCOGNITO);
        mCurrentState = new PageState(mContext, incognito, url, null);
        mCurrentState.mTitle = title;
    }

    private void restoreUserAgent() {
        if (webView == null || mSavedState == null) {
            return;
        }
        if (mSavedState.getBoolean(USERAGENT)
                != mSettings.hasDesktopUseragent(webView)) {
            mSettings.toggleDesktopUseragent(webView);
        }
    }

    public Bitmap getScreenshot() {
        return mCapture;
    }

    public boolean isSnapshot() {
        return false;
    }

    private static class SaveCallback implements ValueCallback<Boolean> {
        boolean mResult;

        @Override
        public void onReceiveValue(Boolean value) {
            mResult = value;
            synchronized (this) {
                notifyAll();
            }
        }

    }

    /**
     * Must be called on the UI thread
     */
    public ContentValues createSnapshotValues() {
        return null;
    }

    /**
     * Probably want to call this on a background thread
     */
    public boolean saveViewState(ContentValues values) {
        return false;
    }

    public byte[] compressBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    public void loadUrl(String url, Map<String, String> headers) {
        if (webView != null) {
            mPageLoadProgress = INITIAL_PROGRESS;
            mInPageLoad = true;
            mCurrentState = new PageState(mContext, false, url, null);
//            mWebViewController.onPageStarted(this, webView, null);
            webView.loadUrl(url, headers);
        }
    }

    public void disableUrlOverridingForLoad() {
        mDisableOverrideUrlLoading = true;
    }

    protected void capture() {
        if (webView == null || mCapture == null) return;
        // https://code.google.com/p/android/issues/detail?id=202016&sort=-opened&colspec=ID%20Status%20Priority%20Owner%20Summary%20Stars%20Reporter%20Opened
        if (/*webView.getContentWidth() <= 0 || */webView.getContentHeight() <= 0) {
            return;
        }
        Canvas c = new Canvas(mCapture);
        final int left = webView.getScrollX();
        final int top = webView.getScrollY();// + webView.getVisibleTitleHeight();
        int state = c.save();
        c.translate(-left, -top);
        float scale = mCaptureWidth / (float) webView.getWidth();
        c.scale(scale, scale, left, top);
        webView.draw(c);
        c.restoreToCount(state);
        // manually anti-alias the edges for the tilt
        c.drawRect(0, 0, 1, mCapture.getHeight(), sAlphaPaint);
        c.drawRect(mCapture.getWidth() - 1, 0, mCapture.getWidth(),
                mCapture.getHeight(), sAlphaPaint);
        c.drawRect(0, 0, mCapture.getWidth(), 1, sAlphaPaint);
        c.drawRect(0, mCapture.getHeight() - 1, mCapture.getWidth(),
                mCapture.getHeight(), sAlphaPaint);
        c.setBitmap(null);
        mHandler.removeMessages(MSG_CAPTURE);
//        persistThumbnail();
//        TabControl tc = mWebViewController.getTabControl();
//        if (tc != null) {
//            OnThumbnailUpdatedListener updateListener
//                    = tc.getOnThumbnailUpdatedListener();
//            if (updateListener != null) {
//                updateListener.onThumbnailUpdated(this);
//            }
//        }
    }

    @Override
    public void onNewPicture(WebView view, Picture picture) {
        postCapture();
    }

    private void postCapture() {
        if (!mHandler.hasMessages(MSG_CAPTURE)) {
            mHandler.sendEmptyMessageDelayed(MSG_CAPTURE, CAPTURE_DELAY);
        }
    }

    public boolean canGoBack() {
        return webView != null ? webView.canGoBack() : false;
    }

    public boolean canGoForward() {
        return webView != null ? webView.canGoForward() : false;
    }

    public void goBack() {
        if (webView != null) {
            webView.goBack();
        }
    }

    public void goForward() {
        if (webView != null) {
            webView.goForward();
        }
    }

//    /**
//     * Causes the tab back/forward stack to be cleared once, if the given URL is the next URL
//     * to be added to the stack.
//     *
//     * This is used to ensure that preloaded URLs that are not subsequently seen by the user do
//     * not appear in the back stack.
//     */
//    public void clearBackStackWhenItemAdded(Pattern urlPattern) {
//        mClearHistoryUrlPattern = urlPattern;
//    }
//
//    protected void persistThumbnail() {
//        DataController.getInstance(mContext).saveThumbnail(this);
//    }
//
//    protected void deleteThumbnail() {
//        DataController.getInstance(mContext).deleteThumbnail(this);
//    }
//
//    void updateCaptureFromBlob(byte[] blob) {
//        synchronized (Tab.this) {
//            if (mCapture == null) {
//                return;
//            }
//            ByteBuffer buffer = ByteBuffer.wrap(blob);
//            try {
//                mCapture.copyPixelsFromBuffer(buffer);
//            } catch (RuntimeException rex) {
//                Log.e(LOGTAG, "Load capture has mismatched sizes; buffer: "
//                        + buffer.capacity() + " blob: " + blob.length
//                        + "capture: " + mCapture.getByteCount());
//                throw rex;
//            }
//        }
//    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(100);
        builder.append(mId);
        builder.append(") has parent: ");
        builder.append("false");
        builder.append(", incog: ");
        builder.append(isPrivateBrowsingEnabled());
        if (!isPrivateBrowsingEnabled()) {
            builder.append(", title: ");
            builder.append(getTitle());
            builder.append(", url: ");
            builder.append(getWebView().getUrl());
        }
        return builder.toString();
    }

    private void handleProceededAfterSslError(SslError error) {
        if (error.getUrl().equals(mCurrentState.mUrl)) {
            // The security state should currently be SECURITY_STATE_SECURE.
            setSecurityState(SecurityState.SECURITY_STATE_BAD_CERTIFICATE);
            mCurrentState.mSslCertificateError = error;
        } else if (getSecurityState() == SecurityState.SECURITY_STATE_SECURE) {
            // The page's main resource is secure and this error is for a
            // sub-resource.
            setSecurityState(SecurityState.SECURITY_STATE_MIXED);
        }
    }

    public void setAcceptThirdPartyCookies(boolean accept) {
        CookieManager cookieManager = CookieManager.getInstance();
        if (webView != null)
            cookieManager.setAcceptThirdPartyCookies(webView, accept);
    }
}