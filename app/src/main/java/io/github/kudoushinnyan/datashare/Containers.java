package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

final class Containers {

    private static final String ASSET = "containers.txt";
    private static final String SEP = "\u0001";

    static final class Host {
        final String pkg;
        final String name;

        Host(String pkg, String name) {
            this.pkg = pkg;
            this.name = name;
        }
    }

    static final class Choice {
        final String host;
        final String pkg;
        final String tpl;

        Choice(String host, String pkg, String tpl) {
            this.host = host;
            this.pkg = pkg;
            this.tpl = tpl;
        }
    }

    private Containers() {
    }

    static String expand(String tpl, String user) {
        String u = user == null || user.isEmpty() ? "0" : user;
        String s = tpl.replace("/storage/emulated/0/", "/data/media/" + u + "/")
                .replace("/sdcard/", "/data/media/" + u + "/")
                .replace("/data/data/", "/data/user/" + u + "/");
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String[] split(String tpl) {
        int i = tpl.indexOf("{pkg}");
        if (i < 0) {
            return new String[]{tpl, ""};
        }
        return new String[]{tpl.substring(0, i), tpl.substring(i + 5)};
    }

    private static List<String[]> rows(Context c) {
        List<String[]> out = new ArrayList<>();
        for (String[] row : Config.rows(c, ASSET)) {
            if (row.length == 2 && row[1].startsWith("/") && row[1].contains("{pkg}")) {
                out.add(row);
            }
        }
        return out;
    }

    private static String rawTemplate(Context c, String host) {
        for (String[] row : rows(c)) {
            if (row[0].equals(host)) {
                return row[1];
            }
        }
        return "";
    }

    static String expandForHost(Context c, String host, String user) {
        String raw = rawTemplate(c, host);
        return raw.isEmpty() ? "" : expand(raw, user);
    }

    private static Set<String> installed(Context c) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pickers.USER_INFO.matcher(Deploy.listUsers().data);
        Set<String> users = new LinkedHashSet<>();
        while (m.find()) {
            users.add(m.group(1));
        }
        if (users.isEmpty()) {
            users.add("0");
        }
        for (String u : users) {
            for (String line : Deploy.listPackages(u).data.split("\n")) {
                String p = line.trim();
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    static void hosts(Activity a, Pickers.Pick<List<Host>> cb) {
        new Thread(() -> {
            Set<String> have = installed(a);
            List<Host> out = new ArrayList<>();
            for (String[] row : rows(a)) {
                if (have.contains(row[0])) {
                    String name = Desync.label(a, row[0]);
                    out.add(new Host(row[0], name));
                }
            }
            Util.ui(a, () -> cb.on(out));
        }).start();
    }

    static void pick(Activity a, String user, String host, Pickers.Pick<Choice> cb) {
        new Thread(() -> {
            List<Pickers.Item> items = new ArrayList<>();
            String tpl = expandForHost(a, host, user);
            if (!tpl.isEmpty()) {
                String[] ps = split(tpl);
                Set<String> seen = new LinkedHashSet<>();
                for (String line : Deploy.containerScan(ps[0], ps[1]).data.split("\n")) {
                    String pkg = line.trim();
                    if (pkg.isEmpty() || !Model.PKG.matcher(pkg).matches()) {
                        continue;
                    }
                    String key = host + SEP + pkg + SEP + tpl;
                    if (!seen.add(key)) {
                        continue;
                    }
                    String label = Desync.label(a, pkg);
                    items.add(new Pickers.Item(
                            label.equals(pkg) ? pkg : label + " · " + pkg, key));
                }
            }
            Util.ui(a, () -> Pickers.showItems(a, a.getString(R.string.msg_pick_app),
                    items, v -> {
                        String[] f = v.split(SEP, -1);
                        if (f.length == 3) {
                            cb.on(new Choice(f[0], f[1], f[2]));
                        }
                    }));
        }).start();
    }
}
