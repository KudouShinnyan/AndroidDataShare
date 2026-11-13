package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

import rikka.shizuku.Shizuku;

final class Perm {

    static final String SHIZUKU = "shizuku";
    static final String ROOT = "root";

    private static final String PREF = "ads_perm";
    private static final String KEY_SHIZUKU = "shizuku_granted";
    private static final String KEY_ROOT = "root_granted";
    private static final String KEY_SELECTED = "selected";

    private static final Map<String, Boolean> grants = new HashMap<>();
    private static String selected = SHIZUKU;
    private static boolean shizukuRoot = false;
    private static int requestCode = 4100;

    private Perm() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static void load(Context c) {
        SharedPreferences p = prefs(c);
        grants.put(SHIZUKU, p.getBoolean(KEY_SHIZUKU, false));
        grants.put(ROOT, p.getBoolean(KEY_ROOT, false));
        String s = p.getString(KEY_SELECTED, SHIZUKU);
        selected = ROOT.equals(s) ? ROOT : SHIZUKU;
    }

    private static void save(Context c) {
        prefs(c).edit()
                .putBoolean(KEY_SHIZUKU, granted(SHIZUKU))
                .putBoolean(KEY_ROOT, granted(ROOT))
                .putString(KEY_SELECTED, selected)
                .apply();
    }

    static String selected() {
        return selected;
    }

    static void select(Context c, String value) {
        if (SHIZUKU.equals(value) || ROOT.equals(value)) {
            selected = value;
            save(c);
        }
    }

    static boolean granted(String value) {
        Boolean b = grants.get(value);
        return b != null && b;
    }

    static boolean unlocked() {
        return !"none".equals(Backends.current().id());
    }

    static void setGranted(Context c, String value, boolean ok) {
        grants.put(value, ok);
        save(c);
    }

    static boolean shizukuCanMount() {
        return shizukuRoot;
    }

    static void probe(Context c) {
        load(c);
        if (granted(SHIZUKU) && !probeShizuku(c)) {
            setGranted(c, SHIZUKU, false);
        }
        if (granted(ROOT) && !new RootBackend(c).ping().ok) {
            setGranted(c, ROOT, false);
        }
        save(c);
    }

    private static boolean probeShizuku(Context c) {
        shizukuRoot = false;
        boolean ok;
        try {
            ok = Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            ok = new ShizukuBackend(c, false).ping().ok;
        }
        if (ok) {
            Backend.Result p = new ShizukuBackend(c, false).ping();
            shizukuRoot = p.ok && p.data.contains("identity=sui");
            if (!p.ok) {
                ok = false;
            }
        }
        return ok;
    }

    static void refreshIdentity(Context c) {
        probeShizuku(c);
    }

    static void request(Activity a, String which, Runnable done) {
        if (ROOT.equals(which)) {
            new Thread(() -> {
                boolean ok = new RootBackend(a).ping().ok;
                setGranted(a, ROOT, ok);
                Util.ui(a, done);
            }).start();
            return;
        }
        try {
            if (!Shizuku.pingBinder()) {
                waitForBinder(a, done);
                return;
            }
        } catch (Exception ignored) {
        }
        requestShizuku(a, done);
    }

    private static void waitForBinder(Activity a, Runnable done) {
        try {
            final boolean[] fired = {false};
            final Shizuku.OnBinderReceivedListener[] holder =
                    new Shizuku.OnBinderReceivedListener[1];
            Shizuku.OnBinderReceivedListener listener = () -> {
                if (fired[0]) {
                    return;
                }
                fired[0] = true;
                Shizuku.removeBinderReceivedListener(holder[0]);
                requestShizuku(a, done);
            };
            holder[0] = listener;
            Shizuku.addBinderReceivedListenerSticky(listener);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (fired[0]) {
                    return;
                }
                fired[0] = true;
                Shizuku.removeBinderReceivedListener(holder[0]);
                setGranted(a, SHIZUKU, false);
                Util.ui(a, () -> {
                    Util.toast(a, a.getString(R.string.perm_shizuku_not_running));
                    done.run();
                });
            }, 5000);
        } catch (Exception e) {
            setGranted(a, SHIZUKU, false);
            Util.ui(a, done);
        }
    }

    private static void requestShizuku(Activity a, Runnable done) {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setGranted(a, SHIZUKU, true);
                Util.ui(a, done);
                return;
            }
            final int code = ++requestCode;
            final boolean[] once = {false};
            final Shizuku.OnRequestPermissionResultListener[] holder =
                    new Shizuku.OnRequestPermissionResultListener[1];
            Shizuku.OnRequestPermissionResultListener listener = (rc, grantResult) -> {
                if (rc != code || once[0]) {
                    return;
                }
                once[0] = true;
                Shizuku.removeRequestPermissionResultListener(holder[0]);
                setGranted(a, SHIZUKU, grantResult == PackageManager.PERMISSION_GRANTED);
                Util.ui(a, done);
            };
            holder[0] = listener;
            Shizuku.addRequestPermissionResultListener(listener);
            Shizuku.requestPermission(code);
        } catch (Exception e) {
            setGranted(a, SHIZUKU, false);
            Util.ui(a, done);
        }
    }
}
