package io.github.kudoushinnyan.datashare;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

final class Versions {

    static final int SAME = 0;
    static final int CHANGED = 1;
    static final int UNKNOWN = 2;
    static final String UNSET = "vc=-|vn=-|sent=-";

    private Versions() {
    }

    static String read(Context c, String user, String pkg, String sentinel) {
        Backend.Result r = Deploy.versions(user, pkg, sentinel);
        if (!r.ok || r.data.trim().isEmpty()) {
            return UNSET;
        }
        String v = r.data.trim().split("\n")[0].trim();
        return v.isEmpty() ? UNSET : v;
    }

    static boolean isUnknown(String v) {
        return v == null || v.isEmpty() || UNSET.equals(v);
    }

    static int groupState(Context c, Model.Group g) {
        if (g.targets.isEmpty()) {
            return UNKNOWN;
        }
        boolean anyKnown = false;
        String cur = read(c, g.primary.user, g.primary.pkg, g.sentinel);
        if (!isUnknown(cur)) {
            anyKnown = true;
            if (!g.srcVersion.isEmpty() && !g.srcVersion.equals(cur)) {
                return CHANGED;
            }
        }
        for (int i = 0; i < g.targets.size(); i++) {
            Model.Entry t = g.targets.get(i);
            if (t.isContainer()) {
                continue;
            }
            String tv = read(c, t.user, t.pkg, g.sentinel);
            if (!isUnknown(tv)) {
                anyKnown = true;
                String base = i < g.dstVersions.size() ? g.dstVersions.get(i) : "";
                if (!base.isEmpty() && !base.equals(tv)) {
                    return CHANGED;
                }
            }
        }
        return anyKnown ? SAME : UNKNOWN;
    }

    static int hardlinkFlag(Context c, Model.Group g) {
        if (g.targets.isEmpty()) {
            return 0;
        }
        String cur = read(c, g.primary.user, g.primary.pkg, g.sentinel);
        if (isUnknown(cur)) {
            return -1;
        }
        if (!g.srcVersion.isEmpty() && !g.srcVersion.equals(cur)) {
            return 1;
        }
        for (int i = 0; i < g.targets.size(); i++) {
            Model.Entry t = g.targets.get(i);
            if (t.isContainer()) {
                continue;
            }
            String tv = read(c, t.user, t.pkg, g.sentinel);
            String base = i < g.dstVersions.size() ? g.dstVersions.get(i) : "";
            if (!isUnknown(tv) && !base.isEmpty() && !base.equals(tv)) {
                return 2;
            }
        }
        return 0;
    }

    static void refreshBaselines(Context c, Model.Group g) {
        g.srcVersion = read(c, g.primary.user, g.primary.pkg, g.sentinel);
        List<String> next = new ArrayList<>();
        for (Model.Entry t : g.targets) {
            next.add(t.isContainer() ? UNSET : read(c, t.user, t.pkg, g.sentinel));
        }
        g.dstVersions.clear();
        g.dstVersions.addAll(next);
        g.versionFlag = 0;
    }

    static synchronized int reconcileRestored(Context c) {
        boolean dirty = false;
        for (Model.Group g : Session.get().groups) {
            if (!g.enabled || !Model.MODE_HARDLINK.equals(g.mode)) {
                continue;
            }
            if (isUnknown(g.srcVersion) || g.srcVersion.isEmpty()) {
                refreshBaselines(c, g);
                dirty = true;
                continue;
            }
            int hf = hardlinkFlag(c, g);
            if (hf < 0) {
                continue;
            }
            if (hf != g.versionFlag) {
                g.versionFlag = hf;
                dirty = true;
            }
        }
        if (dirty) {
            Session.save(c);
        }
        return dirty ? 1 : 0;
    }

    static synchronized int autoCheck(Context c) {
        int changes = 0;
        boolean dirty = false;
        for (Model.Group g : Session.get().groups) {
            if (!g.enabled) {
                continue;
            }
            int st = groupState(c, g);
            if (st == UNKNOWN || g.targets.isEmpty()) {
                continue;
            }
            if (g.isMount() && (Model.STATUS_ACTIVE.equals(g.status) || Model.STATUS_PAUSED.equals(g.status))
                    && st == CHANGED) {
                Actions.op(c, g.id, "terminate", null);
                changes |= 1;
                dirty = true;
            } else if (Model.MODE_HARDLINK.equals(g.mode)) {
                int hf = hardlinkFlag(c, g);
                if (hf < 0) {
                    continue;
                }
                boolean newly = hf != g.versionFlag && hf == 1;
                if (hf != g.versionFlag) {
                    g.versionFlag = hf;
                    dirty = true;
                }
                if (newly) {
                    changes |= 2;
                }
            }
        }
        if (dirty) {
            Session.save(c);
            Actions.writeLinkState(c);
        }
        return changes;
    }
}
