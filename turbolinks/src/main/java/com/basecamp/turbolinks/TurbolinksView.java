package com.basecamp.turbolinks;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * <p>The custom view to add to your activity layout.</p>
 */
public class TurbolinksView extends FrameLayout {
    private View progressView = null;

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
    }

    /**
     * <p>Constructor to match FrameLayout.</p>
     *
     * @param context Refer to FrameLayout.
     * @param attrs Refer to FrameLayout.
     */
    public TurbolinksView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * <p>Constructor to match FrameLayout.</p>
     *
     * @param context Refer to FrameLayout.
     * @param attrs Refer to FrameLayout.
     * @param defStyleAttr Refer to FrameLayout.
     */
    public TurbolinksView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * <p>Constructor to match FrameLayout.</p>
     *
     * @param context Refer to FrameLayout.
     * @param attrs Refer to FrameLayout.
     * @param defStyleAttr Refer to FrameLayout.
     * @param defStyleRes Refer to FrameLayout.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public TurbolinksView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    // ---------------------------------------------------
    // Package public
    // ---------------------------------------------------

    /**
     * <p>Detaches/attaches a progress view on top of the TurbolinksView to indicate the page is
     * loading. Progress indicator is set to a specified delay before displaying -- a very short delay
     * (like 500 ms) can improve perceived loading time to the user.</p>
     *
     * @param progressView The progressView to display on top of TurbolinksView.
     * @param progressIndicator The progressIndicator to display in the view.
     * @param delay The delay before showing the progressIndicator in the view. The default progress view
     *              is 500 ms.
     */
    void showProgressView(final View progressView, final View progressIndicator, int delay) {
        TurbolinksLog.d("showProgressView called");

        removeProgressView();

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
     * <p>Removes the progressView from the TurbolinksView. Ensures no exceptions are thrown where
     * the progressView is already attached to another view.</p>
     */
    void removeProgressView() {
        removeView(progressView);
    }

    /**
     * <p>Attach the shared webView to the TurbolinksView.</p>
     *
     * @param webView The shared webView.
     */
    void attachWebView(WebView webView) {
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) {
            parent.removeView(webView);
        }

        // Set the webview background to match the container background
        if (getBackground() instanceof ColorDrawable) {
            webView.setBackgroundColor(((ColorDrawable) getBackground()).getColor());
        }

        addView(webView, 0);
    }
}
