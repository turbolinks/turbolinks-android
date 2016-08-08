package com.basecamp.turbolinks;

import android.webkit.WebView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = TestBuildConfig.class)
public class TurbolinksViewTest extends BaseTest {
    @Mock WebView webView;

    @Before
    public void setup() {
        super.setup();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getRefreshLayout() {
        TurbolinksView view = new TurbolinksView(context);

        assertThat(view.getRefreshLayout()).isNotNull();
    }

    @Test
    public void attachWebView() {
        TurbolinksView view = new TurbolinksView(context);
        view.attachWebView(webView, false, false);

        assertThat(view.getRefreshLayout().getChildAt(1)).isEqualTo(webView);
    }
}
