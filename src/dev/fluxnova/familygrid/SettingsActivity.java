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
import android.widget.CompoundButton;
import android.widget.Switch;
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
    private CompoundButton directCallBox;
    private LinearLayout textSizeRow;
    private Button pinButton;
    private Button removePinButton;
    private Button navBarButton;

    private static final int REQ_BT_PERMISSION = 3;
    private static final String EXTRA_UNLOCKED = "unlocked";

    private LinearLayout checklistBox;
    private CompoundButton quietBox;
    private boolean uiBuilt;

    /** Opens settings; the PIN (if any) is asked by SettingsActivity itself. */
    static void openWithPin(Activity from) {
        from.startActivity(new Intent(from, SettingsActivity.class));
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(HubConfig.localized(base));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        EdgeToEdge.onBack(this, this::onBackPressed);
        boolean unlocked = (b != null && b.getBoolean(EXTRA_UNLOCKED))
            || getIntent().getBooleanExtra(EXTRA_UNLOCKED, false);
        // Every way in (home screen, app-drawer icon) goes through the same PIN check.
        if (HubConfig.hasPin(this) && !unlocked) askPinThenBuild();
        else build();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(EXTRA_UNLOCKED, uiBuilt);
    }

    private void askPinThenBuild() {
        EditText pin = pinField(this);
        new AlertDialog.Builder(this)
            .setTitle(R.string.enter_pin)
            .setView(padded(this, pin))
            .setCancelable(false)
            .setPositiveButton(R.string.open, (d, w) -> {
                if (HubConfig.checkPin(this, pin.getText().toString())) {
                    getIntent().putExtra(EXTRA_UNLOCKED, true);
                    build();
                } else {
                    Toast.makeText(this, R.string.wrong_pin, Toast.LENGTH_SHORT).show();
                    finish();
                }
            })
            .setNegativeButton(R.string.cancel, (d, w) -> finish())
            .show();
    }

    private void build() {
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
        View ui = buildUi();
        EdgeToEdge.padForSystemBars(ui);
        setContentView(ui);
        uiBuilt = true;
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (uiBuilt) refreshStatus(); // user may be back from Android settings
    }

    /** Updates everything that depends on system state (checklist, big-buttons button). */
    private void refreshStatus() {
        renderChecklist();
        if (navBarButton != null) {
            navBarButton.setText(NavBarService.isEnabled(this)
                ? getString(R.string.navbar_is_on) : getString(R.string.navbar_turn_on));
        }
        if (directCallBox != null) {
            directCallBox.setChecked(HubConfig.directCall(this) && HubActions.canCallDirectly(this));
        }
        if (quietBox != null) quietBox.setChecked(HubConfig.quietMode(this));
    }

    @Override public void onBackPressed() {
        if (uiBuilt) finishEditing(); else finish();
    }

    // ============================================================
    // LAYOUT
    // ============================================================

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F6F9);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(22), dp(18), dp(24));
        scroll.addView(col);
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView title = text(getString(setupMode ? R.string.title_welcome : R.string.title_edit), 28, true);
        title.setTextColor(0xFF0B0F16);
        col.addView(title);
        if (setupMode) col.addView(text(getString(R.string.setup_intro), 17, false));

        // ---- 1. language: first, so everything after it is readable ----
        LinearLayout lang = card(col, languageHeading());
        lang.addView(button("🌐  " + currentLanguageName(), v -> pickLanguage()));

        // ---- 2. setup checklist: what is required / optional and whether it is done ----
        LinearLayout check = card(col, getString(R.string.section_checklist));
        checklistBox = new LinearLayout(this);
        checklistBox.setOrientation(LinearLayout.VERTICAL);
        check.addView(checklistBox);

        // ---- 3. tiles ----
        LinearLayout tilesCard = card(col, getString(R.string.section_tiles));
        tilesCard.addView(text(getString(R.string.tiles_help), 15, false));
        tileList = new LinearLayout(this);
        tileList.setOrientation(LinearLayout.VERTICAL);
        tilesCard.addView(tileList);
        renderTiles();
        tilesCard.addView(button(getString(R.string.add_app), v -> addApp()));
        tilesCard.addView(button(getString(R.string.add_person), v -> addContact()));

        // ---- 4. look: grid + text size ----
        LinearLayout look = card(col, getString(R.string.section_look));
        look.addView(label(getString(R.string.grid_label)));
        LinearLayout gridRow = row();
        look.addView(gridRow);
        renderColumns(gridRow);
        look.addView(label(getString(R.string.section_text_size)));
        textSizeRow = row();
        look.addView(textSizeRow);
        renderTextSize();

        // ---- 5. what the home screen shows ----
        LinearLayout show = card(col, getString(R.string.section_show));
        show.addView(toggle(getString(R.string.show_wifi), HubConfig.SHOW_WIFI));
        show.addView(toggle(getString(R.string.show_battery), HubConfig.SHOW_BATTERY));
        show.addView(toggle(getString(R.string.show_bluetooth), HubConfig.SHOW_BLUETOOTH));
        show.addView(toggle(getString(R.string.show_all_apps), HubConfig.SHOW_ALL_APPS));

        // ---- quiet mode: hide junk notifications ----
        LinearLayout quiet = card(col, getString(R.string.section_quiet));
        quiet.addView(text(getString(R.string.quiet_help), 15, false));
        quietBox = checkbox(getString(R.string.quiet_switch));
        quietBox.setChecked(HubConfig.quietMode(this));
        quietBox.setOnCheckedChangeListener((v, on) -> {
            if (!v.isPressed()) return;
            HubConfig.setQuietMode(this, on);
            HubConfig.touch(this);
            if (on && !QuietService.hasAccess(this)) openQuietAccess();
            renderChecklist();
        });
        quiet.addView(quietBox);
        quiet.addView(button(getString(R.string.quiet_allow_more), v -> pickQuietExtras()));

        // ---- 6. big navigation buttons ----
        LinearLayout nav = card(col, getString(R.string.section_navbar));
        nav.addView(text(getString(R.string.navbar_help), 15, false));
        navBarButton = button("", v -> openBigButtonsSettings());
        nav.addView(navBarButton);
        nav.addView(button(getString(R.string.navbar_rename), v -> renameNavButtons()));

        // ---- calling: lives in the Tiles card, right under "Add a person (speed dial)" ----
        directCallBox = checkbox(getString(R.string.direct_call));
        directCallBox.setChecked(HubConfig.directCall(this) && HubActions.canCallDirectly(this));
        directCallBox.setOnCheckedChangeListener((v, on) -> {
            if (!v.isPressed()) return; // ignore programmatic refreshes
            HubConfig.setDirectCall(this, on);
            if (on && !HubActions.canCallDirectly(this)) {
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL_PERMISSION);
            }
            renderChecklist();
        });
        tilesCard.addView(directCallBox);

        // ---- 8. lock ----
        LinearLayout lock = card(col, getString(R.string.section_lock));
        lock.addView(text(getString(R.string.lock_help), 15, false));
        pinButton = button("", v -> setPin());
        removePinButton = button(getString(R.string.remove_pin), v -> {
            HubConfig.setPin(this, null);
            renderPin();
            Toast.makeText(this, R.string.pin_removed, Toast.LENGTH_SHORT).show();
        });
        lock.addView(pinButton);
        lock.addView(removePinButton);
        renderPin();

        // ---- 9. more ----
        LinearLayout more = card(col, getString(R.string.section_more));
        more.addView(button(getString(R.string.phone_settings), v -> HubActions.openSettings(this)));
        more.addView(button(getString(R.string.reset_tiles), v -> confirm(
            getString(R.string.reset_confirm_title), getString(R.string.reset_confirm_msg), () -> {
                HubConfig.resetToDefaults(this);
                tiles = HubConfig.load(this);
                renderTiles();
            })));

        // Done stays fixed at the bottom so it is always one tap away.
        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(dp(18), dp(10), dp(18), dp(12));
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setElevation(dp(8));
        Button done = button(getString(setupMode ? R.string.finish_setup : R.string.done), v -> finishEditing());
        done.setBackground(rounded(0xFF0D47A1, 0));
        done.setTextColor(Color.WHITE);
        done.setGravity(Gravity.CENTER);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        bottom.addView(done, dl);
        root.addView(bottom);
        return root;
    }

    /** A white rounded card with a heading; returns the column to add content to. */
    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(12), dp(16), dp(16));
        c.setBackground(rounded(Color.WHITE, 0xFFE1E6EC));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        parent.addView(c, lp);
        TextView h = text(heading, 20, true);
        h.setTextColor(0xFF0D47A1);
        c.addView(h);
        return c;
    }

    private TextView label(String s) {
        TextView t = text(s, 16, true);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    // ============================================================
    // CHECKLIST
    // ============================================================

    private void renderChecklist() {
        if (checklistBox == null) return;
        checklistBox.removeAllViews();
        checkRow(getString(R.string.check_home), true, HubActions.isDefaultHome(this),
            () -> guide(R.string.guide_home, () -> HubActions.openHomeSettings(this)));
        checkRow(getString(R.string.check_navbar), false, NavBarService.isEnabled(this),
            () -> openBigButtonsSettings());
        checkRow(getString(R.string.check_call), false, HubActions.canCallDirectly(this),
            () -> guide(R.string.guide_call, () -> {
                HubConfig.setDirectCall(this, true);
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL_PERMISSION);
            }));
        checkRow(getString(R.string.check_adb), LEVEL_RECOMMENDED, !HubActions.isUsbDebuggingOn(this),
            () -> guide(R.string.guide_adb, () -> HubActions.openDeveloperOptions(this)));
        if (HubConfig.quietMode(this)) {
            checkRow(getString(R.string.check_quiet), false, QuietService.hasAccess(this), this::openQuietAccess);
        }
        if (HubConfig.isShown(this, HubConfig.SHOW_BLUETOOTH) && android.os.Build.VERSION.SDK_INT >= 31) {
            checkRow(getString(R.string.check_bt), false, !HubActions.needsBluetoothPermission(this),
                () -> guide(R.string.guide_bt, () -> requestPermissions(
                    new String[]{"android.permission.BLUETOOTH_CONNECT"}, REQ_BT_PERMISSION)));
        }
    }

    private static final int LEVEL_OPTIONAL = 0, LEVEL_REQUIRED = 1, LEVEL_RECOMMENDED = 2;

    private void checkRow(String title, boolean required, boolean ok, Runnable fix) {
        checkRow(title, required ? LEVEL_REQUIRED : LEVEL_OPTIONAL, ok, fix);
    }

    private void checkRow(String title, int level, boolean ok, Runnable fix) {
        boolean required = level == LEVEL_REQUIRED;
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 17, true);
        t.setTextColor(0xFF0B0F16);
        info.addView(t);

        LinearLayout tags = new LinearLayout(this);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        if (level == LEVEL_RECOMMENDED) {
            tags.addView(pill(getString(R.string.tag_recommended), 0xFFFFF1D6, 0xFF8A5A00));
        } else {
            tags.addView(pill(getString(required ? R.string.tag_required : R.string.tag_optional),
                required ? 0xFFFFE0E0 : 0xFFE8EDF3, required ? 0xFFB71C1C : 0xFF4A5868));
        }
        TextView st = pill(getString(ok ? R.string.status_done : R.string.status_todo),
            ok ? 0xFFDDF5E3 : 0xFFFFF1D6, ok ? 0xFF1B6E34 : 0xFF8A5A00);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.setMarginStart(dp(6));
        tags.addView(st, sl);
        info.addView(tags);
        r.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!ok) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(R.string.action_fix);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            b.setTextColor(Color.WHITE);
            b.setBackground(rounded(required ? 0xFFC62828 : 0xFF0D47A1, 0));
            b.setPadding(dp(16), 0, dp(16), 0);
            b.setOnClickListener(v -> fix.run());
            r.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        }
        checklistBox.addView(r);
    }

    private TextView pill(String s, int bg, int fg) {
        TextView p = new TextView(this);
        p.setText(s);
        p.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextColor(fg);
        p.setSingleLine(true);
        p.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(20));
        p.setBackground(d);
        return p;
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
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                name.setAutoSizeTextTypeUniformWithConfiguration(11, 18, 1, TypedValue.COMPLEX_UNIT_SP);
            }
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
        if (HubConfig.TYPE_CONTACT.equals(t.type)) return "  ·  📞 " + t.phone;
        if (HubConfig.TYPE_APP.equals(t.type) && !HubActions.isInstalled(this, t.pkg)) {
            return "  ·  " + getString(R.string.tile_not_installed);
        }
        return "";
    }

    private void renderTextSize() {
        String[] names = new String[HubConfig.TEXT_SCALES.length];
        for (int i = 0; i < names.length; i++) names[i] = getString(HubConfig.TEXT_SCALE_NAMES[i]);
        segmented(textSizeRow, names, HubConfig.textScaleIndex(this), HubConfig.TEXT_SCALES, idx -> {
            HubConfig.setTextScaleIndex(this, idx);
            HubConfig.touch(this);
            renderTextSize();
        });
    }

    private void renderColumns(LinearLayout rowView) {
        String[] names = new String[HubConfig.COLUMN_CHOICES.length];
        int sel = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(HubConfig.COLUMN_NAMES[i]);
            if (HubConfig.COLUMN_CHOICES[i] == HubConfig.columns(this)) sel = i;
        }
        segmented(rowView, names, sel, null, idx -> {
            HubConfig.setColumns(this, HubConfig.COLUMN_CHOICES[idx]);
            HubConfig.touch(this);
            renderColumns(rowView);
        });
    }

    /** Row of big equal buttons where one is selected. scales (optional) previews text size. */
    private void segmented(LinearLayout rowView, String[] names, int selected, float[] scales,
                           java.util.function.IntConsumer onPick) {
        rowView.removeAllViews();
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            Button btn = new Button(this);
            btn.setAllCaps(false);
            btn.setText(names[i]);
            // preview the text size, but keep it inside the button
            int size = Math.round(15 * Math.min(scales != null ? scales[i] : 1f, 1.25f));
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
            btn.setMaxLines(1);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                btn.setAutoSizeTextTypeUniformWithConfiguration(10, size, 1, TypedValue.COMPLEX_UNIT_SP);
            }
            boolean sel = i == selected;
            btn.setTextColor(sel ? Color.WHITE : 0xFF0B0F16);
            btn.setBackground(rounded(sel ? 0xFF0D47A1 : Color.WHITE, sel ? 0 : 0xFFCBD3DD));
            btn.setPadding(dp(4), 0, dp(4), 0);
            btn.setOnClickListener(v -> onPick.accept(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
            lp.setMargins(dp(3), dp(6), dp(3), 0);
            rowView.addView(btn, lp);
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
        refreshStatus();
        boolean denied = results.length > 0 && results[0] != PackageManager.PERMISSION_GRANTED;
        if (denied && perms.length > 0 && !shouldShowRequestPermissionRationale(perms[0])) {
            // "Don't ask again" was chosen earlier, so Android shows nothing: go to the app's page
            HubActions.openAppDetails(this);
        }
        if (req != REQ_CALL_PERMISSION) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            HubConfig.setDirectCall(this, false);
            directCallBox.setChecked(false);
            Toast.makeText(this, R.string.keypad_fallback,
                Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Google Play requires a clear in-app explanation and an explicit "agree" before
     * sending people to switch on an accessibility service that is not an accessibility tool.
     * Switching it off needs no disclosure.
     */
    private void openBigButtonsSettings() {
        if (NavBarService.isEnabled(this)) {
            HubActions.openAccessibilitySettings(this);
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.navbar_disclosure_title)
            .setMessage(R.string.navbar_disclosure_msg)
            .setPositiveButton(R.string.agree_continue, (d, w) -> HubActions.openAccessibilitySettings(this))
            .setNegativeButton(R.string.no_thanks, null)
            .show();
    }

    /** Short "what to do next" note before sending someone to an Android screen. */
    private void guide(int messageRes, Runnable go) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.guide_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.continue_btn, (d, w) -> go.run())
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /** Same Play rule as the big buttons: explain, get an explicit "agree", then open Android settings. */
    private void openQuietAccess() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.quiet_disclosure_title)
            .setMessage(R.string.quiet_disclosure_msg)
            .setPositiveButton(R.string.agree_continue, (d, w) -> HubActions.openNotificationAccess(this))
            .setNegativeButton(R.string.no_thanks, null)
            .show();
    }

    private void pickQuietExtras() {
        List<HubActions.AppEntry> apps = HubActions.launchableApps(this);
        java.util.Set<String> extras = HubConfig.quietExtras(this);
        String[] labels = new String[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            checked[i] = extras.contains(apps.get(i).pkg);
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.quiet_allow_title)
            .setMultiChoiceItems(labels, checked, (d, which, on) -> checked[which] = on)
            .setPositiveButton(R.string.save, (d, w) -> {
                java.util.Set<String> out = new java.util.HashSet<>();
                for (int i = 0; i < apps.size(); i++) if (checked[i]) out.add(apps.get(i).pkg);
                HubConfig.setQuietExtras(this, out);
                HubConfig.touch(this);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
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

    private Switch checkbox(String label) {
        Switch c = new Switch(this);
        c.setText(label);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        c.setTextColor(0xFF0B0F16);
        c.setPadding(dp(8), dp(12), 0, dp(12));
        return c;
    }

    private Switch toggle(String label, String key) {
        Switch c = checkbox(label);
        c.setChecked(HubConfig.isShown(this, key));
        c.setOnCheckedChangeListener((v, on) -> {
            HubConfig.setShown(this, key, on);
            HubConfig.touch(this);
            renderChecklist();
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
