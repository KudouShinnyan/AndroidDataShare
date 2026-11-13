package io.github.kudoushinnyan.datashare;

import android.content.Context;

final class Backends {

    private static volatile Backend current;
    private static volatile boolean installedFlag = false;
    private static volatile boolean publishedFlag = false;

    private Backends() {
    }

    static Backend current() {
        return current == null ? new NoneBackend() : current;
    }

    static boolean moduleInstalled() {
        return installedFlag;
    }

    static boolean modulePublished() {
        return publishedFlag;
    }

    static void setModulePublished(boolean value) {
        publishedFlag = value;
    }

    static synchronized void refresh(Context c) {
        installedFlag = false;
        publishedFlag = false;
        String[] order = Perm.SHIZUKU.equals(Perm.selected())
                ? new String[]{Perm.SHIZUKU, Perm.ROOT}
                : new String[]{Perm.ROOT, Perm.SHIZUKU};
        Backend picked = null;
        for (String id : order) {
            if (!Perm.granted(id)) {
                continue;
            }
            if (Perm.ROOT.equals(id)) {
                RootBackend r = new RootBackend(c);
                if (r.ping().ok) {
                    picked = r;
                }
            } else {
                ShizukuBackend s = new ShizukuBackend(c, false);
                Backend.Result sr = s.ping();
                if (sr.ok) {
                    picked = sr.data.contains("identity=sui")
                            ? new ShizukuBackend(c, true) : s;
                }
            }
            if (picked != null) {
                break;
            }
        }
        current = picked == null ? new NoneBackend() : picked;
        probeModule(c);
    }

    private static void probeModule(Context c) {
        if ("none".equals(current.id())) {
            return;
        }
        try {
            Backend.Result r = current.exec(
                    "engine_flag installed \"$ADS_MODULE_PROP\"\n"
                            + "engine_flag published \"$ADS_MODULE_ZIP\"\n");
            if (!r.ok) {
                return;
            }
            installedFlag = r.data.contains("installed=1");
            publishedFlag = r.data.contains("published=1");
        } catch (Exception ignored) {
        }
    }

    private static final class NoneBackend implements Backend {
        @Override
        public String id() {
            return "none";
        }

        @Override
        public Result ping() {
            return Result.fail("E_NO_BACKEND", "no backend");
        }

        @Override
        public Result exec(String script) {
            return Result.fail("E_NO_BACKEND", "no backend selected");
        }
    }
}
