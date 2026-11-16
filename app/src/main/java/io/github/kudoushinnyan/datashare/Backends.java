package io.github.kudoushinnyan.datashare;

import android.content.Context;

import java.util.Locale;

final class Backends {

    private static final String[] LIST_ASSETS = {
            "containers.txt", "sync_groups.txt", "desync_groups.txt"};
    private static final String[] RELEASE_FILES = {
            "containers.txt", "sync_groups.txt", "desync_groups.txt", "README.txt"};

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
        Config.clearExtra();
        if ("none".equals(current.id())) {
            return;
        }
        String release;
        try {
            release = releaseLists(c);
        } catch (Exception ignored) {
            release = "";
        }
        try {
            Backend.Result r = current.exec(
                    "engine_flag installed \"$ADS_MODULE_PROP\"\n"
                            + "engine_flag published \"$ADS_MODULE_ZIP\"\n"
                            + release
                            + readLists());
            if (!r.ok) {
                return;
            }
            installedFlag = r.data.contains("installed=1");
            publishedFlag = r.data.contains("published=1");
            parseExtra(r.data);
        } catch (Exception ignored) {
        }
    }

    private static final String CTRL_FILE = "/data/local/tmp/ads_ctrl";

    static Backend.Result ctrl(String action) {
        String cmd = "pause".equals(action) || "cancel".equals(action)
                ? "echo " + action + " > " + CTRL_FILE + " 2>/dev/null"
                : "rm -f " + CTRL_FILE + " 2>/dev/null";
        try {
            return current().execRaw(cmd + "\necho ADS_CTRL_OK\n");
        } catch (Exception e) {
            return Backend.Result.fail("E_CTRL", e.toString());
        }
    }

    private static String trim(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) {
            end--;
        }
        return s.substring(0, end);
    }

    private static String langDir() {
        Locale l = Locale.getDefault();
        String lang = l.getLanguage();
        String region = l.getCountry();
        if ("zh".equals(lang)) {
            if ("HK".equals(region)) return "config/zh-HK";
            if ("TW".equals(region)) return "config/zh-TW";
            return "config/zh-CN";
        }
        if ("ja".equals(lang)) return "config/ja";
        return "config/en";
    }

    private static String releaseLists(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("ADS_LIST_DIR=\"$ADS_LINK_DIR\"\n");
        String dir = langDir();
        for (int i = 0; i < RELEASE_FILES.length; i++) {
            String name = RELEASE_FILES[i];
            String text;
            try {
                text = Engine.assetText(c, dir + "/" + name);
            } catch (Exception e) {
                text = Engine.assetText(c, "config/en/" + name);
            }
            sb.append("[ -f \"$ADS_LIST_DIR/").append(name)
                    .append("\" ] || engine_publish \"$ADS_LIST_DIR/").append(name)
                    .append("\" ").append(Engine.heredoc("ADS_L" + i, trim(text)))
                    .append("\n");
        }
        return sb.toString();
    }

    private static String readLists() {
        StringBuilder sb = new StringBuilder();
        for (String a : LIST_ASSETS) {
            sb.append("printf 'ADS_X|").append(a).append("\\n'\n");
            sb.append("engine_read \"$ADS_LIST_DIR/").append(a).append("\"\n");
            sb.append("printf '\\nADS_X|END\\n'\n");
        }
        return sb.toString();
    }

    private static void parseExtra(String data) {
        String cur = null;
        StringBuilder sb = null;
        for (String line : data.split("\n", -1)) {
            if (line.startsWith("ADS_X|")) {
                if (cur != null && sb != null) {
                    Config.setExtra(cur, sb.toString());
                }
                String key = line.substring(6);
                cur = "END".equals(key) ? null : key;
                sb = cur == null ? null : new StringBuilder();
            } else if (sb != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        if (cur != null && sb != null) {
            Config.setExtra(cur, sb.toString());
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
