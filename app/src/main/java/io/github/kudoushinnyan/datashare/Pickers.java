package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Pickers {

    static final Pattern USER_INFO = Pattern.compile("UserInfo\\{(\\d+):([^:}]+)");

    interface Pick<T> {
        void on(T value);
    }

    interface Pick2<A, B> {
        void on(A first, B second);
    }

    static final class Item {
        final String display;
        final String value;

        Item(String display, String value) {
            this.display = display;
            this.value = value;
        }
    }

    private Pickers() {
    }

    static void manual(Activity a, String title, String hint, Pick<String> cb) {
        final EditText e = new EditText(a);
        e.setHint(hint);
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(e)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    String v = e.getText().toString().trim();
                    if (!v.isEmpty()) {
                        cb.on(v);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    static void pickUser(Activity a, Pick<String> cb) {
        new Thread(() -> {
            Backend.Result r = Deploy.listUsers();
            List<Item> items = new ArrayList<>();
            Matcher m = USER_INFO.matcher(r.data);
            Set<String> seen = new LinkedHashSet<>();
            while (m.find()) {
                String id = m.group(1);
                if (!seen.add(id)) {
                    continue;
                }
                items.add(new Item(id + " · " + m.group(2).trim(), id));
            }
            if (items.isEmpty()) {
                items.add(new Item("0 · Owner", "0"));
            }
            Collections.sort(items, (x, y) -> x.display.compareTo(y.display));
            Util.ui(a, () -> showSingle(a, a.getString(R.string.msg_pick_user), items,
                    a.getString(R.string.hint_user_id), cb));
        }).start();
    }

    static void pickApp(Activity a, String user, Pick<String> cb) {
        pickApp(a, user, Collections.<String>emptyList(), cb);
    }

    static void pickApp(Activity a, String user, List<String> pinned, Pick<String> cb) {
        new Thread(() -> {
            Backend.Result r = Deploy.listPackages(user);
            List<Item> items = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String line : r.data.split("\n")) {
                String p = line.trim();
                if (p.isEmpty() || !Model.PKG.matcher(p).matches() || !seen.add(p)) {
                    continue;
                }
                String label = Desync.label(a, p);
                items.add(new Item(label.equals(p) ? p : label + " · " + p, p));
            }
            Collections.sort(items, (x, y) -> x.display.compareTo(y.display));
            final List<Item> ordered = reorder(items, pinned);
            Util.ui(a, () -> showSingle(a, a.getString(R.string.msg_pick_app), ordered,
                    a.getString(R.string.hint_primary_pkg), cb));
        }).start();
    }

    private static List<Item> reorder(List<Item> items, List<String> pinned) {
        if (pinned == null || pinned.isEmpty()) {
            return items;
        }
        List<Item> out = new ArrayList<>();
        Set<String> taken = new LinkedHashSet<>();
        for (String p : pinned) {
            if (p == null || p.isEmpty() || !taken.add(p)) {
                continue;
            }
            for (Item it : items) {
                if (it.value.equals(p)) {
                    out.add(it);
                    break;
                }
            }
        }
        for (Item it : items) {
            if (!taken.contains(it.value)) {
                out.add(it);
            }
        }
        return out;
    }

    static void pickDirs(Activity a, String user, String pkg, List<String> selected,
                         boolean includeFiles, Pick<List<String>> cb) {
        new Thread(() -> {
            Backend.Result r = Deploy.scanItems(user, pkg);
            List<String> all = scanLines(r.data, includeFiles, true);
            Util.ui(a, () -> showMulti(a, a.getString(R.string.label_share_dirs), all,
                    selected, a.getString(R.string.hint_rel_path), cb, null, null,
                    includeFiles ? R.string.scope_pick_hint : 0));
        }).start();
    }

    static void pickFiles(Activity a, String user, String pkg, List<String> selected,
                          String currentMode, Pick2<List<String>, String> cb) {
        new Thread(() -> {
            Backend.Result r = Deploy.scanItems(user, pkg);
            List<String> all = scanLines(r.data, true, false);
            Util.ui(a, () -> showMulti(a, a.getString(R.string.label_extra_links), all,
                    selected, a.getString(R.string.hint_rel_path), null, cb, currentMode, 0));
        }).start();
    }

    private static List<String> scanLines(String data, boolean files, boolean dirs) {
        List<String> out = new ArrayList<>();
        for (String line : data.split("\n")) {
            String t = line.trim();
            if (!(t.startsWith("F|") && files) && !(t.startsWith("D|") && dirs)) {
                continue;
            }
            String v = t.substring(2);
            if (!v.isEmpty() && !v.startsWith("/") && !v.contains("..")
                    && Model.REL.matcher(v).matches() && !out.contains(v)) {
                out.add(v);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static void showSingle(Activity a, String title, List<Item> items,
                                   String manualHint, Pick<String> cb) {
        showSingle(a, title, items, manualHint, true, cb);
    }

    static void showItems(Activity a, String title, List<Item> items, Pick<String> cb) {
        showSingle(a, title, items, "", false, cb);
    }

    private static void showSingle(Activity a, String title, List<Item> items,
                                   String manualHint, boolean allowManual, Pick<String> cb) {
        final String manual = a.getString(R.string.pick_manual_entry);
        LinearLayout box = dialogBox(a);
        final EditText search = new EditText(a);
        search.setHint(R.string.pick_search_hint);
        search.setSingleLine(true);
        box.addView(search, Util.fullWidth());
        final TextView empty = new TextView(a);
        empty.setText(R.string.msg_no_match);
        empty.setVisibility(View.GONE);
        box.addView(empty, Util.fullWidth());

        final List<String> shown = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(a,
                android.R.layout.simple_list_item_1, shown);
        final ListView list = new ListView(a);
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(a, 300)));

        final List<Item> visible = new ArrayList<>();
        final AlertDialog dlg = new AlertDialog.Builder(a).setTitle(title).setView(box)
                .setNegativeButton(R.string.action_cancel, null).create();
        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos < 0 || pos >= visible.size()) {
                return;
            }
            Item it = visible.get(pos);
            dlg.dismiss();
            if (it.value.isEmpty()) {
                manual(a, title, manualHint, cb);
            } else {
                cb.on(it.value);
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSingle(s.toString(), items, manual, allowManual, visible, adapter, empty);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        filterSingle("", items, manual, allowManual, visible, adapter, empty);
        dlg.show();
    }

    private static void filterSingle(String query, List<Item> items, String manual,
                                     boolean allowManual, List<Item> visible,
                                     ArrayAdapter<String> adapter, TextView empty) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (Item it : items) {
            String hay = (it.display + " " + it.value).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || hay.contains(q)) {
                visible.add(it);
            }
        }
        if (allowManual) {
            visible.add(new Item(manual, ""));
        }
        adapter.clear();
        for (Item it : visible) {
            adapter.add(it.display);
        }
        empty.setVisibility(visible.size() == (allowManual ? 1 : 0)
                ? View.VISIBLE : View.GONE);
    }

    private static List<String> checkedValues(List<String> all, Set<String> checked) {
        List<String> out = new ArrayList<>();
        for (String v : all) {
            if (checked.contains(v)) {
                out.add(v);
            }
        }
        return out;
    }

    private static void showMulti(Activity a, String title, List<String> scanned,
                                  List<String> preselected, String manualHint,
                                  Pick<List<String>> cb,
                                  Pick2<List<String>, String> extraCb, String currentMode,
                                  int hintRes) {
        final String manual = a.getString(R.string.pick_manual_entry);
        final List<String> all = new ArrayList<>(scanned);
        final Set<String> checked = new LinkedHashSet<>(preselected);
        LinearLayout box = dialogBox(a);
        final EditText search = new EditText(a);
        search.setHint(R.string.pick_search_hint);
        search.setSingleLine(true);
        box.addView(search, Util.fullWidth());
        final TextView empty = new TextView(a);
        empty.setText(R.string.msg_no_match);
        empty.setVisibility(View.GONE);
        box.addView(empty, Util.fullWidth());

        final List<String> shown = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(a,
                android.R.layout.simple_list_item_multiple_choice, shown);
        final ListView list = new ListView(a);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Util.dp(a, 300)));

        final boolean extra = extraCb != null;
        if (extra || hintRes != 0) {
            TextView hint = new TextView(a);
            hint.setText(extra ? R.string.extra_pick_hint : hintRes);
            hint.setTextSize(12f);
            box.addView(hint, Util.fullWidth());
        }

        final Runnable[] refresh = new Runnable[1];
        AlertDialog.Builder builder = new AlertDialog.Builder(a).setTitle(title).setView(box);
        if (extra) {
            boolean copyFirst = Model.MODE_COPY.equals(currentMode);
            builder.setPositiveButton(
                    copyFirst ? R.string.extra_mode_copy : R.string.extra_mode_link,
                    (d, w) -> extraCb.on(checkedValues(all, checked),
                            copyFirst ? Model.MODE_COPY : Model.MODE_HARDLINK));
            builder.setNeutralButton(
                    copyFirst ? R.string.extra_mode_link : R.string.extra_mode_copy,
                    (d, w) -> extraCb.on(checkedValues(all, checked),
                            copyFirst ? Model.MODE_HARDLINK : Model.MODE_COPY));
        } else {
            builder.setPositiveButton(R.string.action_save,
                    (d, w) -> cb.on(checkedValues(all, checked)));
        }
        final AlertDialog dlg = builder.setNegativeButton(R.string.action_cancel, null).create();
        list.setOnItemClickListener((parent, view, pos, id) -> {
            if (pos < 0 || pos >= shown.size()) {
                return;
            }
            String v = shown.get(pos);
            if (v.equals(manual)) {
                manual(a, title, manualHint, s -> {
                    if (!s.isEmpty() && !all.contains(s)) {
                        all.add(s);
                    }
                    if (!s.isEmpty()) {
                        checked.add(s);
                    }
                    refresh[0].run();
                });
                return;
            }
            if (checked.contains(v)) {
                checked.remove(v);
            } else {
                checked.add(v);
            }
            refresh[0].run();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh[0].run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        refresh[0] = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            adapter.clear();
            for (String v : all) {
                if (q.isEmpty() || v.toLowerCase(Locale.ROOT).contains(q)) {
                    adapter.add(v);
                }
            }
            adapter.add(manual);
            for (int i = 0; i < shown.size(); i++) {
                list.setItemChecked(i, checked.contains(shown.get(i)));
            }
            empty.setVisibility(shown.size() == 1 ? View.VISIBLE : View.GONE);
        };
        refresh[0].run();
        dlg.show();
    }

    private static LinearLayout dialogBox(Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(a, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        return box;
    }

}
