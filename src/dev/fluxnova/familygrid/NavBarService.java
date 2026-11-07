package dev.fluxnova.familygrid;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * UI TIER: a large, labelled "Back · Home · All apps" bar drawn over the system
 * navigation bar in every app. It only draws buttons and performs global actions;
 * it does not read screen content (see res/xml/navbar_service.xml).
 */
public class NavBarService extends AccessibilityService {
    private WindowManager wm;
    private View bar;
    private float shownScale = -1;
    private String shownSignature = "";

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            // hide on the lock screen, show again once the phone is unlocked
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) removeBar();
            else showBar();
        }
    };

    /** true when the user has switched this service on in Accessibility settings. */
    static boolean isEnabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        ComponentName me = new ComponentName(c, NavBarService.class);
        for (String s : list.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    @Override protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, f);
        showBar();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Window changes are the cheap hook to pick up a new text size from the editor.
        if (bar != null && !shownSignature.equals(signature())) {
            removeBar();
            showBar();
        }
    }

    @Override public void onInterrupt() {}

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        removeBar();
        showBar();
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(screenReceiver); } catch (IllegalArgumentException ignored) {}
        removeBar();
        super.onDestroy();
    }

    private void showBar() {
        if (wm == null || bar != null) return;
        // In landscape the system bar moves to the side; stay out of the way there.
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) return;

        shownScale = HubConfig.textScale(this);
        shownSignature = signature();
        Context lc = HubConfig.localized(this); // pick up language changes without a restart
        String[] names = HubConfig.navLabels(lc);
        LinearLayout row = new LinearLayout(lc);
        row.setLayoutDirection(lc.getResources().getConfiguration().getLayoutDirection());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(0xFF0B0F16);
        row.setPadding(dp(6), dp(4), dp(6), dp(4));
        row.addView(button(names[0], 0xFF1C2532, v -> performGlobalAction(GLOBAL_ACTION_BACK)), weight());
        row.addView(button(names[1], 0xFF0D47A1, v -> performGlobalAction(GLOBAL_ACTION_HOME)), weight());
        row.addView(button(names[2], 0xFF1C2532, v -> {
            Intent i = new Intent(this, AppsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }), weight());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            barHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        // Sit exactly on top of the system navigation bar instead of covering the bottom of
        // the app (send boxes, tab bars...).
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0); // don't let the system push the window above its bars
            lp.y = 0;
        } else {
            lp.y = -systemNavHeight(); // older Android stops window layout above the bar
        }
        try {
            wm.addView(row, lp);
            bar = row;
        } catch (RuntimeException e) {
            bar = null;
        }
    }

    /** Changes when text size or labels change, so the bar rebuilds itself. */
    private String signature() {
        return HubConfig.textScale(this) + "|" + HubConfig.language(this) + "|"
            + String.join("|", HubConfig.navLabels(HubConfig.localized(this)));
    }

    private void removeBar() {
        if (bar != null && wm != null) {
            try { wm.removeView(bar); } catch (RuntimeException ignored) {}
        }
        bar = null;
    }

    /** Cover the system navigation bar exactly when it is tall enough, else a touch taller. */
    private int barHeight() {
        int min = dp(Math.round(52 * shownScale));
        return Math.max(systemNavHeight(), min);
    }

    private int systemNavHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private TextView button(String label, int color, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17 * shownScale);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMaxLines(1);
        b.setPadding(dp(4), 0, dp(4), 0);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            // long words in other languages shrink instead of being cut off
            b.setAutoSizeTextTypeUniformWithConfiguration(10, Math.round(17 * shownScale), 1,
                TypedValue.COMPLEX_UNIT_SP);
        }
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(color);
        b.setBackground(d);
        b.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            l.onClick(v);
        });
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
