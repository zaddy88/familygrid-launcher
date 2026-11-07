package dev.fluxnova.familygrid;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * UI TIER: tile editor + settings. Also used as the first-run setup screen
 * (EXTRA_SETUP) so a new install starts from apps that are actually on the phone.
 */
public class SettingsActivity extends Activity {
    static final String EXTRA_SETUP = "setup";
    private static final int REQ_PICK_CONTACT = 1;
    private static final int REQ_CALL_PERMISSION = 2;

    private boolean setupMode;
    private List<HubConfig.Tile> tiles;
    private LinearLayout tileList;
    private CheckBox directCallBox;
    private LinearLayout textSizeRow;
    private Button pinButton;
    private Button removePinButton;
    private Button navBarButton;

    /** Opens the editor, asking for the PIN first if one is set. */
    static void openWithPin(Activity from) {
        if (!HubConfig.hasPin(from)) {
            from.startActivity(new Intent(from, SettingsActivity.class));
            return;
        }
        EditText pin = pinField(from);
        new AlertDialog.Builder(from, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(R.string.enter_pin)
            .setView(padded(from, pin))
            .setPositiveButton(R.string.open, (d, w) -> {
                if (HubConfig.checkPin(from, pin.getText().toString())) {
                    from.startActivity(new Intent(from, SettingsActivity.class));
                } else {
                    Toast.makeText(from, R.string.wrong_pin, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(HubConfig.localized(base));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setupMode = getIntent().getBooleanExtra(EXTRA_SETUP, false) && !HubConfig.setupDone(this);
        if (setupMode) {
            tiles = new ArrayList<>();
            for (HubConfig.Tile t : HubConfig.suggestedTiles(this)) {
                if (!HubConfig.TYPE_APP.equals(t.type) || HubActions.isInstalled(this, t.pkg)) tiles.add(t);
            }
            saveTiles();
        } else {
            tiles = HubConfig.load(this);
        }
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        if (navBarButton != null) {
            navBarButton.setText(NavBarService.isEnabled(this)
                ? getString(R.string.navbar_is_on) : getString(R.string.navbar_turn_on));
        }
    }

    @Override public void onBackPressed() {
        finishEditing();
    }

    // ============================================================
    // LAYOUT
    // ============================================================

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF4F6F9);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(24), dp(18), dp(32));
        scroll.addView(col);

        TextView title = text(getString(setupMode ? R.string.title_welcome : R.string.title_edit), 28, true);
        title.setTextColor(0xFF0B0F16);
        col.addView(title);
        if (setupMode) {
            col.addView(text(getString(R.string.setup_intro), 17, false));
        }

        // ---- language (first, so setup can be done in the family's own language) ----
        col.addView(section(languageHeading()));
        col.addView(button("🌐  " + currentLanguageName(), v -> pickLanguage()));

        // ---- tiles ----
        col.addView(section(getString(R.string.section_tiles)));
        col.addView(text(getString(R.string.tiles_help), 15, false));
        tileList = new LinearLayout(this);
        tileList.setOrientation(LinearLayout.VERTICAL);
        col.addView(tileList);
        renderTiles();
        col.addView(button(getString(R.string.add_app), v -> addApp()));
        col.addView(button(getString(R.string.add_person), v -> addContact()));

        // ---- text size ----
        col.addView(section(getString(R.string.section_text_size)));
        textSizeRow = new LinearLayout(this);
        textSizeRow.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(textSizeRow);
        renderTextSize();

        // ---- what to show ----
        col.addView(section(getString(R.string.section_show)));
        col.addView(toggle(getString(R.string.show_wifi), HubConfig.SHOW_WIFI));
        col.addView(toggle(getString(R.string.show_battery), HubConfig.SHOW_BATTERY));
        col.addView(toggle(getString(R.string.show_bluetooth), HubConfig.SHOW_BLUETOOTH));
        col.addView(toggle(getString(R.string.show_all_apps), HubConfig.SHOW_ALL_APPS));

        // ---- big navigation buttons ----
        col.addView(section(getString(R.string.section_navbar)));
        col.addView(text(getString(R.string.navbar_help), 15, false));
        navBarButton = button("", v -> HubActions.openAccessibilitySettings(this));
        col.addView(navBarButton);
        col.addView(button(getString(R.string.navbar_rename), v -> renameNavButtons()));

        // ---- calling ----
        col.addView(section(getString(R.string.section_calling)));
        directCallBox = checkbox(getString(R.string.direct_call));
        directCallBox.setChecked(HubConfig.directCall(this) && HubActions.canCallDirectly(this));
        directCallBox.setOnCheckedChangeListener((v, on) -> {
            HubConfig.setDirectCall(this, on);
            if (on && !HubActions.canCallDirectly(this)) {
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL_PERMISSION);
            }
        });
        col.addView(directCallBox);

        // ---- lock ----
        col.addView(section(getString(R.string.section_lock)));
        col.addView(text(getString(R.string.lock_help), 15, false));
        pinButton = button("", v -> setPin());
        removePinButton = button(getString(R.string.remove_pin), v -> {
            HubConfig.setPin(this, null);
            renderPin();
            Toast.makeText(this, R.string.pin_removed, Toast.LENGTH_SHORT).show();
        });
        col.addView(pinButton);
        col.addView(removePinButton);
        renderPin();

        // ---- other ----
        col.addView(section(getString(R.string.section_more)));
        col.addView(button(getString(R.string.choose_home), v -> HubActions.openHomeSettings(this)));
        col.addView(button(getString(R.string.phone_settings), v -> HubActions.openSettings(this)));
        col.addView(button(getString(R.string.reset_tiles), v -> confirm(
            getString(R.string.reset_confirm_title), getString(R.string.reset_confirm_msg), () -> {
                HubConfig.resetToDefaults(this);
                tiles = HubConfig.load(this);
                renderTiles();
            })));

        Button done = button(getString(setupMode ? R.string.finish_setup : R.string.done), v -> finishEditing());
        done.setBackground(rounded(0xFF0D47A1, 0));
        done.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams dl = (LinearLayout.LayoutParams) done.getLayoutParams();
        dl.topMargin = dp(28);
        col.addView(done);
        return scroll;
    }

    private void renderTiles() {
        tileList.removeAllViews();
        if (tiles.isEmpty()) {
            tileList.addView(text(getString(R.string.no_tiles_editor), 16, false));
        }
        for (int i = 0; i < tiles.size(); i++) {
            final int idx = i;
            HubConfig.Tile t = tiles.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
            rl.topMargin = dp(8);
            row.setLayoutParams(rl);

            TextView name = text(t.label + subtitle(t), 18, true);
            name.setTextColor(Color.WHITE);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setPadding(dp(14), 0, dp(8), 0);
            name.setSingleLine(true);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{t.colorStart, t.colorEnd});
            g.setCornerRadius(dp(14));
            name.setBackground(g);
            name.setOnClickListener(v -> editTile(idx));
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            row.addView(iconButton("▲", idx > 0, v -> move(idx, -1)));
            row.addView(iconButton("▼", idx < tiles.size() - 1, v -> move(idx, 1)));
            row.addView(iconButton("✕", true, v -> confirm(getString(R.string.remove_tile_q, t.label), null, () -> {
                tiles.remove(idx);
                saveTiles();
                renderTiles();
            })));
            tileList.addView(row);
        }
    }

