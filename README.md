# Turbolinks Android

Turbolinks Android is a native adapter for any [Turbolinks 5](https://github.com/turbolinks/turbolinks#readme) enabled site. It's built entirely using standard Android tools and conventions.

This library has been in use and tested in the wild since November 2015 in the all-new [Basecamp 3 for Android](https://play.google.com/store/apps/details?id=com.basecamp.bc3).

Our goal for this library was that it'd be easy on our fellow programmers:

- **Easy to start**: one jcenter dependency, one custom view, one adapter interface to implement. No other requirements.
- **Easy to use**: one reusable static method call.
- **Easy to understand**: tidy code, and solid documentation via [Javadocs](http://basecamp.github.io/turbolinks-android/javadoc/) and this README.

## Installation (One Step)
Add the dependency from jcenter to your app's (not project) `build.gradle` file.

```
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

### 1. Add TurbolinksView to a layout

In your activity's layout, insert the `TurbolinksView` custom view.

`TurbolinksView` extends `FrameLayout`, so it will span the entirety of the container you give it. It also has all the properties of a `FrameLayout` availalbe to you.

```
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

Tell your activity to extend the `TurbolinksAdapter` interface. You'll be required to implement a handful of methods. 

Don't worry too much about these methods yet -- they are simply callbacks from Turbolinks to your application. It can be useful to intercept these events and handle them however you want. 

There's more detail in what each of these callbacks is in the [Javadoc](http://basecamp.github.io/turbolinks-android/javadoc/), and the demo app provides a good example of what each callback might be used for.

For now you can just implement them as empty methods, and come back to them for more advanced scenarios.

### 3. Initialize Turbolinks and Visit Location

Assuming you are calling Turbolinks from an activity and you've implemented the `TurbolinksAdapter` interface in the same activity, here's how you tell Turbolinks to visit a location.

```
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

**Congratulations, you're using Turbolinks on Android!**


## Advanced Options

### Handling Adapter Callbacks

### Custom Progress View

### Custom WebViewClient

### Custom JavascriptInterfaces