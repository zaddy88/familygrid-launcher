package dev.fluxnova.familygrid;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/**
 * UI TIER helpers for apps targeting Android 15/16:
 * - edge-to-edge is forced, so screens pad themselves by the system bars / camera cutout;
 * - onBackPressed() is no longer called, so Back goes through OnBackInvokedCallback.
 * On older Android versions both helpers are harmless no-ops (the system already insets content).
 */
final class EdgeToEdge {
    private EdgeToEdge() {}

    /** Adds the system-bar and cutout sizes to the view's own padding, keeping its original padding. */
    static void padForSystemBars(View v) {
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), b = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            int il, it, ir, ib;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                il = i.left; it = i.top; ir = i.right; ib = i.bottom;
            } else {
                il = insets.getSystemWindowInsetLeft();
                it = insets.getSystemWindowInsetTop();
                ir = insets.getSystemWindowInsetRight();
                ib = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(l + il, t + it, r + ir, b + ib);
            return insets;
        });
        v.requestApplyInsets();
    }

    /** Routes Back to {@code action} on Android 13+ (call onBackPressed() yourself on older versions). */
    static void onBack(Activity a, Runnable action) {
        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedCallback cb = action::run;
            a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        }
    }
}
