# Turbolinks Android

Turbolinks Android is a native adapter for any [Turbolinks 5](https://github.com/turbolinks/turbolinks#readme) enabled web application. It's built entirely using standard Android tools and conventions.

This library has been in use and tested in the wild since November 2015 in the all-new [Basecamp 3 for Android](https://play.google.com/store/apps/details?id=com.basecamp.bc3).

Our goal for this library was that it'd be easy on our fellow programmers:

- **Easy to start**: one jcenter dependency, one custom view, one adapter interface to implement. No other requirements.
- **Easy to use**: one reusable static method call.
- **Easy to understand**: tidy code, and solid documentation via [Javadocs](http://basecamp.github.io/turbolinks-android/javadoc/) and this README.

## Installation (One Step)
Add the dependency from jcenter to your app's (not project) `build.gradle` file.

```groovy
repositories {
    jcenter()
}

dependencies {
    'com.basecamp:turbolinks:1.0.0'
}
```

## Getting Started (Three Steps)

### Prerequisites

We recommend using Turbolinks from an activity or an extension of your activity (like a custom controller).

This library hasn't been tested with Android Fragments (we don't use them). Using this library with Fragments might produce unintended results.

### 1. Add TurbolinksView to a Layout

In your activity's layout, insert the `TurbolinksView` custom view.

`TurbolinksView` extends `FrameLayout`, so it has all the properties of a `FrameLayout` available to you.

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.basecamp.turbolinks.TurbolinksView
        android:id="@+id/turbolinks_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

</LinearLayout>
```

### 2. Implement the TurbolinksAdapter Interface

Tell your activity to implement the `TurbolinksAdapter` interface. You'll be required to implement a handful of methods that are callbacks from the library. You don't need to worry about handling every callback right off the bat, especially if you're just starting off with a simple app.

**But at the very minimum, you must handle the [visitProposedToLocationWithAction](#visitproposedtolocationwithaction)**. Otherwise your app won't know what to do/where to go when a link is clicked.

There's more detail in what each of these callbacks is in the [advanced options section below](#advanced-options) as well as the [Javadoc](http://basecamp.github.io/turbolinks-android/javadoc/). Also the [demo app](/demoapp) provides a good example of how each callback might be used.

For now you can just implement all of them as empty methods *except visitProposedToLocationWithAction*.

### 3. Initialize Turbolinks and Visit a Location

Assuming you are calling Turbolinks from an activity and you've implemented the `TurbolinksAdapter` interface in the same activity, here's how you tell Turbolinks to visit a location.

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    // Standard activity boilerplate here...

    // Assumes an instance variable is already defined
    turbolinksView = (TurbolinksView) findViewById(R.id.turbolinks_view);

    if (!Turbolinks.isInitialized()) {
        Turbolinks.initialize(this);
    }

    Turbolinks.activity(this)
              .adapter(this)
              .view(turbolinksView)
              .visit("https://basecamp.com");
}
```

🎉**Congratulations, you're using Turbolinks on Android!** 👏

## Advanced Options

### Handling Adapter Callbacks

The `TurbolinksAdapter` class provides callback events directly from the WebView and Turbolinks itself. This gives you the opportunity to intercept those events and inject your own native actions -- things like routing logic, displaying UI elements, and error handling.

You can of course choose to leave these adapter callbacks blank, but we'd recommend at the very least implementing and handling the two error condition callbacks.

#### visitProposedToLocationWithAction

This is a callback from Turbolinks telling you that a visit has been proposed and is about to begin. **This is the most important callback that you must implement.**

This callback provides your app the oppotunity to figure out what it should do and where it should go. At the very minimum, you can create an `Intent` to open another `Activity` that fires another Turbolinks call with the provided location, like so:

```java
Intent intent = new Intent(this, MainActivity.class);
intent.putExtra(INTENT_URL, location);
this.startActivity(intent);
```

In more complex apps, you'll most likely want to do some routing logic here. Should you open another WebView Activity? Are you perhaps routing to an external app? Should you open a native Activity in certain cases? This is the place to do that logic.

#### onPageFinished

This is a callback that's executed at the end of the standard [WebViewClient's onPageFinished](http://developer.android.com/reference/android/webkit/WebViewClient.html#onPageFinished(android.webkit.WebView, java.lang.String)) method.

The first location that Turbolinks loads after initialization is always a "cold boot" -- a full page load of all resources, executed through a normal `WebView.loadUrl()`. Every subsequent location visit (with the exception of an error condition or page invalidation) will fire through Turbolinks, without a full page load.

As a result, this `TurbolinksAdapter.onPageFinished()` callback will only be fired once upon cold booting. If there is any action you need to take after the first full page load is complete, just once, this is the place to do it.

For example, when Basecamp receives this callback, we use it then load up additional Javascript into the `<HEAD>` of the page, since we know the DOM is now fully loaded.

Example:
```java
WebViewHelper.injectJavascriptFileIntoHead();
```

#### visitCompleted

This is a callback from Turbolinks telling you it considers the visit completed. The request has been fulfilled successfully and the page fully rendered.

It's similar conceptually to onPageFinished, except this callback will be called for every Turbolinks visit. This is a good time to take actions that you need on every page, such as reading in page data-attributes that might affect your UI or available native actions.

#### onReceivedError

This is a callback that's executed at the end of the standard [WebViewClient's onReceivedError](http://developer.android.com/reference/android/webkit/WebViewClient.html#onReceivedError(android.webkit.WebView, int, java.lang.String, java.lang.String)) method.

**We recommend you implement this method.** Otherwise, your user will see an endless progress view/spinner without something that handles the error. You can handle the error however you like -- send the user to a different page, show a native error screen, etc.

#### requestFailedWithStatusCode

This is a callback from Turbolinks telling you that an XHR request has failed for some reason.

**We recommend you implement this method.** Otherwise, your user will see an endless progress view/spinner without something that handles the error. You can handle the error however you like -- send the user to a different page, show a native error screen, etc.

#### pageInvalidated

This is a callback from Turbolinks telling you that Turbolinks has detected a change in a resource/asset in the `<HEAD>`, and as a result the Turbolinks state has been invalidated. Most likely the web application has been updated while the app was using it.

The library will automatically fall back to cold booting the location (which it must do since resources have been changed) and then will notify you via this callback that the page was invalidated. This is an opportunity for you to clean up any UI state that you might have lingering around that may no longer be valid (like a screenshot, title data, etc.)

### Custom Progress View

By default the library will provide you with a progress view with a progress bar -- a simple `FrameLayout` that covers the `WebView` while it's loading, and shows a spinner after 500ms.

If that doesn't meet your needs, you can also pass in your own custom progress view like so:

```java
Turbolinks.activity(this)
          .adapter(this)
          .progressView(progressView, resourceIdOfProgressBar, progressBarDelay)
          .view(turbolinksView)
          .visit("https://basecamp.com");
```

Some notes about using a custom progress view:

- It doesn't matter what kind of layout view you use, but you'll want to do something that covers the entire `WebView` and uses `match_parent` for the height and width.
- We ask you to provide the resource ID of the progress bar *inside your progress view* so that we can internally handle when to display it. The library has a mechanism that can delay showing the progress bar to improve perceived loading times (a slight delay in showing the progress bar makes apps feel faster).
- In conjunction with the progress bar resource ID, you can also specify the delay, in milliseconds, before it is displayed. The default, and our recommendation, is 500ms.

### Custom WebView WebSettings

By default the library sets some minimally intrusive WebSettings on the shared WebView. Some are required while others serve as reasonable defaults for modern web applications. They are:

- `setJavaScriptEnabled(true)` (required)
- `setDomStorageEnabled(true)`
- `setDatabaseEnabled(true)`

If however these are not to your liking, you can always override them. The `WebView` is always available to you via `Turbolinks.getWebView()`, and you can update the `WebSettings` to your liking:

```java
WebSettings settings = Turbolinks.getWebView().getSettings();
settings.setDomStorageEnabled(false);
settings.setAllowFileAccess(false);
```

### Custom JavascriptInterfaces

If you have custom Javascript on your pages that you want to access as JavascriptInterfaces, that's possible like so:

```java
Turbolinks.addJavascriptInterface(this, "MyCustomJavascriptInterface");
```
The Java object being referenced can be anything, as long as it has at least one method annotated with `@android.webkit.JavascriptInterface`. Names of interfaces must be unique, or they will be overwritten in the library's map.