    private String subtitle(HubConfig.Tile t) {
        if (HubConfig.TYPE_CONTACT.equals(t.type)) return "  ·  " + t.phone;
        if (HubConfig.TYPE_APP.equals(t.type) && !HubActions.isInstalled(this, t.pkg)) {
            return "  ·  " + getString(R.string.tile_not_installed);
        }
        return "";
    }

    private void renderTextSize() {
        textSizeRow.removeAllViews();
        int current = HubConfig.textScaleIndex(this);
        for (int i = 0; i < HubConfig.TEXT_SCALES.length; i++) {
            final int idx = i;
            Button btn = new Button(this);
            btn.setAllCaps(false);
            btn.setText(HubConfig.TEXT_SCALE_NAMES[i]);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15 * HubConfig.TEXT_SCALES[i]);
            boolean sel = i == current;
            btn.setTextColor(sel ? Color.WHITE : 0xFF0B0F16);
            btn.setBackground(rounded(sel ? 0xFF0D47A1 : Color.WHITE, sel ? 0 : 0xFFCBD3DD));
            btn.setOnClickListener(v -> {
                HubConfig.setTextScaleIndex(this, idx);
                HubConfig.touch(this);
                renderTextSize();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
            lp.setMargins(dp(3), dp(6), dp(3), 0);
            textSizeRow.addView(btn, lp);
        }
    }

    private void renderPin() {
        boolean has = HubConfig.hasPin(this);
        pinButton.setText(has ? R.string.change_pin : R.string.set_pin);
        removePinButton.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    private void move(int idx, int delta) {
        int to = idx + delta;
        if (to < 0 || to >= tiles.size()) return;
        tiles.add(to, tiles.remove(idx));
        saveTiles();
        renderTiles();
    }

    private void editTile(int idx) {
        HubConfig.Tile t = tiles.get(idx);
        new AlertDialog.Builder(this)
            .setTitle(t.label)
            .setItems(new String[]{getString(R.string.rename), getString(R.string.change_colour)}, (d, which) -> {
                if (which == 0) renameTile(idx);
                else pickColour(idx);
            })
            .show();
    }

    private void renameTile(int idx) {
        EditText field = new EditText(this);
        field.setText(tiles.get(idx).label);
        field.setSelectAllOnFocus(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        new AlertDialog.Builder(this)
            .setTitle(R.string.new_name)
            .setView(padded(this, field))
            .setPositiveButton(R.string.save, (d, w) -> {
                String s = field.getText().toString().trim();
                if (s.isEmpty()) return;
                tiles.set(idx, tiles.get(idx).withLabel(s));
                saveTiles();
                renderTiles();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void pickColour(int idx) {
        String[] names = getResources().getStringArray(R.array.colour_names);
        new AlertDialog.Builder(this)
            .setTitle(R.string.colour)
            .setItems(names, (d, which) -> {
                tiles.set(idx, tiles.get(idx).withColors(HubConfig.PALETTE[which]));
                saveTiles();
                renderTiles();
            })
            .show();
    }

    private void addApp() {
        List<HubActions.AppEntry> apps = HubActions.launchableApps(this);
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.phone_keypad);
        for (int i = 0; i < apps.size(); i++) labels[i + 1] = apps.get(i).label;
        new AlertDialog.Builder(this)
            .setTitle(R.string.add_app_title)
            .setItems(labels, (d, which) -> {
                int[] colors = HubConfig.colorsFor(tiles.size());
                if (which == 0) {
                    tiles.add(new HubConfig.Tile(HubConfig.TYPE_DIALER, getString(R.string.tile_phone), null, null, "📞",
                        colors[0], colors[1]));
                } else {
                    HubActions.AppEntry a = apps.get(which - 1);
                    String glyph = a.label.isEmpty() ? "?" : a.label.substring(0, 1);
                    tiles.add(HubConfig.Tile.app(a.label, a.pkg, glyph, colors));
                }
                saveTiles();
                renderTiles();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void addContact() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.add_person_title)
            .setItems(new String[]{getString(R.string.pick_contact), getString(R.string.type_contact)}, (d, which) -> {
                if (which == 0) {
                    try {
                        startActivityForResult(new Intent(Intent.ACTION_PICK,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_PICK_CONTACT);
                    } catch (Exception e) {
                        typeContact("", "");
                    }
                } else {
                    typeContact("", "");
                }
            })
            .show();
    }

    private void typeContact(String name, String number) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText nameField = new EditText(this);
        nameField.setHint(R.string.hint_name);
        nameField.setText(name);
        nameField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText numField = new EditText(this);
        numField.setHint(R.string.hint_number);
        numField.setText(number);
        numField.setInputType(InputType.TYPE_CLASS_PHONE);
        box.addView(nameField);
        box.addView(numField);
        new AlertDialog.Builder(this)
            .setTitle(R.string.add_person_title)
            .setView(padded(this, box))
            .setPositiveButton(R.string.add, (d, w) -> {
                String n = nameField.getText().toString().trim();
                String p = numField.getText().toString().trim();
                if (n.isEmpty() || p.isEmpty()) {
                    Toast.makeText(this, R.string.need_name_number, Toast.LENGTH_LONG).show();
                    return;
                }
                tiles.add(HubConfig.Tile.contact(n, p, HubConfig.colorsFor(tiles.size())));
                saveTiles();
                renderTiles();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK_CONTACT || res != RESULT_OK || data == null || data.getData() == null) return;
        String[] picked = HubActions.readPickedContact(this, data.getData());
        // show the form pre-filled so the name can be shortened ("Mum" instead of a full name)
        typeContact(picked[0], picked[1]);
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req != REQ_CALL_PERMISSION) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            HubConfig.setDirectCall(this, false);
            directCallBox.setChecked(false);
            Toast.makeText(this, R.string.keypad_fallback,
                Toast.LENGTH_LONG).show();
        }
    }

    private void renameNavButtons() {
        String[] current = HubConfig.navLabels(this);
        String[] titles = {getString(R.string.label_back_button), getString(R.string.label_home_button), getString(R.string.label_apps_button)};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText[] fields = new EditText[3];
        for (int i = 0; i < 3; i++) {
            TextView t = text(titles[i], 14, true);
            box.addView(t);
            fields[i] = new EditText(this);
            fields[i].setText(current[i]);
            fields[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            fields[i].setInputType(InputType.TYPE_CLASS_TEXT);
            box.addView(fields[i]);
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.rename_buttons_title)
            .setMessage(R.string.rename_buttons_msg)
            .setView(padded(this, box))
            .setPositiveButton(R.string.save, (d, w) -> {
                String[] out = new String[3];
                for (int i = 0; i < 3; i++) {
                    String v = fields[i].getText().toString().trim();
                    // an untouched default is not a custom name: keep following the app language
                    out[i] = v.equals(getString(HubConfig.NAV_DEFAULTS[i]).trim()) ? "" : v;
                }
                HubConfig.setNavLabels(this, out);
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton(R.string.reset, (d, w) -> HubConfig.setNavLabels(this, new String[]{"", "", ""}))
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /** "Language" in the current language, plus the English word so it is findable by anyone. */
    private String languageHeading() {
        String h = getString(R.string.section_language);
        return h.equals("Language") ? h : h + "  ·  Language";
    }

    private String currentLanguageName() {
        String tag = HubConfig.language(this);
        for (int i = 1; i < HubConfig.LANGUAGE_TAGS.length; i++) {
            if (HubConfig.LANGUAGE_TAGS[i].equals(tag)) return HubConfig.LANGUAGE_NAMES[i];
        }
        return getString(R.string.lang_system);
    }

    private void pickLanguage() {
        String[] names = HubConfig.LANGUAGE_NAMES.clone();
        names[0] = getString(R.string.lang_system);
        new AlertDialog.Builder(this)
            .setTitle(languageHeading())
            .setItems(names, (d, which) -> {
                String tag = HubConfig.LANGUAGE_TAGS[which];
                if (tag.equals(HubConfig.language(this))) return;
                Context before = HubConfig.localized(getBaseContext());
                HubConfig.setLanguage(this, tag);
                Context after = HubConfig.localized(getBaseContext());
                tiles = HubConfig.relabelDefaults(before, after, tiles);
                saveTiles();
                recreate(); // rebuild this screen in the new language
            })
            .show();
    }

    private void setPin() {
        EditText a = pinField(this);
        a.setHint(R.string.new_pin_hint);
        new AlertDialog.Builder(this)
            .setTitle(R.string.set_pin)
            .setView(padded(this, a))
            .setPositiveButton(R.string.save, (d, w) -> {
                String pin = a.getText().toString();
                if (pin.length() < 4) {
                    Toast.makeText(this, R.string.pin_too_short, Toast.LENGTH_LONG).show();
                    return;
                }
                HubConfig.setPin(this, pin);
                renderPin();
                Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void finishEditing() {
        if (setupMode) HubConfig.setSetupDone(this);
        HubConfig.touch(this);
        finish();
    }

    private void saveTiles() {
        if (!HubConfig.save(this, tiles)) {
            Toast.makeText(this, R.string.could_not_save, Toast.LENGTH_LONG).show();
        }
        HubConfig.touch(this);
    }

    private void confirm(String title, String msg, Runnable yes) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(R.string.yes, (d, w) -> yes.run())
            .setNegativeButton(R.string.no, null)
            .show();
    }

    // ============================================================
    // SMALL VIEW HELPERS
    // ============================================================

    private TextView section(String s) {
        TextView t = text(s, 20, true);
        t.setTextColor(0xFF0D47A1);
        t.setPadding(0, dp(26), 0, dp(4));
        return t;
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(0xFF3A4654);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(0xFF0B0F16);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(18), 0, dp(18), 0);
        b.setBackground(rounded(Color.WHITE, 0xFFCBD3DD));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private Button iconButton(String glyph, boolean enabled, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(glyph);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setTextColor(0xFF0B0F16);
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.3f);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(rounded(Color.WHITE, 0xFFCBD3DD));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private CheckBox checkbox(String label) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        c.setTextColor(0xFF0B0F16);
        c.setPadding(dp(8), dp(12), 0, dp(12));
        return c;
    }

    private CheckBox toggle(String label, String key) {
        CheckBox c = checkbox(label);
        c.setChecked(HubConfig.isShown(this, key));
        c.setOnCheckedChangeListener((v, on) -> {
            HubConfig.setShown(this, key, on);
            HubConfig.touch(this);
        });
        return c;
    }

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(14));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private static EditText pinField(Activity a) {
        EditText e = new EditText(a);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        e.setGravity(Gravity.CENTER);
        return e;
    }

    private static View padded(Activity a, View inner) {
        LinearLayout box = new LinearLayout(a);
        int p = Math.round(20 * a.getResources().getDisplayMetrics().density);
        box.setPadding(p, p / 2, p, 0);
        box.addView(inner, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
