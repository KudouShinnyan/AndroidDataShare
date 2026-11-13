package io.github.kudoushinnyan.datashare;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

final class Session {

    private static Model.State state = new Model.State();
    private static boolean loaded;
    private static boolean restoring;
    private static String lastRules = "";
    private static boolean checking = true;
    private static final Set<String> changedGids = new LinkedHashSet<>();

    private Session() {
    }

    static Model.State get() {
        return state;
    }

    static void set(Model.State s) {
        state = s;
        loaded = true;
        changedGids.clear();
        lastRules = Model.toRules(s, true);
    }

    static boolean isLoaded() {
        return loaded;
    }

    static boolean isRestoring() {
        return restoring;
    }

    static void setRestoring(boolean v) {
        restoring = v;
    }

    static boolean isChecking() {
        return checking;
    }

    static void setChecking(boolean v) {
        checking = v;
    }

    static void bump() {
        state.revision++;
    }

    static String lastRules() {
        return lastRules;
    }

    static void setLastRules(String rules) {
        lastRules = rules == null ? "" : rules;
    }

    static void markModeChanged(String gid) {
        changedGids.add(gid);
    }

    static Set<String> modeChangedGids() {
        return new LinkedHashSet<>(changedGids);
    }

    static void clearChangedGids() {
        changedGids.clear();
    }

    static boolean hasDetectableGroups() {
        for (Model.Group g : state.groups) {
            if (g.enabled && g.isMount()) {
                return true;
            }
        }
        return false;
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "state.json");
    }

    static void load(Context c) {
        try {
            File f = file(c);
            if (f.exists()) {
                String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (!t.trim().isEmpty()) {
                    set(Model.parseState(t));
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        state = new Model.State();
        loaded = false;
    }

    static void save(Context c) {
        try {
            Files.write(file(c).toPath(), Model.toStateJson(state).getBytes(StandardCharsets.UTF_8));
            loaded = true;
        } catch (Exception ignored) {
        }
    }

    static boolean restoreFromSnapshot(Context c, Backend.Result snapshot) {
        if (!snapshot.ok || snapshot.data.isEmpty()) {
            return false;
        }
        Model.State s = Model.parseState(snapshot.data);
        if (s.groups.isEmpty()) {
            return false;
        }
        set(s);
        save(c);
        return true;
    }

    static int restoreCheck(Context c, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            JSONObject h = new JSONObject(text.split("\n")[0]);
            if (h.optInt("revoked", 0) != 0) {
                return 0;
            }
            String sig = h.optString("sig", "");
            if (sig.isEmpty()) {
                return 0;
            }
            return sig.equals(Util.signerDigest(c)) ? 1 : -1;
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean restorableHardlinks(String text) {
        int nl = text.indexOf('\n');
        if (nl < 0) {
            return false;
        }
        try {
            return !Model.parseState(text.substring(nl + 1)).groups.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    static int mergeLinkState(Context c, String text) {
        int nl = text.indexOf('\n');
        String body = nl < 0 ? "" : text.substring(nl + 1);
        try {
            Model.State s = Model.parseState(body);
            int n = 0;
            for (Model.Group g : s.groups) {
                if (!Model.MODE_HARDLINK.equals(g.mode)) {
                    continue;
                }
                boolean exists = false;
                for (Model.Group old : state.groups) {
                    if (old.id.equals(g.id)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    state.groups.add(g);
                    n++;
                }
            }
            if (n > 0) {
                changedGids.clear();
                lastRules = Model.toRules(state, true);
                save(c);
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }
}
