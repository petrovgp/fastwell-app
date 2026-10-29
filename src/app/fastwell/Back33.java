package app.fastwell;

import android.app.Activity;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * Predictive back (API 33+). On Android 16 with targetSdk 36, onBackPressed() is no longer called,
 * so in-app back navigation must go through an OnBackInvokedCallback. Kept in its own class so
 * older devices never load these types.
 */
final class Back33 {
    private Back33() {}

    static Object register(Activity a, final Runnable onBack) {
        OnBackInvokedCallback cb = new OnBackInvokedCallback() {
            @Override
            public void onBackInvoked() { onBack.run(); }
        };
        a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        return cb;
    }

    static void unregister(Activity a, Object cb) {
        a.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) cb);
    }
}
