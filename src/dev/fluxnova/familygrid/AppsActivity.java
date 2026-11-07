package dev.fluxnova.familygrid;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** UI TIER: a big, plain list of every app, with a clearly labelled Back button. */
public class AppsActivity extends Activity {
    private List<HubActions.AppEntry> apps;
    private float scale;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(HubConfig.localized(base));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        scale = HubConfig.textScale(this);
        apps = HubActions.launchableApps(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0F16);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(16), dp(14), dp(10));

        TextView back = new TextView(this);
        back.setText(R.string.back); // plain glyph: some OEM fonts draw "←" wrongly
        back.setTextColor(Color.WHITE);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20 * scale);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setPadding(dp(18), dp(12), dp(18), dp(12));
        GradientDrawable bd = new GradientDrawable();
        bd.setCornerRadius(dp(18));
        bd.setColor(0xFF1C2532);
        back.setBackground(bd);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText(R.string.all_apps_title);
        title.setTextColor(0xFF8FA1B3);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22 * scale);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.END);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setPadding(dp(10), 0, dp(10), dp(10));
        list.setClipToPadding(false);
        list.setAdapter(new Adapter());
        list.setOnItemClickListener((parent, view, pos, id) -> {
            HubActions.AppEntry a = apps.get(pos);
            if (HubActions.launchApp(this, a.pkg)) finish();
            else Toast.makeText(this, getString(R.string.could_not_open, a.label), Toast.LENGTH_SHORT).show();
        });
        root.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convert;
            if (row == null) {
                row = new LinearLayout(AppsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                ImageView icon = new ImageView(AppsActivity.this);
                int s = dp(Math.round(48 * scale));
                row.addView(icon, new LinearLayout.LayoutParams(s, s));
                TextView label = new TextView(AppsActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21 * scale);
                label.setTypeface(Typeface.DEFAULT_BOLD);
                label.setPadding(dp(18), 0, 0, 0);
                label.setSingleLine(true);
                row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            HubActions.AppEntry a = apps.get(pos);
            ImageView icon = (ImageView) row.getChildAt(0);
            try {
                icon.setImageDrawable(getPackageManager().getApplicationIcon(a.pkg));
            } catch (Exception e) {
                icon.setImageDrawable(null);
            }
            ((TextView) row.getChildAt(1)).setText(a.label);
            return row;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
