package io.github.kudoushinnyan.datashare;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Config {

    private static final Map<String, String> EXTRA = new LinkedHashMap<>();
    private static final String CONTAINERS = "containers.txt";

    private Config() {
    }

    static void clearExtra() {
        synchronized (EXTRA) {
            EXTRA.clear();
        }
    }

    static void setExtra(String asset, String text) {
        synchronized (EXTRA) {
            EXTRA.put(asset, text == null ? "" : text);
        }
    }

    private static String extra(String asset) {
        synchronized (EXTRA) {
            String s = EXTRA.get(asset);
            return s == null ? "" : s;
        }
    }

    private static List<String[]> parse(String text) {
        List<String[]> out = new ArrayList<>();
        for (String raw : text.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("[\\s,;]+");
            if (parts.length > 0) {
                out.add(parts);
            }
        }
        return out;
    }

    private static List<String[]> assetRows(Context c, String asset) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = c.getAssets().open(asset);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return parse(sb.toString());
    }

    private static final class Builtin {
        final Set<String> groups = new LinkedHashSet<>();
        final Set<String> containers = new LinkedHashSet<>();
    }

    private static Builtin builtin(Context c) {
        Builtin b = new Builtin();
        for (String asset : new String[]{"sync_groups.txt", "desync_groups.txt"}) {
            for (String[] row : assetRows(c, asset)) {
                for (String p : row) {
                    b.groups.add(p);
                }
            }
        }
        for (String[] row : assetRows(c, CONTAINERS)) {
            b.containers.add(String.join(" ", row));
        }
        return b;
    }

    static List<String[]> rows(Context c, String asset) {
        List<String[]> out = assetRows(c, asset);
        String x = extra(asset);
        if (x.isEmpty()) {
            return out;
        }
        Builtin b = builtin(c);
        for (String[] row : parse(x)) {
            if (CONTAINERS.equals(asset)) {
                if (!b.containers.contains(String.join(" ", row))) {
                    out.add(row);
                }
                continue;
            }
            List<String> kept = new ArrayList<>();
            for (String p : row) {
                if (!b.groups.contains(p) && !kept.contains(p)) {
                    kept.add(p);
                }
            }
            if (!kept.isEmpty()) {
                out.add(kept.toArray(new String[0]));
            }
        }
        return out;
    }
}
