package dev.fluxnova.familygrid;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// UI TIER: builds the home screen in code and renders live Wi-Fi / battery state.
public class MainActivity extends Activity {
    private static final int COLUMNS = 3;
    private static final long SAFETY_TICK_MS = 10_000;   // re-check Wi-Fi validation now and then
    private static final long HOLD_TO_EDIT_MS = 2_000;   // hold the clock this long to open the editor
    private static final String PREF_HINT_SHOWN = "hint_shown";

    private TextView wifiChip;
    private TextView batteryChip;
    private TextView bluetoothChip;
    private float textScale = 1f;
    private String createdLang = "";
    private TextView clockTime;
    private TextView clockDate;
    private LinearLayout gridBox;
    private long loadedStamp = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback netCallback;

    private final Runnable safetyTick = new Runnable() {
        @Override public void run() {
            updateWifi();
            handler.postDelayed(this, SAFETY_TICK_MS);
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(a)
                || Intent.ACTION_POWER_CONNECTED.equals(a)
                || Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
                updateBattery();
            } else if (a != null && a.startsWith("android.bluetooth.")) {
                updateBluetooth();
                handler.postDelayed(MainActivity.this::updateBluetooth, 1500); // profiles settle late
            } else {
                updateClock();
            }
        }
    };

    private final Runnable openEditor = () -> {
        View root = getWindow().getDecorView();
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        SettingsActivity.openWithPin(this);
    };

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(HubConfig.localized(base));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        createdLang = HubConfig.language(this);
        if (!HubConfig.setupDone(this)) {
            startActivity(new Intent(this, SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_SETUP, true));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!createdLang.equals(HubConfig.language(this))) { recreate(); return; }
        // include the big-buttons service state: it decides whether "All apps" is needed here
        long stamp = HubConfig.settingsStamp(this) * 2 + (NavBarService.isEnabled(this) ? 1 : 0);
        if (stamp != loadedStamp) {
            loadedStamp = stamp;
            textScale = HubConfig.textScale(this);
            setContentView(buildUi());
            rebuildGrid();
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        f.addAction(Intent.ACTION_POWER_DISCONNECTED);
        f.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        f.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        f.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
        f.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        f.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        registerReceiver(statusReceiver, f);
        registerNetworkCallback();

        updateClock();
        updateBattery();
        updateBluetooth();
        safetyTick.run();
        if (HubConfig.setupDone(this)) maybeShowHint();
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (IllegalArgumentException ignored) {}
        unregisterNetworkCallback();
        handler.removeCallbacks(safetyTick);
        handler.removeCallbacks(openEditor);
    }

    // A home screen has nowhere to go "back" to; swallow it so nothing surprising happens.
    @Override public void onBackPressed() {}

    // ============================================================
    // LAYOUT
    // ============================================================

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF141B26, 0xFF0B0F16}));

        // Row 1: big clock (hold it for 2 s to open the editor) + date
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.HORIZONTAL);
        clockBox.setGravity(Gravity.BOTTOM);
        clockBox.setPadding(dp(22), dp(18), dp(22), dp(4));
        clockTime = new TextView(this);
        clockTime.setTextColor(Color.WHITE);
        sp(clockTime, 44);
        clockTime.setTypeface(Typeface.DEFAULT_BOLD);
        clockTime.setIncludeFontPadding(false);
        clockDate = new TextView(this);
        clockDate.setTextColor(0xFF8FA1B3);
        sp(clockDate, 19);
        clockDate.setTypeface(Typeface.DEFAULT_BOLD);
        clockDate.setGravity(Gravity.END);
        clockDate.setPadding(0, 0, 0, dp(4));
        clockBox.addView(clockTime);
        clockBox.addView(clockDate, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clockBox.setOnTouchListener(this::onClockTouch);

        // Row 2: equal-width status chips (each can be switched off in the editor)
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(14), dp(10), dp(14), dp(4));
        wifiChip = makeChip();
        wifiChip.setOnClickListener(v -> HubActions.openWifiSettings(this));
        batteryChip = makeChip();
        batteryChip.setOnClickListener(v -> HubActions.openBatterySettings(this));
        bluetoothChip = makeChip();
        bluetoothChip.setOnClickListener(v -> {
            if (HubActions.needsBluetoothPermission(this)) {
                requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 7);
            } else {
                HubActions.openBluetoothSettings(this);
            }
        });
        boolean any = false;
        if (HubConfig.isShown(this, HubConfig.SHOW_WIFI)) { addChip(chips, wifiChip); any = true; }
        if (HubConfig.isShown(this, HubConfig.SHOW_BATTERY)) { addChip(chips, batteryChip); any = true; }
        if (HubConfig.isShown(this, HubConfig.SHOW_BLUETOOTH)) { addChip(chips, bluetoothChip); any = true; }

        // Rows share the free height equally; past ~4 rows the list scrolls instead of shrinking.
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setPadding(dp(10), dp(6), dp(10), dp(16));
        scroll.addView(gridBox, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(clockBox);
        if (any) root.addView(chips);
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // the big-buttons bar already has an "Apps" button, so don't show it twice
        if (HubConfig.isShown(this, HubConfig.SHOW_ALL_APPS) && !NavBarService.isEnabled(this)) {
            TextView all = new TextView(this);
            all.setText(R.string.home_all_apps);
            all.setTextColor(Color.WHITE);
            sp(all, 20);
            all.setTypeface(Typeface.DEFAULT_BOLD);
            all.setGravity(Gravity.CENTER);
            all.setPadding(dp(12), dp(14), dp(12), dp(14));
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(22));
            d.setColor(0xFF1C2532);
            d.setStroke(dp(2), 0xFF2E3B4E);
            all.setBackground(d);
            all.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            al.setMargins(dp(18), 0, dp(18), dp(14));
            root.addView(all, al);
        }
        return root;
    }

    private TextView makeChip() {
        TextView chip = new TextView(this);
        sp(chip, 15);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(2);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(dp(6), dp(8), dp(6), dp(8));
        return chip;
    }

    private void addChip(LinearLayout row, TextView chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(chip, lp);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        updateBluetooth();
    }

    /** Sets a font size in sp, multiplied by the user's chosen text size. */
    private void sp(TextView v, float size) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * textScale);
    }

    private void rebuildGrid() {
        List<HubConfig.Tile> tiles = HubConfig.load(this);
        gridBox.removeAllViews();

        if (tiles.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.empty_tiles);
            empty.setTextColor(0xFF8FA1B3);
            sp(empty, 20);
            empty.setGravity(Gravity.CENTER);
            gridBox.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
                row.setMinimumHeight(dp(Math.round(130 * textScale)));
                gridBox.addView(row, rl);
            }
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            tl.setMargins(dp(8), dp(8), dp(8), dp(8));
            row.addView(makeTile(tiles.get(i)), tl);
        }
        // pad the last row with invisible cells so its tiles keep the same width
        int rest = tiles.size() % COLUMNS;
        if (rest != 0) {
            for (int i = rest; i < COLUMNS; i++) {
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, 1, 1f);
                tl.setMargins(dp(8), dp(8), dp(8), dp(8));
                row.addView(new View(this), tl);
            }
        }
    }

    private View makeTile(HubConfig.Tile t) {
        boolean available = !HubConfig.TYPE_APP.equals(t.type) || HubActions.isInstalled(this, t.pkg);

        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6), dp(10), dp(6), dp(10));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{t.colorStart, t.colorEnd});
        bg.setCornerRadius(dp(22));
        tile.setBackground(bg);
        tile.setElevation(dp(4));
        if (!available) tile.setAlpha(0.45f);

        int iconSize = dp(Math.round(52 * textScale));
        tile.addView(makeIcon(t), new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView label = new TextView(this);
        label.setText(t.label);
        label.setTextColor(Color.WHITE);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setShadowLayer(4f, 0f, 2f, 0x66000000);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        int maxSp = Math.round(19 * textScale);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, maxSp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // shrink long names to fit instead of cutting them off
            label.setAutoSizeTextTypeUniformWithConfiguration(11, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(Math.round(34 * textScale)));
        ll.topMargin = dp(8);
        tile.addView(label, ll);

        if (!available) {
            TextView note = new TextView(this);
            note.setText(R.string.not_installed);
            note.setTextColor(Color.WHITE);
            sp(note, 12);
            note.setGravity(Gravity.CENTER);
            tile.addView(note);
        }

        tile.setContentDescription(t.label);
        tile.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (!HubActions.launchTile(this, t)) {
                Toast.makeText(this, getString(R.string.tile_unavailable, t.label),
                    Toast.LENGTH_LONG).show();
            }
        });
        tile.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
                default: break;
            }
            return false;
        });
        return tile;
    }

    private View makeIcon(HubConfig.Tile t) {
        if (HubConfig.TYPE_CONTACT.equals(t.type)) {
            TextView badge = new TextView(this);
            badge.setText(t.glyph);
            badge.setTextColor(t.colorEnd);
            sp(badge, 24);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.WHITE);
            badge.setBackground(circle);
            return badge;
        }
        Drawable d = appIcon(t);
        if (d != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return iv;
        }
        TextView glyph = new TextView(this);
        glyph.setText(t.glyph);
        sp(glyph, 34);
        glyph.setTextColor(Color.WHITE);
        glyph.setGravity(Gravity.CENTER);
        return glyph;
    }

    private Drawable appIcon(HubConfig.Tile t) {
        try {
            if (HubConfig.TYPE_APP.equals(t.type)) {
                return getPackageManager().getApplicationIcon(t.pkg);
            }
            android.content.pm.ResolveInfo ri = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_DIAL), 0);
            if (ri != null && ri.activityInfo != null) {
                return getPackageManager().getApplicationIcon(ri.activityInfo.packageName);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ============================================================
    // HIDDEN EDITOR ENTRY: hold the clock for 2 seconds
    // ============================================================

    private boolean onClockTouch(View v, MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                v.setAlpha(0.6f);
                handler.postDelayed(openEditor, HOLD_TO_EDIT_MS);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                v.setAlpha(1f);
                handler.removeCallbacks(openEditor);
                return true;
            default:
                return true;
        }
    }

    private void maybeShowHint() {
        android.content.SharedPreferences p = getSharedPreferences("familygrid", MODE_PRIVATE);
        if (p.getBoolean(PREF_HINT_SHOWN, false)) return;
        p.edit().putBoolean(PREF_HINT_SHOWN, true).apply();
        Toast.makeText(this, R.string.hint_hold_clock,
            Toast.LENGTH_LONG).show();
    }

    // ============================================================
    // LIVE STATUS
    // ============================================================

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null || netCallback != null) return;
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { postWifi(); }
            @Override public void onLost(Network n) { postWifi(); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { postWifi(); }
        };
        try {
            cm.registerNetworkCallback(new android.net.NetworkRequest.Builder().build(), netCallback);
        } catch (RuntimeException e) {
            netCallback = null;
        }
    }

    private void unregisterNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null && netCallback != null) {
            try { cm.unregisterNetworkCallback(netCallback); } catch (RuntimeException ignored) {}
        }
        netCallback = null;
    }

    private void postWifi() {
        handler.post(this::updateWifi);
    }

    private void updateClock() {
        Date now = new Date();
        if (DateFormat.is24HourFormat(this)) {
            clockTime.setText(new SimpleDateFormat("HH:mm", locale()).format(now));
        } else {
            // 12-hour phones: show AM/PM smaller next to the time so morning/evening is clear
            String hm = new SimpleDateFormat("h:mm", locale()).format(now);
            String ampm = new SimpleDateFormat(" a", locale()).format(now);
            android.text.SpannableString s = new android.text.SpannableString(hm + ampm);
            s.setSpan(new android.text.style.RelativeSizeSpan(0.45f), hm.length(), s.length(), 0);
            clockTime.setText(s);
        }
        Locale loc = locale();
        clockDate.setText(new SimpleDateFormat(
            DateFormat.getBestDateTimePattern(loc, "EEEdMMM"), loc).format(now));
    }

    private void updateWifi() {
        if (wifiChip == null) return;
        switch (HubActions.wifiState(this)) {
            case HubActions.WIFI_OK:
                styleChip(wifiChip, getString(R.string.chip_wifi_ok), 0xFF123D1E, 0xFF7CE89B);
                break;
            case HubActions.WIFI_UNVERIFIED:
                styleChip(wifiChip, getString(R.string.chip_wifi_no_internet), 0xFF4A2A00, 0xFFFFB74D);
                break;
            default:
                styleChip(wifiChip, getString(R.string.chip_wifi_off), 0xFF431322, 0xFFFF8A9B);
                break;
        }
    }

    private void updateBattery() {
        if (batteryChip == null) return;
        HubActions.Battery b = HubActions.battery(this);
        if (b.level < 0) { batteryChip.setVisibility(View.GONE); return; }
        batteryChip.setVisibility(View.VISIBLE);
        String text = getString(b.charging ? R.string.chip_charging : R.string.chip_battery, b.level);
        if (b.charging) {
            styleChip(batteryChip, text, 0xFF0F2E45, 0xFF7CC8FF);
        } else if (b.level <= 15) {
            styleChip(batteryChip, getString(R.string.chip_charge_me, b.level), 0xFF431322, 0xFFFF8A9B);
        } else if (b.level <= 30) {
            styleChip(batteryChip, text, 0xFF4A2A00, 0xFFFFB74D);
        } else {
            styleChip(batteryChip, text, 0xFF1C2532, 0xFFC9D4E0);
        }
    }

    private void updateBluetooth() {
        if (bluetoothChip == null || bluetoothChip.getParent() == null) return;
        HubActions.bluetooth(this, bt -> runOnUiThread(() -> {
            switch (bt.state) {
                case HubActions.BT_CONNECTED:
                    styleChip(bluetoothChip, bt.deviceName != null
                        ? getString(R.string.chip_bt_connected, bt.deviceName)
                        : getString(R.string.chip_bt_connected_unnamed), 0xFF0F2E45, 0xFF7CC8FF);
                    break;
                case HubActions.BT_ON:
                    styleChip(bluetoothChip, getString(R.string.chip_bt_on), 0xFF1C2532, 0xFFC9D4E0);
                    break;
                case HubActions.BT_NO_PERMISSION:
                    styleChip(bluetoothChip, getString(R.string.chip_bt_allow), 0xFF1C2532, 0xFFC9D4E0);
                    break;
                case HubActions.BT_UNAVAILABLE:
                    bluetoothChip.setVisibility(View.GONE);
                    break;
                default:
                    styleChip(bluetoothChip, getString(R.string.chip_bt_off), 0xFF1C2532, 0xFF8FA1B3);
                    break;
            }
        }));
    }

    private void styleChip(TextView chip, String text, int bg, int fg) {
        chip.setText(text);
        chip.setTextColor(fg);
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(18));
        d.setColor(bg);
        chip.setBackground(d);
    }

    private Locale locale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
