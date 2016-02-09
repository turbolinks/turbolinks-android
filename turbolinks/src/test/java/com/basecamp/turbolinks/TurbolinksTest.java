package com.basecamp.turbolinks;

import android.app.Activity;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Matchers.any;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = TestBuildConfig.class)
public class TurbolinksTest extends BaseTest {
    @Mock Activity activity;
    @Mock TurbolinksAdapter adapter;
    @Mock TurbolinksView view;
    @Mock FrameLayout progressView;

    private static final String LOCATION = "https://basecamp.com";
    private static final String VISIT_IDENTIFIER = "visitIdentifierValue";
    private static final String RESTORATION_IDENTIFIER = "restorationIdentifierValue";

    private Turbolinks turbolinks;

    @Before
    public void setup() {
        super.setup();
        MockitoAnnotations.initMocks(this);

        turbolinks = Turbolinks.getNew(context);
    }

    // -----------------------------------------------------------------------
    // Initializing
    // -----------------------------------------------------------------------

    @Test
    public void getNewIsAlwaysNewInstance() {
        Turbolinks defaultInstance = Turbolinks.getNew(context);

        assertThat(defaultInstance).isNotEqualTo(Turbolinks.getNew(context));
    }

    @Test
    public void getDefaultReturnsSameInstance() {
        Turbolinks defaultInstance = Turbolinks.getDefault(context);

        assertThat(defaultInstance).isEqualTo(Turbolinks.getDefault(context));
    }

    @Test
    public void resetDefaultGetsNewDefaultInstance() {
        Turbolinks defaultInstance = Turbolinks.getDefault(context);
        Turbolinks.resetDefault();

        assertThat(defaultInstance).isNotEqualTo(Turbolinks.getDefault(context));
    }

