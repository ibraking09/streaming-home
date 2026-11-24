package local.firetv.home;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

public final class HomeRedirect extends AccessibilityService {
    static final String AMAZON = HomeRedirectPolicy.AMAZON;
    private static final HomeRedirectPolicy policy = new HomeRedirectPolicy();

    static void enterSystemSession() { policy.enterSystemSession(); }
    static void leaveSystemSession() { policy.leaveSystemSession(); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || !policy.shouldRedirect(event.getPackageName(), event.getClassName(),
                        SystemClock.elapsedRealtime())) {
            return;
        }
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override
    public void onInterrupt() {}
}
