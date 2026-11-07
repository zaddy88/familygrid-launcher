package dev.fluxnova.familygrid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DATA + CONFIG TIER
 * Tiles live in tiles.json (internal storage); small settings (PIN, call mode)
 * live in SharedPreferences. No UI or intent code here.
 */
final class HubConfig {

    private static final String CONFIG_FILE = "tiles.json";
    private static final int CONFIG_VERSION = 2;
    private static final String PREFS = "familygrid";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_DIRECT_CALL = "direct_call";

    static final String TYPE_APP = "app";         // launches an installed app (pkg)
    static final String TYPE_DIALER = "dialer";   // opens the phone's dialer
    static final String TYPE_CONTACT = "contact"; // speed-dial a phone number

    /** Gradient pairs offered to new tiles, cycled by position. */
    static final int[][] PALETTE = {
        {0xFF42A5F5, 0xFF0D47A1}, // blue
        {0xFF25D366, 0xFF0E7A55}, // green
        {0xFFFF5252, 0xFFB71C1C}, // red
        {0xFFFFD54F, 0xFFF57C00}, // amber
        {0xFFAB47BC, 0xFF4A148C}, // purple
        {0xFF26A69A, 0xFF00473D}, // teal
        {0xFFFF8A65, 0xFFBF360C}, // orange
        {0xFFEC407A, 0xFF880E4F}, // pink
        {0xFF78909C, 0xFF37474F}, // slate
    };

    static final class Tile {
        final String type;
        final String label;
        final String pkg;      // TYPE_APP only
        final String phone;    // TYPE_CONTACT only
        final String glyph;    // emoji/initials fallback when no icon is available
        final int colorStart;  // gradient top-left
        final int colorEnd;    // gradient bottom-right

        Tile(String type, String label, String pkg, String phone, String glyph,
             int colorStart, int colorEnd) {
            this.type = type;
            this.label = label;
            this.pkg = pkg;
            this.phone = phone;
            this.glyph = glyph;
            this.colorStart = colorStart;
            this.colorEnd = colorEnd;
        }

        static Tile app(String label, String pkg, String glyph, int[] colors) {
            return new Tile(TYPE_APP, label, pkg, null, glyph, colors[0], colors[1]);
        }

        static Tile contact(String name, String phone, int[] colors) {
            return new Tile(TYPE_CONTACT, name, null, phone, initials(name), colors[0], colors[1]);
        }

        Tile withLabel(String newLabel) {
            String g = TYPE_CONTACT.equals(type) ? initials(newLabel) : glyph;
            return new Tile(type, newLabel, pkg, phone, g, colorStart, colorEnd);
        }

        Tile withColors(int[] colors) {
            return new Tile(type, label, pkg, phone, glyph, colors[0], colors[1]);
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("label", label);
            if (pkg != null) o.put("pkg", pkg);
            if (phone != null) o.put("phone", phone);
            o.put("glyph", glyph);
            o.put("colorStart", colorStart);
            o.put("colorEnd", colorEnd);
            return o;
        }

        static Tile fromJson(JSONObject o) {
            String pkg = o.isNull("pkg") ? null : o.optString("pkg", null);
            String phone = o.isNull("phone") ? null : o.optString("phone", null);
            String type = o.optString("type", "");
            if (type.isEmpty()) {
                // v1 files had no type: a null pkg meant "dialer"
                type = pkg == null ? TYPE_DIALER : TYPE_APP;
            }
            return new Tile(type, o.optString("label"), pkg, phone, o.optString("glyph"),
                o.optInt("colorStart", PALETTE[0][0]), o.optInt("colorEnd", PALETTE[0][1]));
        }
    }

    private HubConfig() {}

    // ============================================================
    // TILES
    // ============================================================

    /** Load tiles. Creates the default config on first run or if the file is corrupt. */
    static List<Tile> load(Context context) {
        File file = configFile(context);
        if (file.exists()) {
            try {
                List<Tile> tiles = parse(readFile(file));
                if (tiles != null) return tiles;
            } catch (Exception ignored) {
                // corrupt or unreadable: fall through to defaults
            }
        }
        List<Tile> defaults = defaultTiles(context);
        save(context, defaults);
        return defaults;
    }

