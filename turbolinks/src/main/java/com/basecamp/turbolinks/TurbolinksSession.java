package com.basecamp.turbolinks;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Date;
import java.util.HashMap;

/**
 * <p>The main concrete class to use Turbolinks 5 in your app.</p>
 */
public class TurbolinksSession {

    // ---------------------------------------------------
    // Package public vars (allows for greater flexibility and access for testing)
    // ---------------------------------------------------

    boolean turbolinksBridgeInjected; // Script injected into DOM
    boolean coldBootInProgress;
    boolean restoreWithCachedSnapshot;
    boolean turbolinksIsReady; // Script finished and TL fully instantiated
    int progressIndicatorDelay;
    long previousOverrideTime;
    Activity activity;
    HashMap<String, Object> javascriptInterfaces = new HashMap<>();
    HashMap<String, String> restorationIdentifierMap = new HashMap<>();
    String location;
    String currentVisitIdentifier;
    TurbolinksAdapter turbolinksAdapter;
    TurbolinksView turbolinksView;
    View progressView;
    View progressIndicator;

    static volatile TurbolinksSession defaultInstance;

    // ---------------------------------------------------
    // Final vars
    // ---------------------------------------------------

    static final String ACTION_ADVANCE = "advance";
    static final String ACTION_RESTORE = "restore";
    static final String JAVASCRIPT_INTERFACE_NAME = "TurbolinksNative";
    static final int PROGRESS_INDICATOR_DELAY = 500;

    final Context applicationContext;
    final WebView webView;

    // ---------------------------------------------------
    // Constructor
    // ---------------------------------------------------

    /**
     * Private constructor called to return a new Turbolinks instance.
     *
     * @param context Any Android context.
     */
    private TurbolinksSession(final Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }

