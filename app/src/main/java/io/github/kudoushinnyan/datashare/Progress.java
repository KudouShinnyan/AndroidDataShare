package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class Progress {

    private static final AtomicBoolean BUSY = new AtomicBoolean();

    static boolean busy() {
        return BUSY.get();
    }

    interface Handle {
        void step(String line);

        void finish(String message);

        String log();
    }

    private Progress() {
    }

    private static LinearLayout.LayoutParams bar() {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    static Handle show(Activity a, String title, boolean controllable, Runnable onClose) {
        Util.keepAwake(a, true);
        BUSY.set(true);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Util.dp(a, 16);
        box.setPadding(p, p, p, p);

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
                LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(a, 220)));

        Button pause = new Button(a);
        pause.setText(R.string.progress_pause);
        Button cancel = new Button(a);
        cancel.setText(R.string.progress_cancel);
        Button close = new Button(a);
        close.setText(R.string.progress_close);
        close.setVisibility(View.GONE);
        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        if (controllable) {
            buttons.addView(pause, bar());
            buttons.addView(cancel, bar());
        }
        buttons.addView(close, bar());
        box.addView(buttons, Util.fullWidth());

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(box)
                .setCancelable(false)
                .create();
        dialog.show();

        final StringBuffer log = new StringBuffer();
        final AtomicBoolean paused = new AtomicBoolean(false);
        final AtomicInteger n = new AtomicInteger();
        final Handle[] ref = new Handle[1];

        final Consumer<String> send = action -> new Thread(() -> {
            Backend.Result r = Backends.ctrl(action);
            if ((!r.ok || !r.data.contains("ADS_CTRL_OK")) && ref[0] != null) {
                ref[0].step(a.getString(R.string.progress_ctrl_failed));
            }
        }).start();

        final Runnable askCancel = () -> new AlertDialog.Builder(a)
                .setTitle(R.string.progress_cancel)
                .setMessage(R.string.progress_cancel_body)
                .setPositiveButton(R.string.confirm_accept, (d, w) -> send.accept("cancel"))
                .setNegativeButton(R.string.action_cancel, null)
                .show();

        close.setOnClickListener(v -> {
            Util.keepAwake(a, false);
            BUSY.set(false);
            dialog.dismiss();
            if (onClose != null) {
                onClose.run();
            }
        });

        pause.setOnClickListener(v -> {
            boolean nowPaused = !paused.get();
            paused.set(nowPaused);
            pause.setText(nowPaused ? R.string.progress_resume : R.string.progress_pause);
            send.accept(nowPaused ? "pause" : "continue");
        });

        cancel.setOnClickListener(v -> askCancel.run());

        Handle h = new Handle() {
            @Override
            public void step(String line) {
                log.append(line).append('\n');
                Util.ui(a, () -> {
                    count.setText(a.getString(R.string.progress_count,
                            String.valueOf(n.incrementAndGet())));
                    lines.add(line);
                    adapter.notifyDataSetChanged();
                    list.setSelection(lines.size() - 1);
                });
            }

            @Override
            public void finish(String message) {
                String saved = a.getString(R.string.progress_log_saved);
                log.append(message).append('\n').append(saved).append('\n');
                Util.ui(a, () -> {
                    Util.keepAwake(a, false);
                    count.setText(message);
                    hint.setText(saved);
                    lines.add(message);
                    lines.add(saved);
                    adapter.notifyDataSetChanged();
                    list.setSelection(lines.size() - 1);
                    pause.setVisibility(View.GONE);
                    cancel.setVisibility(View.GONE);
                    close.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public String log() {
                return log.toString();
            }
        };
        ref[0] = h;
        return h;
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
