package com.basecamp.turbolinks;

import android.webkit.WebSettings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = TestBuildConfig.class)
public class TurbolinksHelperTest extends BaseTest {

    @Before
    public void setup() {
        super.setup();
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createWebView() {
        assertThat(TurbolinksHelper.createWebView(context)).isNotNull();
    }

    @Test
    public void creatingWebViewHasCorrectSettings() {
        WebSettings settings = TurbolinksHelper.createWebView(context).getSettings();

        assertThat(settings.getJavaScriptEnabled()).isTrue();
        assertThat(settings.getDomStorageEnabled()).isTrue();
        assertThat(settings.getDatabaseEnabled()).isTrue();
    }

    @Test
    public void encodeUrlProperlyEncodes() {
        String url = "http://basecamp.com/search?q=test test + testing & /testfile .mp4";
        assertThat(TurbolinksHelper.encodeUrl(url)).doesNotContain(" ");
    }

//    TODO: Robolectric having trouble with local resources directory
//    @Test
//    public void getContentFromAssetFile() {
//        try {
//            TurbolinksHelper.getContentFromAssetFile(context, "js/turbolinks_bridge.js");
//        } catch (IOException e) {
//            Log.d("TurbolinksHelpler", e.toString());
//            assertThat(0).isEqualTo(1);
//        } finally {
//            assertThat(2).isEqualTo(2);
//        }
//    }
}