        this.applicationContext = context.getApplicationContext();
        this.webView = TurbolinksHelper.createWebView(applicationContext);
        this.webView.addJavascriptInterface(this, JAVASCRIPT_INTERFACE_NAME);
        this.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                coldBootInProgress = true;
            }

            @Override
            public void onPageFinished(WebView view, String location) {
                if (!turbolinksBridgeInjected) {
                    TurbolinksHelper.injectTurbolinksBridge(TurbolinksSession.this, applicationContext, webView);
                    turbolinksAdapter.onPageFinished();

                    TurbolinksLog.d("Page finished: " + location);
                }
            }

            /**
             * Turbolinks will not call adapter.visitProposedToLocationWithAction in some cases,
             * like target=_blank or when the domain doesn't match. We still route those here.
             * This is mainly only called when links within a webView are clicked and not during
             * loadUrl. However, a redirect on a cold boot can also cause this to fire, so don't
             * override in that situation, since Turbolinks is not yet ready.
             * http://stackoverflow.com/a/6739042/3280911
             */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String location) {
                if (!turbolinksIsReady || coldBootInProgress) {
                    return false;
                }

                /**
                 * Prevents firing twice in a row within a few milliseconds of each other, which
                 * happens. So we check for a slight delay between requests, which is plenty of time
                 * to allow for a user to click the same link again.
                 */
                long currentOverrideTime = new Date().getTime();
                if ((currentOverrideTime - previousOverrideTime) > 500) {
                    previousOverrideTime = currentOverrideTime;
                    TurbolinksLog.d("Overriding load: " + location);
                    visitProposedToLocationWithAction(location, ACTION_ADVANCE, null);
                }

                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                resetToColdBoot();

                turbolinksAdapter.onReceivedError(errorCode);
                TurbolinksLog.d("onReceivedError: " + errorCode);
            }

            @Override
            @TargetApi(Build.VERSION_CODES.M)
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);

                if (request.isForMainFrame()) {
                    resetToColdBoot();
                    turbolinksAdapter.onReceivedError(errorResponse.getStatusCode());
                    TurbolinksLog.d("onReceivedHttpError: " + errorResponse.getStatusCode());
                }
            }
        });
    }

    // ---------------------------------------------------
    // Initialization
    // ---------------------------------------------------

    /**
     * Creates a brand new TurbolinksSession that the calling application will be responsible for
     * managing.
     *
     * @param context Any Android context.
     * @return TurbolinksSession to be managed by the calling application.
     */
    public static TurbolinksSession getNew(Context context) {
        TurbolinksLog.d("TurbolinksSession getNew called");

        return new TurbolinksSession(context);
    }

    /**
     * Convenience method that returns a default TurbolinksSession. This is useful for when an
     * app only needs one instance of a TurbolinksSession.
     *
     * @param context Any Android context.
     * @return The default, static instance of a TurbolinksSession, guaranteed to not be null.
     */
    public static TurbolinksSession getDefault(Context context) {
        if (defaultInstance == null) {
            synchronized (TurbolinksSession.class) {
                if (defaultInstance == null) {
                    TurbolinksLog.d("Default instance is null, creating new");
                    defaultInstance = TurbolinksSession.getNew(context);
                }
            }
        }

        return defaultInstance;
    }

    /**
     * Resets the default TurbolinksSession instance to null in case you want a fresh session.
     */
    public static void resetDefault() {
        defaultInstance = null;
    }

    // ---------------------------------------------------
    // Required chained methods
    // ---------------------------------------------------

    /**
     * <p><b>REQUIRED</b> Turbolinks requires a context for a variety of uses, and for maximum clarity
     * we ask for an Activity context instead of a generic one. (On occassion, we've run into WebView
     * bugs where an Activity is the only fix).</p>
     *
     * <p>It's best to pass a new activity to Turbolinks for each new visit for clarity. This ensures
     * there is a one-to-one relationship maintained between internal activity IDs and visit IDs.</p>
     *
     * @param activity An Android Activity, one per visit.
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession activity(Activity activity) {
        this.activity = activity;

        Context webViewContext = webView.getContext();
        if (webViewContext instanceof MutableContextWrapper) {
            ((MutableContextWrapper) webViewContext).setBaseContext(this.activity);
        }

        return this;
    }

    /**
     * <p><b>REQUIRED</b> A {@link TurbolinksAdapter} implementation is required so that callbacks
     * during the Turbolinks event lifecycle can be passed back to your app.</p>
     *
     * @param turbolinksAdapter Any class that implements {@link TurbolinksAdapter}.
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession adapter(TurbolinksAdapter turbolinksAdapter) {
        this.turbolinksAdapter = turbolinksAdapter;
        return this;
    }

    /**
     * <p><b>REQUIRED</b> A {@link TurbolinksView} object that's been inflated in a custom layout is
     * required so the library can manage various view-related tasks: attaching/detaching the
     * internal webView, showing/hiding a progress loading view, etc.</p>
     *
     * @param turbolinksView An inflated TurbolinksView from your custom layout.
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession view(TurbolinksView turbolinksView) {
        this.turbolinksView = turbolinksView;
        this.turbolinksView.attachWebView(webView);

        return this;
    }

    /**
     * <p><b>REQUIRED</b> Executes a Turbolinks visit. Must be called at the end of the chain --
     * all required parameters will first be validated before firing.</p>
     *
     * @param location The URL to visit.
     */
    public void visit(String location) {
        TurbolinksLog.d("visit called");

        this.location = location;

        validateRequiredParams();
        initProgressView();

        if (turbolinksIsReady) {
            visitCurrentLocationWithTurbolinks();
        }

        if (!turbolinksIsReady && !coldBootInProgress) {
            TurbolinksLog.d("Cold booting: " + location);
            webView.loadUrl(location);
        }

        // Reset so that cached snapshot is not the default for the next visit
        restoreWithCachedSnapshot = false;

        /*
        if (!turbolinksIsReady && coldBootInProgress), we don't fire a new visit. This is
        typically a slow connection load. This allows the previous cold boot to finish (inject TL).
        No matter what, if new requests are sent to Turbolinks via Turbolinks.location, we'll
        always have the last desired location. And when setTurbolinksIsReady(true) is called,
        we open that last location.
        */
    }

    // ---------------------------------------------------
    // Optional chained methods
    // ---------------------------------------------------

    /**
     * <p><b>Optional</b> This will override the default progress view/progress indicator that's provided
     * out of the box. This allows you to customize how you want the progress view to look while
     * pages are loading.</p>
     *
     * @param progressView           A custom progressView object.
     * @param progressIndicatorResId The resource ID of a progressIndicator object inside the progressView.
     * @param progressIndicatorDelay The delay, in milliseconds, before the progress indicator should be displayed
     *                               inside the progress view (default is 500 ms).
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession progressView(View progressView, int progressIndicatorResId, int progressIndicatorDelay) {
        this.progressView = progressView;
        this.progressIndicator = progressView.findViewById(progressIndicatorResId);
        this.progressIndicatorDelay = progressIndicatorDelay;

        if (this.progressIndicator == null) {
            throw new IllegalArgumentException("A progress indicator view must be provided in your custom progressView.");
        }

        return this;
    }

    /**
     * <p><b>Optional</b> By default Turbolinks will "advance" to the next page and scroll position
     * will not be restored. Optionally calling this method allows you to set the behavior on a
     * per-visitbasis. This will be reset to "false" after each visit.</p>
     *
     * @param restoreWithCachedSnapshot If true, will restore scroll position. If false, will not restore
     *                                  scroll position.
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession restoreWithCachedSnapshot(boolean restoreWithCachedSnapshot) {
        this.restoreWithCachedSnapshot = restoreWithCachedSnapshot;
        return this;
    }

    // ---------------------------------------------------
    // TurbolinksNative adapter methods
    // ---------------------------------------------------

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when a new visit is initiated from a
     * webView link.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param location URL to be visited.
     * @param action   Whether to treat the request as an advance (navigating forward) or a replace (back).
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitProposedToLocationWithAction(String location, String action, String target) {
        TurbolinksLog.d("visitProposedToLocationWithAction called");

        turbolinksAdapter.visitProposedToLocationWithAction(location, action, target);
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when a new visit has just started.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier        A unique identifier for the visit.
     * @param visitHasCachedSnapshot Whether the visit has a cached snapshot available.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitStarted(String visitIdentifier, boolean visitHasCachedSnapshot) {
        TurbolinksLog.d("visitStarted called");

        currentVisitIdentifier = visitIdentifier;

        runJavascript("webView.changeHistoryForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.issueRequestForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.loadCachedSnapshotForVisitWithIdentifier", visitIdentifier);
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the HTTP request has been
     * completed.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRequestCompleted(String visitIdentifier) {
        TurbolinksLog.d("visitRequestCompleted called");

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            runJavascript("webView.loadResponseForVisitWithIdentifier", visitIdentifier);
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the HTTP request has failed.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     * @param statusCode      The HTTP status code that caused the failure.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRequestFailedWithStatusCode(final String visitIdentifier, final int statusCode) {
        TurbolinksLog.d("visitRequestFailedWithStatusCode called");

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
                @Override
                public void run() {
                    turbolinksAdapter.requestFailedWithStatusCode(statusCode);
                }
            });
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks once the page has been fully rendered
     * in the webView.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRendered(String visitIdentifier) {
        TurbolinksLog.d("visitRendered called, hiding progress view for identifier: " + visitIdentifier);
        hideProgressView(visitIdentifier);
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the visit is fully completed --
     * request successful and page rendered.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier       A unique identifier for the visit.
     * @param restorationIdentifier A unique identifier for restoring the page and scroll position
     *                              from cache.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitCompleted(String visitIdentifier, String restorationIdentifier) {
        TurbolinksLog.d("visitCompleted called");

        addRestorationIdentifierToMap(restorationIdentifier);

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
                @Override
                public void run() {
                    turbolinksAdapter.visitCompleted();
                }
            });
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Called when Turbolinks detects that the page being visited
     * has been invalidated, typically by new resources in the the page HEAD.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void pageInvalidated() {
        TurbolinksLog.d("pageInvalidated called");

        resetToColdBoot();

        TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
            @Override
            public void run() { // route through normal chain so progress view is shown, regular logging, etc.
                turbolinksAdapter.pageInvalidated();

                visit(location);
            }
        });
    }

    // ---------------------------------------------------
    // TurbolinksNative helper methods
    // ---------------------------------------------------

    /**
     * <p><b>JavascriptInterface only</b> Hides the progress view when the page is fully rendered.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void hideProgressView(final String visitIdentifier) {
        TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
            @Override
            public void run() {
                /**
                 * pageInvalidated will cold boot, but another in-flight response from
                 * visitResponseLoaded could attempt to hide the progress view. Checking
                 * turbolinksIsReady ensures progress view isn't hidden too soon by the non cold boot.
                 */
                if (turbolinksIsReady && TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
                    TurbolinksLog.d("Hiding progress view for visitIdentifier: " + visitIdentifier + ", currentVisitIdentifier: " + currentVisitIdentifier);
                    turbolinksView.removeProgressView();
                    progressView = null;
                }
            }
        });
    }

    /**
     * <p><b>JavascriptInterface only</b> Sets internal flags that indicate whether Turbolinks in
     * the webView is ready for use.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     *
     * @param turbolinksIsReady The Javascript bridge checks the current page for Turbolinks, and
     *                          sends the results of that check here.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void setTurbolinksIsReady(boolean turbolinksIsReady) {
        this.turbolinksIsReady = turbolinksIsReady;

        if (turbolinksIsReady) {
            TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
                @Override
                public void run() {
                    TurbolinksLog.d("TurbolinksSession is ready");
                    visitCurrentLocationWithTurbolinks();
                }
            });

            coldBootInProgress = false;
        } else {
            TurbolinksLog.d("TurbolinksSession is not ready. Resetting and throw error.");
            resetToColdBoot();
            visitRequestFailedWithStatusCode(currentVisitIdentifier, 500);
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Handles the error condition when reaching a page without
     * Turbolinks.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void turbolinksDoesNotExist() {
        TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
            @Override
            public void run() {
                TurbolinksLog.d("Error instantiating turbolinks_bridge.js - resetting to cold boot.");
                resetToColdBoot();
                turbolinksView.removeProgressView();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    /**
     * <p>Provides the ability to add an arbitrary number of custom Javascript Interfaces to the built-in
     * Turbolinks webView.</p>
     *
     * @param object The object with annotated JavascriptInterface methods
     * @param name   The unique name for the interface (must not use the reserved name "TurbolinksNative")
     */
    @SuppressLint("JavascriptInterface")
    public void addJavascriptInterface(Object object, String name) {
        if (TextUtils.equals(name, JAVASCRIPT_INTERFACE_NAME)) {
            throw new IllegalArgumentException(JAVASCRIPT_INTERFACE_NAME + " is a reserved Javascript Interface name.");
        }

        if (javascriptInterfaces.get(name) == null) {
            javascriptInterfaces.put(name, object);
            webView.addJavascriptInterface(object, name);

            TurbolinksLog.d("Adding JavascriptInterface: " + name + " for " + object.getClass().toString());
        }
    }

    /**
     * <p>Returns the activity attached to the Turbolinks call.</p>
     *
     * @return The attached activity.
     */
    public Activity getActivity() {
        return activity;
    }

    /**
     * <p>Returns the internal WebView used by Turbolinks.</p>
     *
     * @return The WebView used by Turbolinks.
     */
    public WebView getWebView() {
        return webView;
    }

    /**
     * <p>Resets the TurbolinksSession to go through the full cold booting sequence (full page load)
     * on the next Turbolinks visit.</p>
     */
    public void resetToColdBoot() {
        turbolinksBridgeInjected = false;
        turbolinksIsReady = false;
        coldBootInProgress = false;
    }

    /**
     * <p>Runs a Javascript function with any number of arbitrary params in the Turbolinks webView.</p>
     *
     * @param functionName The name of the function, without any parenthesis or params
     * @param params       A comma delimited list of params. Params will be automatically JSONified.
     */
    public void runJavascript(final String functionName, final Object... params) {
        TurbolinksHelper.runJavascript(applicationContext, webView, functionName, params);
    }

    /**
     * <p>Runs raw Javascript in webView. Simply wraps the loadUrl("javascript: methodName()") call.</p>
     *
     * @param rawJavascript The full Javascript string that will be executed by the WebView.
     */
    public void runJavascriptRaw(String rawJavascript) {
        TurbolinksHelper.runJavascriptRaw(applicationContext, webView, rawJavascript);
    }

    /**
     * <p>Tells the logger whether to allow logging in debug mode.</p>
     *
     * @param enabled If true debug logging is enabled.
     */
    public void setDebugLoggingEnabled(boolean enabled) {
        TurbolinksLog.setDebugLoggingEnabled(enabled);
    }

    /**
     * <p>Provides the status of whether Turbolinks is initialized and ready for use.</p>
     *
     * @return True if Turbolinks has been fully loaded and detected on the page.
     */
    public boolean turbolinksIsReady() {
        return turbolinksIsReady;
    }

    /**
     * <p>A convenience method to fire a Turbolinks visit manually.</p>
     *
     * @param location URL to visit.
     * @param action   Whether to treat the request as an advance (navigating forward) or a replace (back).
     */
    public void visitLocationWithAction(String location, String action) {
        runJavascript("webView.visitLocationWithActionAndRestorationIdentifier", TurbolinksHelper.encodeUrl(location), action, getRestorationIdentifierFromMap());
    }

    // ---------------------------------------------------
    // Private
    // ---------------------------------------------------

    /**
     * <p>Adds the restoration (cached scroll position) identifier to the local Hashmap.</p>
     *
     * @param value Restoration ID provided by Turbolinks.
     */
    private void addRestorationIdentifierToMap(String value) {
        if (activity != null) {
            restorationIdentifierMap.put(activity.toString(), value);
        }
    }

    /**
     * <p>Gets the restoration ID for the current activity.</p>
     *
     * @return Restoration ID for the current activity.
     */
    private String getRestorationIdentifierFromMap() {
        return restorationIdentifierMap.get(activity.toString());
    }

    /**
     * <p>Shows the progress view, either a custom one provided or the default.</p>
     *
     * <p>A default progress view is inflated if {@link #progressView} isn't called.
     * If already inflated, progress view is fully detached before being shown since it's reused.</p>
     */
    private void initProgressView() {
        // No custom progress view provided, use default
        if (progressView == null) {
            progressView = LayoutInflater.from(activity).inflate(R.layout.turbolinks_progress, turbolinksView, false);

            TurbolinksLog.d("TurbolinksSession background: " + turbolinksView.getBackground());
            progressView.setBackground(turbolinksView.getBackground());
            progressIndicator = progressView.findViewById(R.id.turbolinks_default_progress_indicator);
            progressIndicatorDelay = PROGRESS_INDICATOR_DELAY;

            Drawable background = turbolinksView.getBackground() != null ? turbolinksView.getBackground() : new ColorDrawable(activity.getResources().getColor(android.R.color.white));
            progressView.setBackground(background);
        }

        // A progress view can be reused, so ensure it's detached from its previous parent first
        if (progressView.getParent() != null) {
            ((ViewGroup) progressView.getParent()).removeView(progressView);
        }

        // Executed from here to account for progress indicator delay
        turbolinksView.showProgressView(progressView, progressIndicator, progressIndicatorDelay);
    }

    /**
     * <p>Convenience method to simply revisit the current location in the TurbolinksSession. Useful
     * so that different visit logic can be wrappered around this call in {@link #visit} or
     * {@link #setTurbolinksIsReady(boolean)}</p>
     */
    private void visitCurrentLocationWithTurbolinks() {
        TurbolinksLog.d("Visiting current stored location: " + location);

        String action = restoreWithCachedSnapshot ? ACTION_RESTORE : ACTION_ADVANCE;
        visitLocationWithAction(TurbolinksHelper.encodeUrl(location), action);
    }

    /**
     * <p>Ensures all required chained calls/parameters ({@link #activity}, {@link #turbolinksView},
     * and location}) are set before calling {@link #visit(String)}.</p>
     */
    private void validateRequiredParams() {
        if (activity == null) {
            throw new IllegalArgumentException("TurbolinksSession.activity(activity) must be called with a non-null object.");
        }

        if (turbolinksAdapter == null) {
            throw new IllegalArgumentException("TurbolinksSession.adapter(turbolinksAdapter) must be called with a non-null object.");
        }

        if (turbolinksView == null) {
            throw new IllegalArgumentException("TurbolinksSession.view(turbolinksView) must be called with a non-null object.");
        }

        if (TextUtils.isEmpty(location)) {
            throw new IllegalArgumentException("TurbolinksSession.visit(location) location value must not be null.");
        }
    }
}
