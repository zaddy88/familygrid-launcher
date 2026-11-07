package dev.fluxnova.familygrid;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

// LOGIC TIER: actions and state checks. No UI code here.
final class HubActions {
    static final int WIFI_NONE = 0;        // no Wi-Fi connection
    static final int WIFI_UNVERIFIED = 1;  // Wi-Fi linked but no validated internet
    static final int WIFI_OK = 2;          // Wi-Fi connected and internet validated

    /** Battery snapshot. level is 0-100, or -1 when unknown. */
    static final class Battery {
        final int level;
        final boolean charging;
        Battery(int level, boolean charging) { this.level = level; this.charging = charging; }
    }

    /** An installed app that can be put on a tile. */
    static final class AppEntry {
        final String label;
        final String pkg;
        AppEntry(String label, String pkg) { this.label = label; this.pkg = pkg; }
    }

    private HubActions() {}

    /** @return false when the tile's target could not be opened (e.g. app uninstalled). */
    static boolean launchTile(Context c, HubConfig.Tile t) {
        switch (t.type) {
            case HubConfig.TYPE_DIALER:  return openDialer(c, null);
            case HubConfig.TYPE_CONTACT: return callNumber(c, t.phone);
            default:                     return launchApp(c, t.pkg);
        }
    }

    static boolean launchApp(Context c, String pkg) {
        if (pkg == null) return false;
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return start(c, i);
    }

    static boolean isInstalled(Context c, String pkg) {
        return pkg != null && c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    /** Calls straight away when allowed and permitted; otherwise opens the dialer pre-filled. */
    static boolean callNumber(Context c, String number) {
        if (number == null || number.trim().isEmpty()) return openDialer(c, null);
        Uri uri = Uri.fromParts("tel", number.trim(), null);
        if (HubConfig.directCall(c) && canCallDirectly(c)) {
            Intent call = new Intent(Intent.ACTION_CALL, uri);
            call.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (start(c, call)) return true;
        }
        return openDialer(c, number);
    }

    static boolean canCallDirectly(Context c) {
        return c.checkSelfPermission(Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED;
    }

    static boolean openDialer(Context c, String number) {
        Intent i = new Intent(Intent.ACTION_DIAL);
        if (number != null) i.setData(Uri.fromParts("tel", number.trim(), null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return start(c, i);
    }

    static void openWifiSettings(Context c) {
        start(c, new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void openSettings(Context c) {
        start(c, new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void openBatterySettings(Context c) {
        Intent i = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!start(c, i)) openSettings(c);
    }

    // Generic jobs a default tile can stand for, resolved to whatever app this phone uses.
    static final String ROLE_SMS = "sms";
    static final String ROLE_CAMERA = "camera";
    static final String ROLE_GALLERY = "gallery";
    static final String ROLE_BROWSER = "browser";

    /** The package that handles a role on this phone, or null if there is no clear answer. */
    static String appFor(Context c, String role) {
        String pkg = null;
        try {
            switch (role) {
                case ROLE_SMS:
                    pkg = android.provider.Telephony.Sms.getDefaultSmsPackage(c);
                    break;
                case ROLE_CAMERA:
                    pkg = handler(c, new Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                    break;
                case ROLE_GALLERY:
                    pkg = isInstalled(c, "com.google.android.apps.photos") ? "com.google.android.apps.photos"
                        : handler(c, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY));
                    break;
                case ROLE_BROWSER:
                    pkg = handler(c, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER));
                    if (pkg == null) pkg = handler(c, new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")));
                    break;
                default:
                    break;
            }
        } catch (RuntimeException ignored) {}
        return isInstalled(c, pkg) ? pkg : null;
    }

    /** Default handler's package; null when Android would ask the user to choose. */
    private static String handler(Context c, Intent i) {
        ResolveInfo ri = c.getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo == null) return null;
        String pkg = ri.activityInfo.packageName;
        return "android".equals(pkg) ? null : pkg; // "android" = the "choose an app" screen
    }

    /**
     * Apps that may still notify in Quiet mode: everything on a tile, the extras the family
     * picked, and the essentials (calls, messages, alarms, Android itself).
     */
    static java.util.Set<String> quietAllowList(Context c) {
        java.util.Set<String> s = new java.util.HashSet<>(HubConfig.quietExtras(c));
        for (HubConfig.Tile t : HubConfig.load(c)) if (t.pkg != null) s.add(t.pkg);
        s.add(c.getPackageName());
        s.add("android");
        s.add("com.android.systemui");
        s.add("com.android.phone");
        s.add("com.android.server.telecom");
        s.add("com.android.providers.downloads");
        try {
            android.telecom.TelecomManager tm =
                (android.telecom.TelecomManager) c.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null && tm.getDefaultDialerPackage() != null) s.add(tm.getDefaultDialerPackage());
        } catch (RuntimeException ignored) {}
        String sms = appFor(c, ROLE_SMS);
        if (sms != null) s.add(sms);
        String clock = handler(c, new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS));
        if (clock != null) s.add(clock);
        return s;
    }

    /** This app's own page in Android settings (Permissions live there). */
    static void openAppDetails(Context c) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", c.getPackageName(), null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!start(c, i)) openSettings(c);
    }

    /** USB debugging gives a computer far more control than an app; it should be off after setup. */
    static boolean isUsbDebuggingOn(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0;
    }

    static void openDeveloperOptions(Context c) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!start(c, i)) openSettings(c);
    }

    static void openNotificationAccess(Context c) {
        Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!start(c, i)) openSettings(c);
    }

