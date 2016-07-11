package com.basecamp.turbolinks;

/**
 * <p>Defines a callback for determining whether or not a child view can scroll up.</p>
 */
public interface CanScrollUpCallback {
    /**
     *<p>Used to determine whether or not a child view can scroll up.</p>
     *
     * @return True if the child can scroll up. False otherwise.
     */
    boolean canChildScrollUp();
}
