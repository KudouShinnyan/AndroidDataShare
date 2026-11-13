package io.github.kudoushinnyan.datashare;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class WatchService extends Service {

    private static final String CHANNEL = "ads_watch";
    private static final long INTERVAL_MS = 60000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            check();
            h.postDelayed(this, INTERVAL_MS);
        }
    };
    private BroadcastReceiver rx;
    private boolean registered = false;

    @Override
    public void onCreate() {
        super.onCreate();
        foregroundNotice();
        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                check();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        try {
            registerReceiver(rx, f);
            registered = true;
        } catch (Exception ignored) {
        }
        h.postDelayed(tick, INTERVAL_MS);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        check();
        return START_STICKY;
    }

    private void check() {
        final Context c = this;
        new Thread(() -> {
            int changes = Versions.autoCheck(c);
            if (changes != 0) {
                VersionEvent.notifyChange(c, changes);
            }
        }).start();
    }

    private void foregroundNotice() {
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                        getString(R.string.notify_channel_name),
                        NotificationManager.IMPORTANCE_LOW));
            }
            Notification n = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.notify_foreground_title))
                    .setContentText(getString(R.string.notify_foreground_body))
                    .build();
            startForeground(2, n);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        h.removeCallbacks(tick);
        if (registered) {
            try {
                unregisterReceiver(rx);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
