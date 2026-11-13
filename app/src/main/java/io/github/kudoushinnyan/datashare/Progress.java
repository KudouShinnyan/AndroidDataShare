package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class Progress {

    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static WeakReference<View> attachedBox;

    static boolean busy() {
        return BUSY.get();
    }

    interface Handle {
        void step(String line);

        void done(boolean ok, String message);

        String log();
    }

    private Progress() {
    }

    static Handle show(Activity a, String title) {
        return open(a, null, title);
    }

    static Handle attach(Activity a, LinearLayout host, String title) {
        return open(a, host, title);
    }

    private static Handle open(Activity a, LinearLayout host, String title) {
        Util.keepAwake(a, true);
        BUSY.set(true);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Util.dp(a, host == null ? 16 : 12);
        box.setPadding(p, p, p, p);

        if (host != null) {
            TextView head = new TextView(a);
            head.setText(title);
            head.setTextSize(15f);
            box.addView(head, Util.fullWidth());
        }

        TextView count = new TextView(a);
        count.setText(R.string.progress_wait);
        box.addView(count, Util.fullWidth());

        TextView hint = new TextView(a);
        hint.setText(R.string.progress_hint);
        hint.setTextSize(12f);
        box.addView(hint, Util.fullWidth());

        ArrayList<String> lines = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(a,
                android.R.layout.simple_list_item_1, lines);
        ListView list = new ListView(a);
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Util.dp(a, host == null ? 220 : 200)));

        AlertDialog dialog = null;
        if (host == null) {
            dialog = new AlertDialog.Builder(a)
                    .setTitle(title)
                    .setView(box)
                    .setCancelable(false)
                    .create();
            dialog.show();
        } else {
            View old = attachedBox == null ? null : attachedBox.get();
            if (old != null && old.getParent() == host) {
                host.removeView(old);
            }
            host.addView(box, Util.fullWidth());
            attachedBox = new WeakReference<>(box);
        }
        final AlertDialog d = dialog;
        final StringBuffer log = host == null ? null : new StringBuffer();

        AtomicInteger n = new AtomicInteger();
        return new Handle() {
            @Override
            public void step(String line) {
                if (log != null) {
                    log.append(line).append('\n');
                }
                Util.ui(a, () -> {
                    count.setText(a.getString(R.string.progress_count,
                            String.valueOf(n.incrementAndGet())));
                    lines.add(line);
                    adapter.notifyDataSetChanged();
                    list.setSelection(lines.size() - 1);
                });
            }

            @Override
            public String log() {
                return log == null ? "" : log.toString();
            }

            @Override
            public void done(boolean ok, String message) {
                Util.ui(a, () -> {
                    Util.keepAwake(a, false);
                    BUSY.set(false);
                    if (d == null) {
                        count.setText(message);
                        hint.setText(a.getString(ok
                                ? R.string.msg_applied : R.string.result_failed));
                    } else {
                        d.dismiss();
                    }
                });
            }
        };
    }

    static boolean saveLog(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String body = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        try {
            return Backends.current().exec(
                    "engine_publish \"$ADS_RUNLOG\" " + Engine.heredoc("ADS_RUNLOG", body) + "\n").ok;
        } catch (Exception e) {
            return false;
        }
    }
}
