package com.basecamp.turbolinks;

import android.content.Context;
import android.util.AttributeSet;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * <p>Custom SwipeRefreshLayout for Turbolinks.</p>
 */
class TurbolinksSwipeRefreshLayout extends SwipeRefreshLayout {
    private TurbolinksScrollUpCallback callback;

    /**
     * <p>Constructor to match SwipeRefreshLayout</p>
     *
     * @param context Refer to SwipeRefreshLayout
     */
    TurbolinksSwipeRefreshLayout(Context context) {
        super(context);
    }

    /**
     * <p>Constructor to match SwipeRefreshLayout</p>
     *
     * @param context Refer to SwipeRefreshLayout
     * @param attrs Refer to SwipeRefreshLayout
     */
    TurbolinksSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * <p>Overridden from SwipeRefreshLayout. Uses a custom callback.</p>
     * <p>If the custom callback is null, it falls back to the parent canChildScrollUp()</p>
     *
     * @return True if the child view can scroll up. False otherwise.
     */
    @Override
    public boolean canChildScrollUp() {
        if (callback != null) { return callback.canChildScrollUp(); }
        return super.canChildScrollUp();
    }

    /**
     * <p>Sets the callback to be used in canChildScrollUp().</p>
     * <p>See canChildScrollUp() to see how it's used.</p>
     *
     * @param callback The custom callback to be set
     */
    void setCallback(TurbolinksScrollUpCallback callback) { this.callback = callback; }
}
