package com.basecamp.turbolinks;

public interface TurbolinksAdapter {
    void onPageFinished();
    void onReceivedError(int errorCode);
    void pageInvalidated();
    void requestFailedWithStatusCode(int statusCode);
    void visitCompleted();
    void visitProposedToLocationWithAction(String location, String action);
}
