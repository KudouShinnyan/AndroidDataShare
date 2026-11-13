package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class VersionEvent {

    static final int MOUNT = 1;
    static final int HARDLINK = 2;

    private static final String FILE = "version_event.json";
    private static final String CHANNEL = "ads_version";

    private VersionEvent() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    static void record(Context c, int changes) {
        if (changes == 0) {
            return;
        }
        try {
            int all = peek(c) | changes;
            Files.write(file(c).toPath(),
                    String.valueOf(all).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static int peek(Context c) {
        try {
            File f = file(c);
            if (f.exists()) {
                return Integer.parseInt(
                        new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    static int consume(Context c) {
        int v = peek(c);
        try {
            Files.deleteIfExists(file(c).toPath());
        } catch (Exception ignored) {
        }
        return v;
    }

    static void notifyChange(Context c, int changes) {
        if (changes == 0) {
            return;
        }
        record(c, changes);
        boolean mount = (changes & MOUNT) != 0;
        boolean hard = (changes & HARDLINK) != 0;
        int title;
        int body;
        if (mount && hard) {
            title = R.string.notify_both_title;
            body = R.string.notify_both_body;
        } else if (mount) {
            title = R.string.notify_mount_title;
            body = R.string.notify_mount_body;
        } else {
            title = R.string.notify_hard_title;
            body = R.string.notify_hard_body;
        }
        try {
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                            c.getString(R.string.notify_channel_name),
                            NotificationManager.IMPORTANCE_DEFAULT));
                }
                Intent i = new Intent(c, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("ads_version_event", "1");
                PendingIntent pi = PendingIntent.getActivity(c, 1, i,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                Notification n = new Notification.Builder(c, CHANNEL)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(c.getString(title))
                        .setContentText(c.getString(body))
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build();
                nm.notify(1, n);
            }
        } catch (Exception ignored) {
        }
        if (mount) {
            try {
                Intent i = new Intent(c, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("ads_version_event", "1");
                c.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }

    static void show(Activity a, int changes) {
        if (changes == 0) {
            return;
        }
        boolean mount = (changes & MOUNT) != 0;
        int body = mount
                ? R.string.version_event_mount_body : R.string.version_event_hardlink_body;
        new AlertDialog.Builder(a)
                .setTitle(R.string.version_event_title)
                .setMessage(a.getString(body))
                .setPositiveButton(R.string.version_event_open_status,
                        (d, w) -> a.startActivity(new Intent(a, MainActivity.class)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
