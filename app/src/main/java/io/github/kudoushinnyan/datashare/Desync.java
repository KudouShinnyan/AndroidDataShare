package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Desync {

    private Desync() {
    }

    private static String[] match(Context c, String asset, Model.Group g) {
        Set<String> selected = new LinkedHashSet<>();
        if (!g.primary.pkg.isEmpty()) {
            selected.add(g.primary.pkg);
        }
        for (Model.Entry e : g.targets) {
            if (!e.isContainer() && !e.pkg.isEmpty()) {
                selected.add(e.pkg);
            }
        }
        if (selected.size() < 2) {
            return null;
        }
        for (String[] parts : Config.rows(c, asset)) {
            List<String> found = new ArrayList<>();
            for (String p : parts) {
                if (selected.contains(p) && !found.contains(p)) {
                    found.add(p);
                }
            }
            if (found.size() >= 2) {
                return new String[]{found.get(0), found.get(1)};
            }
        }
        return null;
    }

    static String[] hit(Context c, Model.Group g) {
        return match(c, "desync_groups.txt", g);
    }

    static boolean whitelisted(Context c, Model.Group g) {
        return match(c, "sync_groups.txt", g) != null;
    }

    static List<String> peers(Context c, String primary) {
        List<String> out = new ArrayList<>();
        if (primary == null || primary.isEmpty()) {
            return out;
        }
        for (String[] parts : Config.rows(c, "sync_groups.txt")) {
            boolean hit = false;
            List<String> row = new ArrayList<>();
            for (String p : parts) {
                if (p.isEmpty()) {
                    continue;
                }
                if (p.equals(primary)) {
                    hit = true;
                } else if (!row.contains(p)) {
                    row.add(p);
                }
            }
            if (hit) {
                out.addAll(row);
                break;
            }
        }
        return out;
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence s = pm.getApplicationLabel(ai);
            if (s != null && !s.toString().trim().isEmpty()) {
                return s.toString();
            }
        } catch (Exception ignored) {
        }
        return pkg;
    }

    private static boolean isCrossUser(Model.Group g) {
        String u = g.primary == null ? "0" : g.primary.user;
        for (Model.Entry t : g.targets) {
            if (!t.isContainer() && !u.equals(t.user)) {
                return true;
            }
        }
        return false;
    }

    static void showShellRisk(Activity a, Runnable accept) {
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(R.string.warn_shell_title)
                .setMessage(R.string.warn_shell_body)
                .setPositiveButton(accept == null ? R.string.progress_ok
                                : R.string.warn_general_accept,
                        (d, w) -> {
                            if (accept != null) {
                                accept.run();
                            }
                        });
        if (accept != null) {
            b.setNegativeButton(R.string.warn_cancel, null);
        }
        b.show();
    }

    static void showMountRisk(Activity a, Model.Group g, Runnable accept) {
        String body = a.getString(R.string.warn_mount_short_body);
        if (isCrossUser(g)) {
            body += "\n\n" + a.getString(R.string.warn_mount_risk_cross_user);
        }
        new AlertDialog.Builder(a)
                .setTitle(R.string.warn_mount_short_title)
                .setMessage(body)
                .setPositiveButton(R.string.warn_general_accept, (d, w) -> accept.run())
                .setNegativeButton(R.string.warn_cancel, null)
                .show();
    }

    static String modeLabel(Context c, String mode, boolean recommended) {
        if (Model.MODE_HARDLINK.equals(mode)) {
            return c.getString(recommended
                    ? R.string.mode_hardlink_rec : R.string.mode_hardlink);
        }
        if (Model.MODE_COPY.equals(mode)) {
            return c.getString(R.string.mode_copy);
        }
        return c.getString(R.string.mode_mount);
    }

    static void showCrossUserRisk(Activity a) {
        Warn.info(a, a.getString(R.string.warn_cross_user_title),
                a.getString(R.string.warn_mount_risk_cross_user));
    }

    static void showSaveNotice(Activity a, Model.Group g, boolean clear, Runnable commit) {
        String[] hit = hit(a, g);
        boolean crossPkg = false;
        for (Model.Entry t : g.targets) {
            if (!t.isContainer() && !t.pkg.equals(g.primary.pkg)) {
                crossPkg = true;
            }
        }
        boolean general = crossPkg && !g.hintsDismissed && !whitelisted(a, g);
        int title;
        int accept;
        String body;
        if (hit != null) {
            body = a.getString(R.string.warn_desync_body,
                    label(a, hit[0]), label(a, hit[1]));
            title = R.string.warn_desync_title;
            accept = R.string.warn_desync_accept;
        } else {
            body = clear ? a.getString(R.string.save_clear_line, modeLabel(a, g.mode, false))
                    : a.getString(R.string.save_edit_line);
            if (general) {
                body += "\n\n" + a.getString(R.string.warn_general_body);
                title = R.string.warn_general_title;
                accept = R.string.warn_general_accept;
            } else {
                title = R.string.save_confirm_title;
                accept = R.string.confirm_accept;
            }
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(accept, (d, w) -> commit.run())
                .setNegativeButton(R.string.warn_cancel, null);
        if (general && hit == null) {
            b.setNeutralButton(R.string.warn_general_dismiss, (d, w) -> {
                g.hintsDismissed = true;
                commit.run();
            });
        }
        b.show();
    }
}
