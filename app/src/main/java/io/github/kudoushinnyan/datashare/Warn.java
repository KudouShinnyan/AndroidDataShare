package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import java.util.function.Consumer;

final class Warn {

    private Warn() {
    }

    static void info(Activity a, String title, String body) {
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(a.getString(R.string.warn_whole_accept), null)
                .show();
    }

    static void confirm(Activity a, String title, String body, Runnable proceed) {
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(a.getString(R.string.confirm_accept), (d, w) -> proceed.run())
                .setNegativeButton(a.getString(R.string.confirm_decline), null)
                .show();
    }

    static void confirmCheck(Activity a, String title, String body, String checkLabel,
                             Consumer<Boolean> proceed) {
        CheckBox check = new CheckBox(a);
        check.setText(checkLabel);
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int p = Util.dp(a, 20);
        wrap.setPadding(p, 0, p, 0);
        wrap.addView(check);
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(body)
                .setView(wrap)
                .setPositiveButton(a.getString(R.string.confirm_accept),
                        (d, w) -> proceed.accept(check.isChecked()))
                .setNegativeButton(a.getString(R.string.action_cancel), null)
                .show();
    }
}
