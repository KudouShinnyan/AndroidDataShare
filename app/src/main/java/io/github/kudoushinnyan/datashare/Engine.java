package io.github.kudoushinnyan.datashare;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

final class Engine {

    private static String cached;

    private Engine() {
    }

    static String assetText(Context c, String name) {
        try {
            InputStream in = c.getAssets().open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException(name + " asset unavailable", e);
        }
    }

    static String asset(Context c) {
        if (cached == null) {
            cached = assetText(c, "engine.sh");
        }
        return cached;
    }

    static String script(Context c, String command) {
        return asset(c) + "\nads_choose_base\n" + command + "\n";
    }

    static String heredoc(String marker, String data) {
        return "<<'" + marker + "'\n" + data + "\n" + marker;
    }
}