    // -----------------------------------------------------------------------
    // Visit parameters
    // -----------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void activityIsNull() {
        turbolinks.activity(null)
            .adapter(adapter)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void adapterNotProvided() {
        turbolinks.activity(activity)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void adapterInterfaceNotImplemented() {
        turbolinks.activity(activity)
            .adapter(new Object())
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void progressViewWithInvalidProgressBar() {
        turbolinks.activity(activity)
            .adapter(adapter)
            .progressView(progressView, 123, 0)
            .view(view)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void viewNotProvided() {
        turbolinks.activity(activity)
            .adapter(adapter)
            .visit(LOCATION);
    }

    @Test(expected = IllegalArgumentException.class)
    public void visitWithoutLocation() {
        turbolinks.activity(activity)
            .adapter(adapter)
            .view(view)
            .visit("");
    }

    // -----------------------------------------------------------------------
    // Adapter
    // -----------------------------------------------------------------------

    @Test
    public void visitProposedToLocationWithActionCallsAdapter() {
        turbolinks.activity(activity)
            .adapter(adapter);
        turbolinks.visitProposedToLocationWithAction(LOCATION, Turbolinks.ACTION_ADVANCE);

        verify(adapter).visitProposedToLocationWithAction(any(String.class), any(String.class));
    }

    @Test
    public void visitStartedSavesCurrentVisitIdentifier() {
        turbolinks.currentVisitIdentifier = null;

        assertThat(turbolinks.currentVisitIdentifier).isNotEqualTo(VISIT_IDENTIFIER);

        turbolinks.activity(activity)
            .adapter(adapter);
        turbolinks.visitStarted(VISIT_IDENTIFIER, true);

        assertThat(turbolinks.currentVisitIdentifier).isEqualTo(VISIT_IDENTIFIER);
    }

    @Test
    public void visitRequestFailedWithStatusCodeCallsAdapter() {
        turbolinks.activity(activity)
            .adapter(adapter);
        turbolinks.currentVisitIdentifier = VISIT_IDENTIFIER;
        turbolinks.visitRequestFailedWithStatusCode(VISIT_IDENTIFIER, 0);

        verify(adapter).requestFailedWithStatusCode(any(int.class));
    }

    @Test
    public void visitCompletedCallsAdapter() {
        turbolinks.activity(activity)
            .adapter(adapter);
        turbolinks.currentVisitIdentifier = VISIT_IDENTIFIER;
        turbolinks.visitCompleted(VISIT_IDENTIFIER, RESTORATION_IDENTIFIER);

        verify(adapter).visitCompleted();
    }

    @Test
    public void visitCompletedSavesRestorationIdentifier() {
        assertThat(turbolinks.restorationIdentifierMap.size()).isEqualTo(0);

        turbolinks.activity(activity)
            .adapter(adapter);
        turbolinks.visitCompleted(VISIT_IDENTIFIER, RESTORATION_IDENTIFIER);

        assertThat(turbolinks.restorationIdentifierMap.size()).isEqualTo(1);
    }

//    TODO: Robolectric having trouble with local resources directory
//    @Test
//    public void pageInvalidatedCallsAdapter() {
//        Turbolinks.initialize(context);
//        Turbolinks.activity(activity)
//            .adapter(adapter)
//            .view(view)
//            .visit(LOCATION);
//        Turbolinks.singleton.pageInvalidated();
//
//        verify(adapter).pageInvalidated();
//    }
//
//    @Test
//    public void pageInvalidatedResetsToColdBoot() {
//        Turbolinks.initialize(context);
//        Turbolinks.activity(activity)
//            .adapter(adapter)
//            .view(view)
//            .visit(LOCATION);
//        Turbolinks.singleton.turbolinksBridgeInjected = true;
//        Turbolinks.singleton.turbolinksIsReady = true;
//        Turbolinks.singleton.coldBootInProgress = false;
//
//        Turbolinks.singleton.pageInvalidated();
//
//        assertThat(Turbolinks.singleton.turbolinksBridgeInjected).isTrue();
//        assertThat(Turbolinks.singleton.turbolinksIsReady).isTrue();
//        assertThat(Turbolinks.singleton.coldBootInProgress).isFalse();
//    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @Test
    public void hideProgressViewNullsView() {
        turbolinks.turbolinksIsReady = true;
        turbolinks.turbolinksView = view;
        turbolinks.progressView = new FrameLayout(context);
        turbolinks.currentVisitIdentifier = VISIT_IDENTIFIER;
        turbolinks.hideProgressView(VISIT_IDENTIFIER);

        assertThat(turbolinks.progressView).isNull();
    }


    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void addJavascriptInterfaceWithReservedName() {
        turbolinks.addJavascriptInterface(new Object(), Turbolinks.JAVASCRIPT_INTERFACE_NAME);
    }

    @Test
    public void addJavascriptInterfaceAddsToMap() {
        assertThat(turbolinks.javascriptInterfaces.size()).isEqualTo(0);
        turbolinks.addJavascriptInterface(new Object(), "TestJavascriptInterface");
        assertThat(turbolinks.javascriptInterfaces.size()).isEqualTo(1);
    }

    @Test
    public void activityIsNullIfNotInitialized() {
        assertThat(turbolinks.getActivity()).isNull();
    }

    @Test
    public void webViewIsNullIfNotInitialized() {
        assertThat(turbolinks.getActivity()).isNull();
    }

    @Test
    public void resetToColdBoot() {
        turbolinks.activity(activity)
            .adapter(adapter);
        turbolinks.turbolinksBridgeInjected = true;
        turbolinks.turbolinksIsReady = true;
        turbolinks.coldBootInProgress = false;
        turbolinks.resetToColdBoot();

        assertThat(turbolinks.turbolinksBridgeInjected).isFalse();
        assertThat(turbolinks.turbolinksIsReady).isFalse();
        assertThat(turbolinks.coldBootInProgress).isFalse();
    }

    @Test
    public void turbolinksIsReady() {
        turbolinks.turbolinksIsReady = true;

        assertThat(turbolinks.turbolinksIsReady()).isTrue();
    }
}
