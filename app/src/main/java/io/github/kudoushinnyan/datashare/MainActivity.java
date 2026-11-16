package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private LinearLayout box;
    private Button moduleButton;

    @Override
    public void onBackPressed() {
        if (!Progress.busy()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Util.dp(this, 16);
        box.setPadding(p, p, p, p);
        sv.addView(box);
        setContentView(sv);
        Util.fitSystemBars(sv, box, p);
        Session.setChecking(true);
        if (!Session.isLoaded()) {
            Session.load(this);
        }
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Thread(() -> {
            Perm.probe(this);
            Backends.refresh(this);
            if (Session.get().groups.isEmpty()) {
                Session.setRestoring(true);
                Util.ui(this, this::render);
                boolean restored = Session.restoreFromSnapshot(this, Deploy.snapshot());
                if (restored) {
                    Versions.reconcileRestored(this);
                    Session.setRestoring(false);
                } else {
                    Backend.Result ls = Deploy.linkstateRead();
                    final String copy = ls.data;
                    final int verdict = ls.ok && !copy.isEmpty()
                            ? Session.restoreCheck(MainActivity.this, copy) : 0;
                    if (verdict == 1 && Session.restorableHardlinks(copy)) {
                        Util.ui(MainActivity.this, () -> new AlertDialog.Builder(MainActivity.this)
                                .setTitle(getString(R.string.config_restore_title))
                                .setMessage(getString(R.string.config_restore_body))
                                .setPositiveButton(getString(R.string.config_restore_accept), (d, w) -> {
                                    Session.mergeLinkState(MainActivity.this, copy);
                                    new Thread(() -> {
                                        Versions.reconcileRestored(MainActivity.this);
                                        Session.setRestoring(false);
                                        Util.ui(MainActivity.this, () -> render());
                                    }).start();
                                })
                                .setNegativeButton(getString(R.string.config_restore_decline), (d, w) -> {
                                    Session.setRestoring(false);
                                    render();
                                })
                                .show());
                    } else {
                        if (verdict == -1) {
                            Util.ui(this, () -> Util.toast(this, getString(R.string.msg_linkstate_bad_sig)));
                        } else if (verdict == 1) {
                            Deploy.linkstateRemove();
                        }
                        Session.setRestoring(false);
                    }
                }
            } else if (Backends.moduleInstalled()) {
                Backend.Result snap = Deploy.snapshot();
                if (snap.ok && !snap.data.isEmpty()) {
                    Model.State remote = Model.parseState(snap.data);
                    if (remote.revision != Session.get().revision
                            || !remote.instanceId.equals(Session.get().instanceId)) {
                        Deploy.syncState(Session.get());
                    }
                }
            }
            int changes = Versions.autoCheck(this);
            int pending = VersionEvent.consume(this);
            if (Session.hasDetectableGroups()) {
                KeepAlive.startWatch(this);
            } else {
                KeepAlive.stopWatch(this);
            }
            final int all = changes | pending;
            Session.setChecking(false);
            Util.ui(this, () -> {
                render();
                if (all == 0) {
                    return;
                }
                if ((all & VersionEvent.MOUNT) != 0) {
                    Warn.info(this, getString(R.string.terminate_auto_title),
                            getString(R.string.terminate_auto_body));
                } else {
                    VersionEvent.show(this, all);
                }
            });
        }).start();
    }

    private void render() {
        box.removeAllViews();
        final boolean unlocked = Perm.unlocked();

        TextView appTitle = new TextView(this);
        appTitle.setText(R.string.app_name);
        appTitle.setTextSize(20f);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appTitle.setPadding(0, 0, 0, Util.dp(this, 10));
        box.addView(appTitle, Util.fullWidth());

        box.addView(section(R.string.perm_title));
        TextView body = new TextView(this);
        body.setText(R.string.perm_body);
        body.setTextSize(12f);
        box.addView(body, Util.fullWidth());

        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbShizuku = new RadioButton(this);
        rbShizuku.setId(View.generateViewId());
        rbShizuku.setText(R.string.perm_shizuku);
        RadioButton rbRoot = new RadioButton(this);
        rbRoot.setId(View.generateViewId());
        rbRoot.setText(R.string.perm_root);
        String sel = Perm.selected();
        rbShizuku.setChecked(Perm.SHIZUKU.equals(sel));
        rbRoot.setChecked(Perm.ROOT.equals(sel));
        rg.addView(rbShizuku, weighted());
        rg.addView(rbRoot, weighted());
        box.addView(rg, Util.fullWidth());
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            Perm.select(this, checkedId == rbRoot.getId() ? Perm.ROOT : Perm.SHIZUKU);
            render();
            new Thread(() -> {
                Backends.refresh(this);
                Util.ui(this, this::render);
            }).start();
        });

        boolean granted = Perm.granted(sel);
        Button request = new Button(this);
        request.setText(granted ? R.string.perm_granted : R.string.perm_request);
        request.setEnabled(!granted);
        request.setOnClickListener(v -> requestPermission());
        box.addView(request, Util.fullWidth());

        TextView current = new TextView(this);
        current.setText(getString(R.string.perm_current, currentBackendLabel()));
        box.addView(current, Util.fullWidth());
        if (Session.hasDetectableGroups()) {
            TextView moduleNote = new TextView(this);
            moduleNote.setText(Backends.moduleInstalled()
                    ? R.string.status_module_flashed : R.string.perm_no_module);
            moduleNote.setTextSize(12f);
            box.addView(moduleNote, Util.fullWidth());
        }
        if (!unlocked) {
            TextView lock = new TextView(this);
            lock.setText(Session.get().groups.isEmpty()
                    ? R.string.perm_locked_hint : R.string.perm_restored_locked);
            box.addView(lock, Util.fullWidth());
        }

        if (unlocked && Session.hasDetectableGroups()) {
            box.addView(section(R.string.keep_title));
            TextView keepBody = new TextView(this);
            keepBody.setText(R.string.keep_body);
            keepBody.setTextSize(12f);
            box.addView(keepBody, Util.fullWidth());
            final boolean keepOn = KeepAlive.enabled(this);
            Button keepBtn = new Button(this);
            keepBtn.setText(keepOn ? R.string.keep_revoke : R.string.keep_allow);
            keepBtn.setEnabled(unlocked);
            keepBtn.setOnClickListener(v -> confirmKeep(!keepOn));
            box.addView(keepBtn, Util.fullWidth());
            TextView keepStatus = new TextView(this);
            keepStatus.setText(keepOn ? R.string.keep_status_on : R.string.keep_status_off);
            keepStatus.setTextSize(12f);
            box.addView(keepStatus, Util.fullWidth());
            Button keepVendor = new Button(this);
            keepVendor.setText(R.string.keep_vendor);
            keepVendor.setOnClickListener(v -> KeepAlive.vendorSettings(this));
            box.addView(keepVendor, Util.fullWidth());
        }

        moduleButton = new Button(this);
        int st = ModuleNotice.state();
        TextView moduleHint = new TextView(this);
        moduleHint.setText(R.string.module_button_hint);
        moduleHint.setTextSize(12f);
        if (st == ModuleNotice.CHECKING) {
            moduleButton.setText(R.string.module_checking);
        } else if (st == ModuleNotice.HIDDEN) {
            moduleButton.setVisibility(View.GONE);
            moduleHint.setVisibility(View.GONE);
        } else if (st == ModuleNotice.PENDING) {
            moduleButton.setText(R.string.module_gen_pending);
        } else {
            moduleButton.setText(R.string.module_gen);
        }
        moduleButton.setEnabled(unlocked && st != ModuleNotice.CHECKING && st != ModuleNotice.HIDDEN);
        moduleButton.setOnClickListener(v -> generateModule());
        box.addView(moduleButton, Util.fullWidth());
        box.addView(moduleHint, Util.fullWidth());

        box.addView(section(R.string.label_groups));
        if (Session.isRestoring()) {
            TextView restoring = new TextView(this);
            restoring.setText(R.string.msg_restoring);
            box.addView(restoring, Util.fullWidth());
        } else if (Session.get().groups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.msg_no_groups);
            box.addView(empty, Util.fullWidth());
        } else {
            for (Model.Group g : Session.get().groups) {
                addGroupCard(g, unlocked);
            }
        }

        box.addView(buttonRow(
                button(R.string.action_add_group, v -> edit(null), unlocked),
                button(R.string.action_about, v -> go(AboutActivity.class))), Util.fullWidth());
    }

    private void addGroupCard(Model.Group g, boolean unlocked) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(this, 12);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundColor(Color.parseColor("#FFFFFF"));
        LinearLayout.LayoutParams clp = Util.fullWidth();
        clp.topMargin = Util.dp(this, 8);
        box.addView(card, clp);

        TextView title = new TextView(this);
        title.setText(g.name + " · " + Desync.modeLabel(this, g.mode, false));
        title.setTextSize(16f);
        card.addView(title, Util.fullWidth());

        java.util.List<String> dirs = g.isMount() ? g.mountDirs : g.dirs;
        TextView info = new TextView(this);
        info.setText(g.primary.user + " · " + g.primary.pkg + "  →  "
                + getString(R.string.msg_target_count, String.valueOf(g.targets.size())) + " · "
                + getString(R.string.msg_dir_count, String.valueOf(dirs.size())));
        info.setTextSize(12f);
        info.setTextColor(Color.parseColor("#8A9099"));
        card.addView(info, Util.fullWidth());

        TextView state = new TextView(this);
        state.setText(stateLine(g));
        state.setTextSize(13f);
        state.setBackgroundColor(Color.parseColor("#FFF3E0"));
        state.setPadding(pad / 2, pad / 3, pad / 2, pad / 3);
        card.addView(state, Util.fullWidth());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, Util.fullWidth());
        if (g.isMount()) {
            Button main = new Button(this);
            if (Model.STATUS_TERMINATED.equals(g.status)) {
                main.setText(R.string.action_reshare);
                main.setOnClickListener(v -> confirmRestore(g));
            } else if (Model.STATUS_PAUSED.equals(g.status)) {
                main.setText(R.string.action_resume);
                main.setOnClickListener(v -> confirmSimple(g,
                        R.string.action_resume, R.string.pause_note, "resume"));
            } else {
                main.setText(R.string.action_pause);
                main.setOnClickListener(v -> confirmSimple(g,
                        R.string.action_pause, R.string.pause_note, "pause"));
            }
            main.setEnabled(unlocked);
            row.addView(main, weighted());
            if (!Model.STATUS_TERMINATED.equals(g.status)) {
                row.addView(button(R.string.action_terminate,
                        v -> confirmTerminate(g), unlocked), weighted());
            }
        } else if (Model.MODE_HARDLINK.equals(g.mode)) {
            row.addView(button(R.string.action_clear,
                    v -> confirmClear(g, "clear", R.string.result_cleared), unlocked), weighted());
            row.addView(button(R.string.action_rebuild,
                    v -> confirmClear(g, "rebuild", R.string.result_rebuilt), unlocked), weighted());
        } else {
            row.addView(button(R.string.action_clear,
                    v -> confirmClear(g, "clear", R.string.result_cleared), unlocked), weighted());
            row.addView(button(R.string.action_resync,
                    v -> confirmClear(g, "resync", R.string.result_resynced), unlocked), weighted());
        }

        card.setOnClickListener(v -> edit(g.id));
        card.setOnLongClickListener(v -> {
            if (unlocked) {
                confirmDeleteGroup(Session.get().groups.indexOf(g));
            }
            return true;
        });
    }

    private String stateLine(Model.Group g) {
        if (!g.enabled) {
            return getString(R.string.status_row_disabled);
        }
        if (Model.STATUS_PAUSED.equals(g.status)) {
            return getString(R.string.status_line_paused);
        }
        if (Model.STATUS_TERMINATED.equals(g.status)) {
            return getString(R.string.status_line_terminated);
        }
        if (!g.applied) {
            return getString(R.string.status_line_failed);
        }
        if (Model.MODE_HARDLINK.equals(g.mode)) {
            if (g.versionFlag == 1) {
                return getString(R.string.status_row_pending);
            }
            if (g.versionFlag == 2) {
                return getString(R.string.status_row_secondary);
            }
        }
        return getString(R.string.status_line_active);
    }

    private void confirmSimple(Model.Group g, int title, int body, String op) {
        Warn.confirm(this, getString(title), getString(body), () -> runGroup(g, op, 0));
    }

    private void confirmTerminate(Model.Group g) {
        Warn.confirm(this, getString(R.string.terminate_title),
                getString(R.string.terminate_body),
                () -> runGroup(g, "terminate", R.string.result_terminated));
    }

    private void confirmRestore(Model.Group g) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_reshare)
                .setMessage(R.string.restore_body)
                .setPositiveButton(R.string.restore_accept,
                        (d, w) -> runGroup(g, "restore", R.string.result_restored))
                .setNeutralButton(R.string.restore_no_change,
                        (d, w) -> runGroup(g, "restore", R.string.result_restored))
                .setNegativeButton(R.string.restore_decline, null)
                .show();
    }

    private void confirmClear(Model.Group g, String op, int resultKey) {
        int label = op.equals("rebuild") ? R.string.action_rebuild
                : (op.equals("resync") ? R.string.action_resync : R.string.action_clear);
        Warn.confirm(this, getString(R.string.confirm_title, getString(label)),
                getString(R.string.confirm_body),
                () -> runGroup(g, op, resultKey));
    }

    private void runGroup(Model.Group g, String op, int resultKey) {
        boolean controllable = Model.MODE_HARDLINK.equals(g.mode) && g.enabled
                && !"clear".equals(op);
        final Progress.Handle h = Progress.show(this, getString(R.string.progress_title),
                controllable, this::render);
        new Thread(() -> {
            Backend.Result r = Actions.op(this, g.id, op, h::step);
            Actions.markApplied(this, g.id, r, "clear".equals(op));
            int okKey = resultKey == 0 ? R.string.msg_applied : resultKey;
            String msg = "CANCELLED".equals(r.code) ? getString(R.string.progress_cancelled)
                    : (r.ok ? getString(okKey) : getString(R.string.result_failed));
            h.finish(msg);
            Progress.saveLog(h.log());
        }).start();
    }

    private String currentBackendLabel() {
        String id = Backends.current().id();
        if ("root".equals(id)) {
            return getString(R.string.backend_root);
        }
        if ("sui".equals(id)) {
            return getString(R.string.backend_sui);
        }
        if ("shizuku".equals(id)) {
            return getString(R.string.backend_shizuku);
        }
        return getString(R.string.backend_none);
    }

    private void requestPermission() {
        final String which = Perm.selected();
        if (Perm.granted(which)) {
            return;
        }
        Perm.request(this, which, () -> new Thread(() -> {
            Perm.refreshIdentity(this);
            final boolean incompatible = Perm.granted(which)
                    && Perm.SHIZUKU.equals(which)
                    && !Perm.shizukuCanMount()
                    && Session.hasDetectableGroups();
            Backends.refresh(this);
            Util.ui(this, () -> {
                if (!Perm.granted(which)) {
                    Util.toast(this, getString(R.string.perm_request_denied));
                } else if (incompatible) {
                    Warn.info(this, getString(R.string.warn_backend_mount_title),
                            getString(R.string.warn_mode_incompatible_body));
                }
                render();
            });
        }).start());
    }

    private void confirmKeep(boolean on) {
        new AlertDialog.Builder(this)
                .setTitle(on ? R.string.keep_allow : R.string.keep_revoke)
                .setMessage(getString(on ? R.string.keep_confirm_body : R.string.keep_revoke_body))
                .setPositiveButton(R.string.confirm_accept, (d, w) -> {
                    if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                        try {
                            requestPermissions(
                                    new String[]{"android.permission.POST_NOTIFICATIONS"}, 5100);
                        } catch (Exception ignored) {
                        }
                    }
                    new Thread(() -> {
                        Backend.Result r = KeepAlive.apply(MainActivity.this, on);
                        boolean ok = r.ok && r.data.contains("ADS_KEEPALIVE_OK");
                        KeepAlive.setEnabled(MainActivity.this, on);
                        if (on) {
                            KeepAlive.startWatch(MainActivity.this);
                        } else {
                            KeepAlive.stopWatch(MainActivity.this);
                        }
                        Util.ui(MainActivity.this, () -> {
                            if (!ok) {
                                Util.toast(MainActivity.this, getString(R.string.keep_failed));
                            }
                            render();
                        });
                    }).start();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDeleteGroup(int index) {
        if (index >= Session.get().groups.size()) {
            return;
        }
        final Model.Group g = Session.get().groups.get(index);
        final String gid = g.id;
        final String clearRules = Model.rulesForGroup(Session.get(), gid);
        if (g.isMount()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_delete)
                    .setMessage(getString(R.string.delete_mount_body, g.name))
                    .setPositiveButton(R.string.confirm_accept,
                            (d, w) -> performDelete(index, gid, clearRules))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else {
            Warn.confirmCheck(this, getString(R.string.action_delete), g.name,
                    getString(R.string.delete_clear_check),
                    checked -> performDelete(index, gid, checked ? clearRules : ""));
        }
    }

    private void performDelete(int index, String gid, String clearRules) {
        Session.markModeChanged(gid);
        Session.get().groups.remove(index);
        Session.save(this);
        final Progress.Handle h = Progress.show(this, getString(R.string.progress_title),
                false, this::render);
        new Thread(() -> {
            Backend.Result r = Actions.submitLive(this, clearRules, h::step);
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

    private void edit(String gid) {
        if (!Perm.unlocked()) {
            Util.toast(this, getString(R.string.perm_locked_hint));
            return;
        }
        Intent i = new Intent(this, GroupActivity.class);
        if (gid != null) {
            i.putExtra("gid", gid);
        }
        startActivity(i);
    }

    private void generateModule() {
        if (!Perm.unlocked()) {
            Util.toast(this, getString(R.string.perm_locked_hint));
            return;
        }
        moduleButton.setEnabled(false);
        Util.toast(this, getString(R.string.msg_module_busy));
        new Thread(() -> {
            boolean ok = ModuleGen.generate(this);
            Util.ui(this, () -> {
                moduleButton.setEnabled(true);
                if (!ok) {
                    Util.toast(this, getString(R.string.msg_module_failed));
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.module_gen_title))
                        .setMessage(R.string.module_install_hint)
                        .setPositiveButton(getString(R.string.action_install_later), null)
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show();
                Backends.setModulePublished(true);
                render();
            });
        }).start();
    }

    private Button button(int text, View.OnClickListener l) {
        return button(text, l, true);
    }

    private Button button(int text, View.OnClickListener l, boolean enabled) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        b.setEnabled(enabled);
        return b;
    }

    private LinearLayout buttonRow(Button a, Button b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(a, weighted());
        row.addView(b, weighted());
        return row;
    }

    private TextView section(int res) {
        TextView t = new TextView(this);
        t.setText(res);
        t.setTextSize(16f);
        t.setPadding(0, Util.dp(this, 10), 0, Util.dp(this, 4));
        return t;
    }

    private void go(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }
}
