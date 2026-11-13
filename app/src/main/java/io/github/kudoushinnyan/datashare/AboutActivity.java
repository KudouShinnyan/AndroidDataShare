package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Util.dp(this, 12);
        box.setPadding(p, p, p, p);
        sv.addView(box);
        setContentView(sv);
        Util.fitSystemBars(sv, box, p);
        setTitle(R.string.about_title);

        TextView sum = new TextView(this);
        sum.setText(R.string.about_summary);
        box.addView(sum);
        TextView ver = new TextView(this);
        ver.setText(getString(R.string.about_version, "1.0 / 100"));
        box.addView(ver);
        box.addView(button(R.string.about_support, v -> {
            TextView d = new TextView(this);
            d.setText(R.string.about_support_desc);
            new AlertDialog.Builder(this).setTitle(R.string.about_support).setView(d)
                    .setPositiveButton(R.string.warn_whole_accept, null)
                    .setNegativeButton(R.string.url_support, (x, w) -> open(getString(R.string.url_support)))
                    .show();
        }));
        box.addView(button(R.string.about_releases, v -> open(getString(R.string.url_releases))));
        box.addView(button(R.string.about_license, v -> {
            TextView d = new TextView(this);
            d.setText(ModuleGen.license(this));
            new AlertDialog.Builder(this).setTitle(R.string.about_license).setView(d)
                    .setPositiveButton(R.string.warn_whole_accept, null).show();
        }));
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Util.toast(this, getString(R.string.msg_browser_unavailable));
        }
    }

    private Button button(int text, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }
}
