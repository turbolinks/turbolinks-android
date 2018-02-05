package com.basecamp.turbolinks;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

/* The internal view hierarchy uses the following structure:
 *
 * TurbolinksView
 *   > TurbolinksSwipeRefreshLayout
 *     > WebView (gets attached/detached here)
 *   > Progress View
 *   > Screenshot View
 */

/**
 * <p>The custom view to add to your activity layout.</p>
 */
public class TurbolinksView extends FrameLayout {
    private TurbolinksSwipeRefreshLayout refreshLayout = null;
    private View progressView = null;
    private ImageView screenshotView = null;
    private int screenshotOrientation = 0;

    // ---------------------------------------------------
    // Constructors
    // ---------------------------------------------------

    /**
     * <p>Constructor to match FrameLayout.</p>
     *
     * @param context Refer to FrameLayout.
     */
    public TurbolinksView(Context context) {
        super(context);
        init();
    }

    /**
     * <p>Constructor to match FrameLayout.</p>
     *
     * @param context Refer to FrameLayout.
     * @param attrs   Refer to FrameLayout.
     */
    public TurbolinksView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * <p>Constructor to match FrameLayout.</p>
     *
     * @param context      Refer to FrameLayout.
     * @param attrs        Refer to FrameLayout.
     * @param defStyleAttr Refer to FrameLayout.
     */
    public TurbolinksView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * <p>Constructor to match FrameLayout.</p>
     *
     * @param context      Refer to FrameLayout.
     * @param attrs        Refer to FrameLayout.
     * @param defStyleAttr Refer to FrameLayout.
     * @param defStyleRes  Refer to FrameLayout.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TurbolinksView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    /**
     * <p>Initializes the view in a common places from all constructors.</p>
     */
    private void init() {
        refreshLayout = new TurbolinksSwipeRefreshLayout(getContext(), null);
        addView(refreshLayout, 0);
    }

    // ---------------------------------------------------
    // Package public
    // ---------------------------------------------------

    /**
     * <p>Shows a progress view or a generated screenshot of the webview content (if available)
     * on top of the webview. When advancing to a new url, this indicates that the page is still
     * loading. When resuming an activity in the navigation stack, a screenshot is displayed while the
     * webview is restoring its snapshot.</p>
     * <p>Progress indicator is set to a specified delay before displaying -- a very short delay
     * (like 500 ms) can improve perceived loading time to the user.</p>
     *
     * @param progressView      The progressView to display on top of TurbolinksView.
     * @param progressIndicator The progressIndicator to display in the view.
     * @param delay             The delay before showing the progressIndicator in the view. The default progress view
     *                          is 500 ms.
     */
    void showProgress(final View progressView, final View progressIndicator, int delay) {
        TurbolinksLog.d("showProgress called");

        // Don't show the progress view if a screenshot is available
        if (screenshotView != null && screenshotOrientation == getOrientation()) return;

        hideProgress();

        this.progressView = progressView;
        progressView.setClickable(true);
        addView(progressView);

        progressIndicator.setVisibility(View.GONE);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                progressIndicator.setVisibility(View.VISIBLE);
            }
        }, delay);
    }

    /**
     * <p>Removes the progress view and/or screenshot from the TurbolinksView, so the webview is
     * visible underneath.</p>
     */
    void hideProgress() {
        removeProgressView();
        removeScreenshotView();
    }

    /**
     * <p>Attach the swipeRefreshLayout, which contains the shared webView, to the TurbolinksView.</p>
     *
     * @param webView              The shared webView.
     * @param screenshotsEnabled   Indicates whether screenshots are enabled for the current session.
     * @param pullToRefreshEnabled Indicates whether pull to refresh is enabled for the current session.
     * @return True if the webView has been attached to a new parent, otherwise false
     */
    boolean attachWebView(WebView webView, boolean screenshotsEnabled, boolean pullToRefreshEnabled) {
        if (webView.getParent() == refreshLayout) return false;

        refreshLayout.setEnabled(pullToRefreshEnabled);

        if (webView.getParent() instanceof TurbolinksSwipeRefreshLayout) {
            TurbolinksSwipeRefreshLayout previousRefreshLayout = (TurbolinksSwipeRefreshLayout) webView.getParent();
            TurbolinksView previousTurbolinksView = (TurbolinksView) previousRefreshLayout.getParent();

            if (screenshotsEnabled) previousTurbolinksView.screenshotView();

            try {
                // This is an admittedly hacky workaround, but it buys us some time as we investigate
                // a potential bug with Chrome 64, which is currently throwing an IllegalStateException
                // when accessibility services (like Talkback or 1password) are enabled.
                // We're tracking this bug on the Chromium issue tracker:
                // https://bugs.chromium.org/p/chromium/issues/detail?id=806108
                previousRefreshLayout.removeView(webView);
            } catch (Exception e) {
                previousRefreshLayout.removeView(webView);
            }
        }

        // Set the webview background to match the container background
        if (getBackground() instanceof ColorDrawable) {
            webView.setBackgroundColor(((ColorDrawable) getBackground()).getColor());
        }

        refreshLayout.addView(webView);
        return true;
    }

    /**
     * <p>Gets the refresh layout used internally for pull-to-refresh functionality.</p>
     *
     * @return The internal refresh layout.
     */
    TurbolinksSwipeRefreshLayout getRefreshLayout() {
        return refreshLayout;
    }

    /**
     * Removes the progress view as a child of TurbolinksView
     */
    private void removeProgressView() {
        if (progressView == null) return;

        removeView(progressView);
        TurbolinksLog.d("Progress view removed");
    }

    /**
     * Removes the screenshot view as a child of TurbolinksView
     */
    private void removeScreenshotView() {
        if (screenshotView == null) return;

        removeView(screenshotView);
        screenshotView = null;
        TurbolinksLog.d("Screenshot removed");
    }

    /**
     * <p>Creates a screenshot of the current webview content and makes it the top visible view.</p>
     */
    private void screenshotView() {
        // Only take a screenshot if the activity is not finishing
        if (getContext() instanceof Activity && ((Activity) getContext()).isFinishing()) return;

        Bitmap screenshot = getScreenshotBitmap();
        if (screenshot == null) return;

        screenshotView = new ImageView(getContext());
        screenshotView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        screenshotView.setClickable(true);
        screenshotView.setImageBitmap(screenshot);
        screenshotOrientation = getOrientation();

        addView(screenshotView);

        TurbolinksLog.d("Screenshot taken");
    }

    /**
     * <p>Creates a bitmap screenshot of the webview contents from the canvas.</p>
     *
     * @return The screenshot of the webview contents.
     */
    private Bitmap getScreenshotBitmap() {
        if (!hasEnoughHeapMemoryForScreenshot()) return null;

        if (getWidth() <= 0 || getHeight() <= 0) return null;

        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        draw(new Canvas(bitmap));
        return bitmap;
    }

    /**
     * Gets the current orientation of the device.
     *
     * @return The current orientation.
     */
    private int getOrientation() {
        return getContext().getResources().getConfiguration().orientation;
    }

    /**
     * Determines if the app's memory heap has enough space to create a bitmapped screenshot without
     * running into an OOM.
     *
     * @return Whether the heap has over a given % of memory available.
     */
    private boolean hasEnoughHeapMemoryForScreenshot() {
        final Runtime runtime = Runtime.getRuntime();

        // Auto casting to floats necessary for division
        float free = runtime.freeMemory();
        float total = runtime.totalMemory();
        float remaining = free / total;

        TurbolinksLog.d("Memory remaining percentage: " + remaining);

        return remaining > .10;
    }
}
