package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class GroupActivity extends Activity {

    private String gid = "";
    private Model.Group g;
    private boolean isNew;
    private boolean wholeMode = false;
    private boolean dirty = false;
    private boolean updating = false;
    private boolean applying = false;
    private LinearLayout page;
    private Button saveButton;
    private Button cancelButton;
    private Button addTargetButton;

    private EditText nameInput;
    private TextView primaryStatus;
    private Button primaryApp;
    private Button primaryUser;

    private Button modeButton;
    private TextView modeHint;
    private LinearLayout scopeSection;
    private TextView scopeLabel;
    private Button scopeButton;
    private TextView dirsWholeNote;
    private TextView dirsAutoWhole;

    private LinearLayout dirsSection;
    private TextView dirsLabel;
    private LinearLayout extraSection;
    private TextView extraLabel;
    private TextView targetsLabel;
    private boolean hasRootFiles = false;
    private boolean shellRiskShown = false;
    private boolean scanPending = false;
    private String scanKey = "";

    private LinearLayout targetList;
    private CheckBox advanced;
    private LinearLayout sentinelSection;
    private EditText sentinel;

    @Override
    public void onBackPressed() {
        if (!Progress.busy()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gid = getIntent().getStringExtra("gid") == null ? "" : getIntent().getStringExtra("gid");
        isNew = gid.isEmpty();
        Model.Group src = isNew ? null : Model.find(Session.get(), gid);
        if (src == null) {
            g = new Model.Group();
            g.mode = "shizuku".equals(Backends.current().id())
                    ? Model.MODE_COPY : Model.MODE_HARDLINK;
        } else {
            g = copyGroup(src);
        }
        if (isNew && !g.isMount()) {
            g.dirs.add(Model.WHOLE);
        }
        wholeMode = g.hasWhole();

        ScrollView sv = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(this, 16);
        page.setPadding(pad, pad, pad, pad);
        sv.addView(page);
        setContentView(sv);
        Util.fitSystemBars(sv, page, pad);
        setTitle(R.string.group_editor_title);

        nameInput = new EditText(this);
        nameInput.setHint(R.string.hint_group_name);
        nameInput.setText(g.name);
        page.addView(nameInput, Util.fullWidth());

        page.addView(section(R.string.label_primary));
        primaryStatus = new TextView(this);
        primaryStatus.setTextSize(13f);
        page.addView(primaryStatus, Util.fullWidth());

        primaryApp = new Button(this);
        primaryApp.setOnClickListener(v -> Pickers.pickApp(this, g.primary.user, p -> {
            if (!p.equals(g.primary.pkg)) {
                resetForPrimaryChange(g);
            }
            g.primary.pkg = p;
            dirty = true;
            refreshUi();
        }));
        page.addView(primaryApp, Util.fullWidth());

        primaryUser = new Button(this);
        primaryUser.setOnClickListener(v -> Pickers.pickUser(this, u -> {
            if (shellBackend() && !"0".equals(u) && !shellRiskShown) {
                shellRiskShown = true;
                Desync.showShellRisk(this, null);
            }
            if (!u.equals(g.primary.user)) {
                resetForPrimaryChange(g);
            }
            g.primary.user = u;
            dirty = true;
            refreshUi();
        }));
        page.addView(primaryUser, Util.fullWidth());
        primaryApp.setEnabled(isNew);
        primaryUser.setEnabled(isNew);

        page.addView(section(R.string.label_mode));
        modeButton = new Button(this);
        modeButton.setOnClickListener(v -> chooseMode());
        page.addView(modeButton, Util.fullWidth());
        modeHint = new TextView(this);
        modeHint.setTextSize(12f);
        page.addView(modeHint, Util.fullWidth());

        scopeSection = new LinearLayout(this);
        scopeSection.setOrientation(LinearLayout.VERTICAL);
        scopeLabel = section(R.string.label_scope);
        scopeSection.addView(scopeLabel);
        scopeButton = new Button(this);
        scopeButton.setOnClickListener(v -> chooseScope());
        scopeSection.addView(scopeButton, Util.fullWidth());
        page.addView(scopeSection, Util.fullWidth());

        dirsWholeNote = new TextView(this);
        dirsWholeNote.setText(R.string.dirs_whole_note);
        dirsWholeNote.setTextSize(13f);
        page.addView(dirsWholeNote, Util.fullWidth());
        dirsAutoWhole = new TextView(this);
        dirsAutoWhole.setText(R.string.dirs_auto_whole);
        dirsAutoWhole.setTextSize(13f);
        page.addView(dirsAutoWhole, Util.fullWidth());

        dirsSection = new LinearLayout(this);
        dirsSection.setOrientation(LinearLayout.VERTICAL);
        dirsSection.addView(section(R.string.label_share_dirs));
        dirsLabel = new TextView(this);
        dirsSection.addView(dirsLabel, Util.fullWidth());
        dirsSection.addView(button(R.string.action_pick_dirs, v -> Pickers.pickDirs(this,
                g.primary.user, g.primary.pkg,
                new ArrayList<>(g.isMount() ? g.mountDirs : g.dirs),
                !g.isMount(), picked -> {
                    List<String> clean = removeCovered(picked, picked);
                    if (g.isMount()) {
                        g.mountDirs.clear();
                        g.mountDirs.addAll(clean);
                        filterExtraCovered();
                    } else {
                        g.dirs.clear();
                        g.dirs.addAll(clean);
                    }
                    dirty = true;
                    refreshUi();
                })), Util.fullWidth());
        page.addView(dirsSection, Util.fullWidth());

        extraSection = new LinearLayout(this);
        extraSection.setOrientation(LinearLayout.VERTICAL);
        extraSection.addView(section(R.string.extra_mode_hint), Util.fullWidth());
        extraLabel = new TextView(this);
        extraSection.addView(extraLabel, Util.fullWidth());
        extraSection.addView(button(R.string.action_pick_extra, v -> Pickers.pickFiles(this,
                g.primary.user, g.primary.pkg, new ArrayList<>(g.extraLinks), g.extraMode,
                (picked, mode) -> {
                    g.extraLinks.clear();
                    g.extraLinks.addAll(picked);
                    g.extraMode = mode;
                    filterExtraCovered();
                    dirty = true;
                    refreshUi();
                })), Util.fullWidth());
        TextView extraBottomHint = new TextView(this);
        extraBottomHint.setText(R.string.extra_lifecycle);
        extraBottomHint.setTextSize(12f);
        extraSection.addView(extraBottomHint, Util.fullWidth());

        targetsLabel = section(R.string.label_targets);
        page.addView(targetsLabel);
        targetList = new LinearLayout(this);
        targetList.setOrientation(LinearLayout.VERTICAL);
        page.addView(targetList, Util.fullWidth());
        addTargetButton = button(R.string.action_add_target, v -> showTargetDialog(-1));
        page.addView(addTargetButton, Util.fullWidth());

        advanced = new CheckBox(this);
        advanced.setText(R.string.label_advanced);
        advanced.setChecked(!g.sentinel.isEmpty());
        advanced.setOnCheckedChangeListener((b, v) -> {
            dirty = true;
            refreshUi();
        });
        page.addView(advanced, Util.fullWidth());

        sentinelSection = new LinearLayout(this);
        sentinelSection.setOrientation(LinearLayout.VERTICAL);
        TextView help = new TextView(this);
        help.setText(R.string.sentinel_help);
        help.setTextSize(12f);
        sentinelSection.addView(help, Util.fullWidth());
        sentinel = new EditText(this);
        sentinel.setHint(R.string.hint_sentinel_path);
        sentinel.setText(g.sentinel);
        sentinelSection.addView(sentinel, Util.fullWidth());
        sentinelSection.addView(button(R.string.action_pick_sentinel, v -> Pickers.manual(this,
                getString(R.string.label_sentinel), getString(R.string.hint_sentinel_path),
                s -> sentinel.setText(s))), Util.fullWidth());
        page.addView(sentinelSection, Util.fullWidth());

        saveButton = button(R.string.action_save, v -> save());
        page.addView(saveButton, Util.fullWidth());
        cancelButton = button(R.string.action_cancel, v -> finish());
        page.addView(cancelButton, Util.fullWidth());

        refreshUi();
    }

    private static boolean sameTargets(Model.Group a, Model.Group b) {
        if (a.targets.size() != b.targets.size()) {
            return false;
        }
        for (int i = 0; i < a.targets.size(); i++) {
            if (!a.targets.get(i).targetKey().equals(b.targets.get(i).targetKey())) {
                return false;
            }
        }
        return true;
    }

    private static Model.Group copyGroup(Model.Group src) {
        Model.State s = new Model.State();
        s.groups.add(src);
        Model.State t = Model.parseState(Model.toStateJson(s));
        return t.groups.isEmpty() ? new Model.Group() : t.groups.get(0);
    }

    static void resetForPrimaryChange(Model.Group g) {
        g.mountDirs.clear();
        g.extraLinks.clear();
        g.dirs.clear();
        if (!g.isMount()) {
            g.dirs.add(Model.WHOLE);
        }
    }

    private void chooseMode() {
        if (isNew) {
            showModeOptions();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.warn_mode_change_title)
                .setMessage(R.string.warn_mode_change_body)
                .setPositiveButton(R.string.confirm_accept, (d, w) -> {
                    d.dismiss();
                    showModeOptions();
                })
                .setNegativeButton(R.string.warn_cancel, null)
                .show();
    }

    private void showModeOptions() {
        CharSequence[] items = {
                getString(R.string.mode_hardlink_rec),
                getString(R.string.mode_mount),
                getString(R.string.mode_copy)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.label_mode)
                .setItems(items, (d, w) -> {
                    String next = w == 0 ? Model.MODE_HARDLINK
                            : (w == 1 ? Model.MODE_MOUNT : Model.MODE_COPY);
                    if (next.equals(g.mode)) {
                        return;
                    }
                    if (shellBackend() && !Model.MODE_COPY.equals(next) && !shellRiskShown) {
                        d.dismiss();
                        shellRiskShown = true;
                        Desync.showShellRisk(this, () -> applyMode(next));
                        return;
                    }
                    if (Model.MODE_MOUNT.equals(next)) {
                        d.dismiss();
                        Desync.showMountRisk(this, g, () -> applyMode(next));
                        return;
                    }
                    applyMode(next);
                })
                .setNegativeButton(R.string.warn_cancel, null)
                .show();
    }

    private void applyMode(String next) {
        g.mode = next;
        if (!g.isMount() && g.dirs.isEmpty()) {
            g.dirs.add(Model.WHOLE);
        }
        wholeMode = g.hasWhole();
        dirty = true;
        refreshUi();
    }

    private void chooseScope() {
        if (!g.isMount()) {
            CharSequence[] opts = {
                    getString(R.string.scope_all_files),
                    getString(R.string.scope_pick_items)
            };
            new AlertDialog.Builder(this)
                    .setTitle(scopeTitle())
                    .setItems(opts, (d, w) -> {
                        boolean whole = w == 0;
                        if (whole == wholeMode) {
                            return;
                        }
                        g.dirs.clear();
                        if (whole) {
                            g.dirs.add(Model.WHOLE);
                        }
                        dirty = true;
                        refreshUi();
                    })
                    .setNegativeButton(R.string.warn_cancel, null)
                    .show();
            return;
        }
        CharSequence[] items = {
                getString(R.string.scope_subdirs),
                getString(R.string.mode_whole)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.label_scope)
                .setItems(items, (d, w) -> {
                    boolean next = w == 1;
                    if (next == wholeMode) {
                        return;
                    }
                    if (next) {
                        d.dismiss();
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.warn_scope_whole_title)
                                .setMessage(R.string.warn_scope_whole_body)
                                .setPositiveButton(R.string.warn_general_accept,
                                        (x, y) -> {
                                            wholeMode = true;
                                            dirty = true;
                                            refreshUi();
                                        })
                                .setNegativeButton(R.string.warn_cancel, null)
                                .show();
                        return;
                    }
                    wholeMode = false;
                    dirty = true;
                    refreshUi();
                })
                .setNegativeButton(R.string.warn_cancel, null)
                .show();
    }

    private void filterExtraCovered() {
        List<String> kept = removeCovered(g.extraLinks, g.mountDirs);
        g.extraLinks.clear();
        g.extraLinks.addAll(kept);
    }

    private void refreshUi() {
        if (updating) {
            return;
        }
        updating = true;
        try {
            boolean mount = g.isMount();
            if (!mount) {
                g.mountDirs.clear();
                g.extraLinks.clear();
                if (g.dirs.contains(Model.WHOLE)) {
                    g.dirs.clear();
                    g.dirs.add(Model.WHOLE);
                }
                wholeMode = g.hasWhole();
            } else if (wholeMode) {
                g.mountDirs.clear();
                g.mountDirs.add(Model.WHOLE);
                g.extraLinks.clear();
                g.dirs.clear();
            } else {
                g.dirs.clear();
                g.mountDirs.remove(Model.WHOLE);
                filterExtraCovered();
            }

            String u = g.primary.user;
            String p = g.primary.pkg;
            String label = Desync.label(this, p);
            String display = p.isEmpty() ? getString(R.string.action_pick_app)
                    : (label.equals(p) ? p : label + " · " + p);
            primaryStatus.setText(getString(R.string.msg_primary_selected,
                    u.isEmpty() ? "0" : u, display));
            primaryApp.setText(p.isEmpty() ? getString(R.string.action_pick_app) : display);
            primaryUser.setText(getString(R.string.label_user) + " · " + (u.isEmpty() ? "0" : u));
            modeButton.setText(Desync.modeLabel(this, g.mode, true));
            modeHint.setText(g.isMount() ? R.string.mode_mount_hint
                    : (Model.MODE_COPY.equals(g.mode)
                        ? R.string.mode_copy_hint : R.string.mode_hardlink_hint));
            scopeLabel.setText(scopeTitle());
            scopeButton.setText(wholeMode
                    ? (mount ? getString(R.string.mode_whole) : getString(R.string.scope_all_files))
                    : (mount ? getString(R.string.scope_subdirs)
                             : getString(R.string.scope_pick_items)));

            dirsWholeNote.setVisibility(mount && wholeMode ? View.VISIBLE : View.GONE);
            dirsAutoWhole.setVisibility(!mount && wholeMode ? View.VISIBLE : View.GONE);
            boolean subdirs = !wholeMode;
            dirsSection.setVisibility(subdirs ? View.VISIBLE : View.GONE);
            boolean showExtra = mount && subdirs && (hasRootFiles || !g.extraLinks.isEmpty());
            if (showExtra) {
                if (extraSection.getParent() == null) {
                    page.addView(extraSection, page.indexOfChild(targetsLabel), Util.fullWidth());
                }
            } else {
                page.removeView(extraSection);
            }
            maybeScanRootFiles(mount && subdirs);
            List<String> shown = mount ? g.mountDirs : g.dirs;
            dirsLabel.setText(getString(R.string.msg_dir_count,
                    String.valueOf(shown.size())) + " : "
                    + String.join(", ", shown));
            extraLabel.setText(getString(R.string.msg_file_count,
                    String.valueOf(g.extraLinks.size())) + " : "
                    + String.join(", ", g.extraLinks));
            sentinelSection.setVisibility(advanced.isChecked() ? View.VISIBLE : View.GONE);
            addTargetButton.setVisibility(g.primary.pkg.isEmpty()
                    ? View.GONE : View.VISIBLE);
            renderTargets();
        } finally {
            updating = false;
        }
    }

    private void maybeScanRootFiles(boolean subdirs) {
        if (!subdirs || g.primary.pkg.isEmpty()) {
            return;
        }
        String key = g.primary.user + "/" + g.primary.pkg;
        if (scanPending || key.equals(scanKey)) {
            return;
        }
        scanKey = key;
        scanPending = true;
        final String u = g.primary.user.isEmpty() ? "0" : g.primary.user;
        final String p = g.primary.pkg;
        new Thread(() -> {
            Backend.Result r = Deploy.scanItems(u, p);
            boolean has = !r.ok || r.data.contains("F|");
            Util.ui(this, () -> {
                scanPending = false;
                hasRootFiles = has;
                refreshUi();
            });
        }).start();
    }

    private int scopeTitle() {
        if (g.isMount()) {
            return R.string.label_scope;
        }
        return Model.MODE_COPY.equals(g.mode)
                ? R.string.label_scope_copy : R.string.label_scope_link;
    }

    private boolean shellBackend() {
        return "shizuku".equals(Backends.current().id());
    }

    private static List<String> removeCovered(List<String> items, List<String> parents) {
        List<String> out = new ArrayList<>();
        for (String a : items) {
            boolean covered = false;
            for (String b : parents) {
                if (!Model.WHOLE.equals(b) && !a.equals(b) && a.startsWith(b + "/")) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                out.add(a);
            }
        }
        return out;
    }

    private void renderTargets() {
        targetList.removeAllViews();
        for (int i = 0; i < g.targets.size(); i++) {
            final int pos = i;
            Model.Entry e = g.targets.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = new TextView(this);
            label.setText((i + 1) + ". " + (e.isContainer()
                    ? Desync.label(this, e.host) + " · " + e.container
                    : e.user + " · " + e.pkg));
            label.setTextSize(14f);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(button(R.string.action_edit, v -> showTargetDialog(pos)));
            row.addView(button(R.string.action_delete, v -> {
                g.targets.remove(pos);
                dirty = true;
                refreshUi();
            }));
            targetList.addView(row, Util.fullWidth());
        }
        if (g.targets.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(R.string.msg_no_targets);
            targetList.addView(t, Util.fullWidth());
        }
    }

    private void showTargetDialog(int position) {
        Containers.hosts(this, hosts -> showTargetDialog(position, hosts));
    }

    private void showTargetDialog(int position, List<Containers.Host> hosts) {
        Model.Entry src = position < 0 ? new Model.Entry() : g.targets.get(position);
        final Model.Entry d = new Model.Entry();
        d.kind = src.kind;
        d.user = src.user;
        d.pkg = position < 0 ? g.primary.pkg : src.pkg;
        d.host = src.host;
        d.container = src.container;
        d.inner = src.inner;
        d.tpl = src.tpl;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);

        Button kindButton = new Button(this);
        if (position >= 0 || !hosts.isEmpty()) {
            box.addView(kindButton, Util.fullWidth());
        }
        Button appButton = new Button(this);
        box.addView(appButton, Util.fullWidth());
        Button userButton = new Button(this);
        box.addView(userButton, Util.fullWidth());

        final Runnable refresh = () -> {
            kindButton.setText(d.isContainer()
                    ? Desync.label(this, d.host) : getString(R.string.kind_native));
            appButton.setText(d.pkg.isEmpty()
                    ? getString(R.string.action_pick_app) : d.pkg);
            userButton.setText(getString(R.string.label_user) + " · " + d.user);
        };
        refresh.run();

        kindButton.setOnClickListener(v -> {
            CharSequence[] kinds = new CharSequence[hosts.size() + 1];
            kinds[0] = getString(R.string.kind_native);
            for (int i = 0; i < hosts.size(); i++) {
                kinds[i + 1] = hosts.get(i).name;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.label_target_kind)
                    .setItems(kinds, (dlg, w) -> {
                        if (w == 0) {
                            d.kind = Model.KIND_NATIVE;
                            d.host = "";
                            d.container = "";
                            d.inner = "";
                            d.tpl = "";
                        } else {
                            d.kind = Model.KIND_CONTAINER;
                            d.host = hosts.get(w - 1).pkg;
                            d.tpl = Containers.expandForHost(this, d.host, d.user);
                        }
                        refresh.run();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        });

        appButton.setOnClickListener(v -> {
            if (d.isContainer()) {
                Containers.pick(this, d.user, d.host, ch -> {
                    d.host = ch.host;
                    d.tpl = ch.tpl;
                    if (!ch.pkg.isEmpty()) {
                        d.pkg = ch.pkg;
                        d.container = ch.pkg;
                        d.inner = ch.pkg;
                    }
                    refresh.run();
                });
            } else {
                Pickers.pickApp(this, d.user, Desync.peers(this, g.primary.pkg), p -> {
                    d.pkg = p;
                    refresh.run();
                });
            }
        });
        userButton.setOnClickListener(v -> Pickers.pickUser(this, s -> {
            boolean changed = !s.equals(d.user);
            d.user = s;
            if (d.isContainer()) {
                String t = Containers.expandForHost(this, d.host, s);
                if (!t.isEmpty()) {
                    d.tpl = t;
                }
            }
            refresh.run();
            if (changed && shellBackend() && !"0".equals(s) && !shellRiskShown) {
                shellRiskShown = true;
                Desync.showShellRisk(this, null);
            } else if (changed && g.isMount() && !d.isContainer()
                    && !s.equals(g.primary.user)) {
                Desync.showCrossUserRisk(this);
            }
        }));

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(position < 0 ? R.string.action_add_target : R.string.action_edit)
                .setView(box)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (d.pkg.isEmpty() || d.user.isEmpty()) {
                Util.toast(this, getString(R.string.err_pkg));
                return;
            }
            if (!d.isContainer() && d.user.equals(g.primary.user)
                    && d.pkg.equals(g.primary.pkg)) {
                Warn.info(this, getString(R.string.err_same_path),
                        getString(R.string.err_target_same_body));
                return;
            }
            if (duplicate(d, position)) {
                Warn.info(this, getString(R.string.err_duplicate_target),
                        getString(R.string.err_target_dup_body));
                return;
            }
            if (position < 0) {
                g.targets.add(d);
            } else {
                g.targets.set(position, d);
            }
            dirty = true;
            dlg.dismiss();
            refreshUi();
        });
    }

    private boolean duplicate(Model.Entry candidate, int position) {
        if (occupiedElsewhere(candidate.user, appPkg(candidate), scopeOf(candidate))) {
            return true;
        }
        String key = candidate.targetKey();
        for (int j = 0; j < g.targets.size(); j++) {
            if (j != position && g.targets.get(j).targetKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String appPkg(Model.Entry t) {
        return t.isContainer() ? t.inner : t.pkg;
    }

    private static String scopeOf(Model.Entry t) {
        return t.isContainer() ? "C|" + t.host : "N";
    }

    private boolean occupiedElsewhere(String user, String pkg, String scope) {
        for (Model.Group other : Session.get().groups) {
            if (other.id.equals(g.id)) {
                continue;
            }
            if (other.primary.user.equals(user) && appPkg(other.primary).equals(pkg)
                    && scopeOf(other.primary).equals(scope)) {
                return true;
            }
            for (Model.Entry t : other.targets) {
                if (t.user.equals(user) && appPkg(t).equals(pkg)
                        && scopeOf(t).equals(scope)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void save() {
        if (applying) {
            return;
        }
        String n = nameInput.getText().toString().trim();
        String next = n.isEmpty() ? "group-" + g.id.substring(0, 6) : n;
        if (!next.equals(g.name)) {
            g.name = next;
            dirty = true;
        }
        String sent = advanced.isChecked() ? sentinel.getText().toString().trim() : "";
        if (!sent.equals(g.sentinel)) {
            dirty = true;
        }
        g.sentinel = sent;
        if (!dirty && !isNew) {
            finish();
            return;
        }
        refreshUi();
        String err = Model.validate(g);
        if (!err.isEmpty()) {
            Util.toast(this, getString(getResources().getIdentifier(err, "string", getPackageName())));
            return;
        }
        if (isNew && occupiedElsewhere(g.primary.user, g.primary.pkg, scopeOf(g.primary))) {
            Warn.info(this, getString(R.string.err_primary_duplicate),
                    getString(R.string.err_primary_duplicate_body));
            return;
        }
        for (int i = 0; i < g.targets.size(); i++) {
            if (duplicate(g.targets.get(i), i)) {
                Warn.info(this, getString(R.string.err_duplicate_target),
                        getString(R.string.err_target_dup_body));
                return;
            }
        }
        Desync.showSaveNotice(this, g, isNew, this::commit);
    }

    private void commit() {
        Model.Group live = isNew ? null : Model.find(Session.get(), gid);
        if (live != null) {
            boolean identityUnchanged = live.primary.user.equals(g.primary.user)
                    && live.primary.pkg.equals(g.primary.pkg)
                    && live.sentinel.equals(g.sentinel)
                    && live.mode.equals(g.mode)
                    && sameTargets(live, g);
            if (identityUnchanged) {
                g.versionFlag = live.versionFlag;
                g.srcVersion = live.srcVersion;
                g.dstVersions.clear();
                g.dstVersions.addAll(live.dstVersions);
            } else {
                g.versionFlag = 0;
                g.srcVersion = "";
                g.dstVersions.clear();
            }
        }
        if (isNew || structuralChanged(live)) {
            Session.markModeChanged(g.id);
        }
        if (isNew) {
            Session.get().groups.add(g);
        } else {
            for (int i = 0; i < Session.get().groups.size(); i++) {
                if (Session.get().groups.get(i).id.equals(gid)) {
                    Session.get().groups.set(i, g);
                    break;
                }
            }
        }
        Session.save(this);
        applyAndFinish();
    }

    private boolean structuralChanged(Model.Group live) {
        if (live == null) {
            return true;
        }
        return !live.mode.equals(g.mode)
                || !live.primary.user.equals(g.primary.user)
                || !live.primary.pkg.equals(g.primary.pkg)
                || !live.extraMode.equals(g.extraMode)
                || !live.mountDirs.equals(g.mountDirs)
                || !live.extraLinks.equals(g.extraLinks)
                || !live.dirs.equals(g.dirs)
                || !sameTargets(live, g);
    }

    private void applyAndFinish() {
        applying = true;
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        boolean controllable = Model.MODE_HARDLINK.equals(g.mode)
                && (isNew || Session.modeChangedGids().contains(g.id));
        final Progress.Handle h = Progress.show(this, getString(R.string.progress_title),
                controllable, this::finish);
        new Thread(() -> {
            Backend.Result r = Actions.submitLive(this,
                    isNew ? Model.rulesForGroup(Session.get(), g.id) : "", h::step);
            Actions.markApplied(this, g.id, r, false);
            String msg = "CANCELLED".equals(r.code) ? getString(R.string.progress_cancelled)
                    : (r.ok
                        ? ("PARTIAL".equals(r.code)
                            ? getString(R.string.msg_rule_partial, r.message)
                            : getString(R.string.msg_applied))
                        : getString(R.string.msg_failed, r.code + " " + r.message));
            h.finish(msg);
            Progress.saveLog(h.log());
        }).start();
    }

    private Button button(int res, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(res);
        b.setOnClickListener(l);
        return b;
    }

    private TextView section(int res) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(15f);
        t.setPadding(0, Util.dp(this, 14), 0, Util.dp(this, 4));
        return t;
    }

}