    static boolean save(Context context, List<Tile> tiles) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", CONFIG_VERSION);
            JSONArray arr = new JSONArray();
            for (Tile t : tiles) arr.put(t.toJson());
            root.put("tiles", arr);
            // write to a temp file then rename, so a crash never leaves half a file
            File tmp = new File(context.getFilesDir(), CONFIG_FILE + ".tmp");
            writeFile(tmp, root.toString(2));
            return tmp.renameTo(configFile(context));
        } catch (Exception e) {
            return false;
        }
    }

    static void resetToDefaults(Context context) {
        save(context, defaultTiles(context));
    }

    /** Cheap change detector so the home screen only rebuilds when tiles changed. */
    static long lastModified(Context context) {
        return configFile(context).lastModified();
    }

    static int[] colorsFor(int index) {
        return PALETTE[Math.floorMod(index, PALETTE.length)];
    }

    // ============================================================
    // SETTINGS (PIN lock, call mode)
    // ============================================================

    static boolean hasPin(Context c) {
        return prefs(c).getString(KEY_PIN_HASH, null) != null;
    }

    static boolean checkPin(Context c, String pin) {
        String stored = prefs(c).getString(KEY_PIN_HASH, null);
        return stored == null || stored.equals(hash(pin));
    }

    /** Pass null or empty to remove the PIN. */
    static void setPin(Context c, String pin) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (pin == null || pin.isEmpty()) e.remove(KEY_PIN_HASH);
        else e.putString(KEY_PIN_HASH, hash(pin));
        e.apply();
    }

    /** true: contact tiles call immediately; false: they open the dialer with the number filled in. */
    static boolean directCall(Context c) {
        return prefs(c).getBoolean(KEY_DIRECT_CALL, true);
    }

    static void setDirectCall(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_DIRECT_CALL, on).apply();
    }

    // Text size presets shown in setup/editor. Multiplies every font on the home screen.
    static final float[] TEXT_SCALES = {1.0f, 1.2f, 1.4f};
    static final int[] TEXT_SCALE_NAMES = {R.string.text_normal, R.string.text_large, R.string.text_xlarge};

    static int textScaleIndex(Context c) {
        int i = prefs(c).getInt("text_scale", 1);
        return Math.max(0, Math.min(TEXT_SCALES.length - 1, i));
    }

    static float textScale(Context c) {
        return TEXT_SCALES[textScaleIndex(c)];
    }

    static void setTextScaleIndex(Context c, int i) {
        prefs(c).edit().putInt("text_scale", i).apply();
    }

    // Grid style: how many tiles per row. Rows follow from the number of tiles
    // (and scroll past ~4 rows), so only the column count is a choice.
    static final int[] COLUMN_CHOICES = {2, 3, 4};
    static final int[] COLUMN_NAMES = {R.string.grid_2, R.string.grid_3, R.string.grid_4};

    static int columns(Context c) {
        int v = prefs(c).getInt("columns", 3);
        return (v >= 2 && v <= 4) ? v : 3;
    }

    static void setColumns(Context c, int cols) {
        prefs(c).edit().putInt("columns", cols).apply();
    }

    // Optional status chips and buttons on the home screen.
    static final String SHOW_WIFI = "show_wifi";
    static final String SHOW_BATTERY = "show_battery";
    static final String SHOW_BLUETOOTH = "show_bluetooth";
    static final String SHOW_ALL_APPS = "show_all_apps";

    static boolean isShown(Context c, String key) {
        boolean def = !SHOW_BLUETOOTH.equals(key); // Bluetooth chip is opt-in
        return prefs(c).getBoolean(key, def);
    }

    static void setShown(Context c, String key, boolean on) {
        prefs(c).edit().putBoolean(key, on).apply();
    }

    // Labels on the big Back / Home / Apps bar, editable so families can use their own language.
    static final String[] NAV_KEYS = {"nav_back", "nav_home", "nav_apps"};
    static final int[] NAV_DEFAULTS = {R.string.nav_back, R.string.nav_home, R.string.nav_apps};

    static String[] navLabels(Context c) {
        String[] out = new String[NAV_KEYS.length];
        for (int i = 0; i < out.length; i++) {
            String v = prefs(c).getString(NAV_KEYS[i], "");
            out[i] = v.trim().isEmpty() ? c.getString(NAV_DEFAULTS[i]) : v;
        }
        return out;
    }

    /** Empty strings restore the default label. */
    static void setNavLabels(Context c, String[] labels) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int i = 0; i < NAV_KEYS.length; i++) e.putString(NAV_KEYS[i], labels[i].trim());
        e.apply();
    }

    // ============================================================
    // LANGUAGE (in-app choice, independent of the phone's own language)
    // ============================================================

    /** Language tags offered in the editor; "" follows the phone's language. */
    static final String[] LANGUAGE_TAGS = {"", "en", "ur", "ar", "hi", "bn", "es"};
    /** Each language named in its own script, so anyone can find theirs. */
    static final String[] LANGUAGE_NAMES = {null, "English", "اردو", "العربية", "हिन्दी", "বাংলা", "Español"};

    static String language(Context c) {
        return prefs(c).getString("lang", "");
    }

    static void setLanguage(Context c, String tag) {
        prefs(c).edit().putString("lang", tag).apply();
    }

    /** Wraps a context so its resources use the chosen app language (and its text direction). */
    static Context localized(Context base) {
        String tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "");
        Locale loc = tag.isEmpty() ? Locale.getDefault() : Locale.forLanguageTag(tag);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(loc);
        cfg.setLayoutDirection(loc);
        return base.createConfigurationContext(cfg);
    }

    /**
     * After a language switch, renames tiles that still carry a built-in name
     * ("Phone", "Camera"...) to the new language. Names the family typed stay untouched.
     */
    static List<Tile> relabelDefaults(Context oldLang, Context newLang, List<Tile> tiles) {
        int[] ids = {R.string.tile_phone, R.string.tile_camera, R.string.tile_photos,
                     R.string.tile_messages, R.string.tile_browser};
        List<Tile> out = new ArrayList<>();
        for (Tile t : tiles) {
            Tile r = t;
            for (int id : ids) {
                if (t.label.equals(oldLang.getString(id))) { r = t.withLabel(newLang.getString(id)); break; }
            }
            out.add(r);
        }
        return out;
    }

    /** First-run setup has been completed (or skipped). */
    static boolean setupDone(Context c) {
        return prefs(c).getBoolean("setup_done", false);
    }

    static void setSetupDone(Context c) {
        prefs(c).edit().putBoolean("setup_done", true).apply();
    }

    /** Bumped whenever any setting changes, so the home screen knows to rebuild. */
    static long settingsStamp(Context c) {
        return prefs(c).getLong("stamp", 0) + lastModified(c);
    }

    static void touch(Context c) {
        prefs(c).edit().putLong("stamp", System.currentTimeMillis()).apply();
    }

    /** The built-in suggestions, offered as pre-ticked choices during first-run setup. */
    static List<Tile> suggestedTiles(Context c) {
        return defaultTiles(c);
    }

    // ============================================================
    // INTERNAL
    // ============================================================

    private static List<Tile> parse(String json) throws JSONException {
        JSONArray arr = new JSONObject(json).optJSONArray("tiles");
        if (arr == null) return null;
        List<Tile> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) result.add(Tile.fromJson(o));
        }
        return result;
    }

    /**
     * Built-in suggestions. Brand apps use fixed package names; generic jobs (messages,
     * camera, photos, internet) are looked up on each phone, so they work on any brand.
     * Anything this phone doesn't have is simply left out.
     */
    private static List<Tile> defaultTiles(Context c) {
        List<Tile> l = new ArrayList<>();
        l.add(new Tile(TYPE_DIALER, c.getString(R.string.tile_phone), null, null, "📞", 0xFF42A5F5, 0xFF0D47A1));
        addApp(l, "WhatsApp", "com.whatsapp", "💬", 0xFF25D366, 0xFF0E7A55);
        addApp(l, c.getString(R.string.tile_messages), HubActions.appFor(c, HubActions.ROLE_SMS), "✉️", 0xFF26A69A, 0xFF00473D);
        addApp(l, c.getString(R.string.tile_camera), HubActions.appFor(c, HubActions.ROLE_CAMERA), "📷", 0xFF78909C, 0xFF37474F);
        addApp(l, c.getString(R.string.tile_photos), HubActions.appFor(c, HubActions.ROLE_GALLERY), "🖼️", 0xFFFFD54F, 0xFFF57C00);
        addApp(l, "YouTube", "com.google.android.youtube", "▶", 0xFFFF5252, 0xFFB71C1C);
        addApp(l, "Facebook", "com.facebook.katana", "f", 0xFF4A7DF2, 0xFF0B3D91);
        addApp(l, "Messenger", "com.facebook.orca", "💬", 0xFF00B2FF, 0xFF0062CC);
        addApp(l, c.getString(R.string.tile_browser), HubActions.appFor(c, HubActions.ROLE_BROWSER), "🌐", 0xFFFF8A65, 0xFFBF360C);
        return l;
    }

    private static void addApp(List<Tile> l, String label, String pkg, String glyph, int a, int b) {
        if (pkg != null) l.add(Tile.app(label, pkg, glyph, new int[]{a, b}));
    }

    static String initials(String name) {
        if (name == null) return "?";
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && sb.length() < 2) sb.appendCodePoint(part.codePointAt(0));
        }
        return sb.length() == 0 ? "?" : sb.toString().toUpperCase();
    }

    private static File configFile(Context c) {
        return new File(c.getFilesDir(), CONFIG_FILE);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String hash(String pin) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                .digest(("familygrid:" + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin;
        }
    }

    private static String readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.getFD().sync();
        }
    }
}