    /** true when pressing Home opens FamilyGrid (it is the chosen default home app). */
    static boolean isDefaultHome(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null
            && c.getPackageName().equals(ri.activityInfo.packageName);
    }

    static void openAccessibilitySettings(Context c) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!start(c, i)) openSettings(c);
    }

    /** Lets the user pick another home app (useful to switch away from FamilyGrid). */
    static void openHomeSettings(Context c) {
        Intent i = new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!start(c, i)) openSettings(c);
    }

    // Inspect every network and judge the Wi-Fi one specifically, so cellular
    // data being active cannot mask a broken Wi-Fi connection.
    static int wifiState(Context c) {
        ConnectivityManager cm =
            (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return WIFI_NONE;
        int best = WIFI_NONE;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc == null || !nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
            if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return WIFI_OK;
            best = WIFI_UNVERIFIED;
        }
        return best;
    }

    static Battery battery(Context c) {
        // ACTION_BATTERY_CHANGED is sticky: passing a null receiver just reads the last value.
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return new Battery(-1, false);
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            || status == BatteryManager.BATTERY_STATUS_FULL;
        int pct = (level < 0 || scale <= 0) ? -1 : Math.round(level * 100f / scale);
        return new Battery(pct, charging);
    }

    // ---------------- Bluetooth ----------------
    static final int BT_UNAVAILABLE = 0;  // no adapter on this phone
    static final int BT_OFF = 1;
    static final int BT_ON = 2;           // on, nothing connected
    static final int BT_CONNECTED = 3;    // a headset/speaker/car is connected
    static final int BT_NO_PERMISSION = 4;

    static final class Bluetooth {
        final int state;
        final String deviceName; // only for BT_CONNECTED, may be null
        Bluetooth(int state, String deviceName) { this.state = state; this.deviceName = deviceName; }
    }

    static boolean needsBluetoothPermission(Context c) {
        return Build.VERSION.SDK_INT >= 31
            && c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
               != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Reports Bluetooth state. Device names arrive asynchronously through a profile proxy,
     * so the callback runs once (on the main thread) when the answer is known.
     */
    @SuppressWarnings("deprecation")
    static void bluetooth(Context c, Consumer<Bluetooth> cb) {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) { cb.accept(new Bluetooth(BT_UNAVAILABLE, null)); return; }
        if (needsBluetoothPermission(c)) { cb.accept(new Bluetooth(BT_NO_PERMISSION, null)); return; }
        try {
            if (!a.isEnabled()) { cb.accept(new Bluetooth(BT_OFF, null)); return; }
            int profile = -1;
            if (a.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED) {
                profile = BluetoothProfile.A2DP;
            } else if (a.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED) {
                profile = BluetoothProfile.HEADSET;
            }
            if (profile < 0) { cb.accept(new Bluetooth(BT_ON, null)); return; }
            final int p = profile;
            boolean ok = a.getProfileProxy(c.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int prof, BluetoothProfile proxy) {
                    String name = null;
                    try {
                        List<BluetoothDevice> devs = proxy.getConnectedDevices();
                        if (!devs.isEmpty()) name = devs.get(0).getName();
                    } catch (SecurityException ignored) {}
                    a.closeProfileProxy(p, proxy);
                    cb.accept(new Bluetooth(BT_CONNECTED, name));
                }
                @Override public void onServiceDisconnected(int prof) {}
            }, p);
            if (!ok) cb.accept(new Bluetooth(BT_CONNECTED, null));
        } catch (SecurityException e) {
            cb.accept(new Bluetooth(BT_NO_PERMISSION, null));
        }
    }

    static void openBluetoothSettings(Context c) {
        Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!start(c, i)) openSettings(c);
    }

    /** All apps with a launcher entry, sorted by name, excluding FamilyGrid itself. */
    static List<AppEntry> launchableApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppEntry> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || seen.contains(pkg)) continue;
            seen.add(pkg);
            out.add(new AppEntry(String.valueOf(ri.loadLabel(pm)), pkg));
        }
        Collator col = Collator.getInstance();
        Collections.sort(out, (a, b) -> col.compare(a.label, b.label));
        return out;
    }

    /**
     * Reads name + number from a contact picked via ACTION_PICK on Phone.CONTENT_URI.
     * The picker grants temporary read access, so READ_CONTACTS is not needed.
     * @return {name, number}; entries are "" when unavailable.
     */
    static String[] readPickedContact(Context c, Uri uri) {
        String[] out = {"", ""};
        String[] cols = {
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (android.database.Cursor cur = c.getContentResolver().query(uri, cols, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                out[0] = cur.isNull(0) ? "" : cur.getString(0);
                out[1] = cur.isNull(1) ? "" : cur.getString(1);
            }
        } catch (RuntimeException ignored) {}
        return out;
    }

    private static boolean start(Context c, Intent i) {
        try {
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
