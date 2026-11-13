package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

final class KeepAlive {

    private static final String PREF = "ads_keepalive";
    private static final String KEY = "enabled";
    private static final String PKG = "io.github.kudoushinnyan.datashare";

    private KeepAlive() {
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    static void setEnabled(Context c, boolean v) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, v).apply();
    }

    static Backend.Result apply(Context c, boolean on) {
        String sign = on ? "+" : "-";
        String appops = on ? "allow" : "default";
        String sh = "dumpsys deviceidle whitelist " + sign + PKG + " 2>/dev/null || true\n"
                + "cmd appops set " + PKG + " RUN_ANY_IN_BACKGROUND "
                + appops + " 2>/dev/null || true\n";
        if (on) {
            sh += "am set-standby-bucket " + PKG + " active 2>/dev/null || true\n";
        }
        sh += "echo ADS_KEEPALIVE_OK\n";
        try {
            return Backends.current().exec(sh);
        } catch (Exception e) {
            return Backend.Result.fail("E_IO", e.toString());
        }
    }

    static void startWatch(Context c) {
        if (!enabled(c) || !Session.hasDetectableGroups()) {
            return;
        }
        try {
            Intent i = new Intent(c, WatchService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Exception ignored) {
        }
    }

    static void stopWatch(Context c) {
        try {
            c.stopService(new Intent(c, WatchService.class));
        } catch (Exception ignored) {
        }
    }

    static void vendorSettings(Activity a) {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + a.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
