package io.github.kudoushinnyan.datashare;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.security.MessageDigest;

final class Util {

    private Util() {
    }

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    static void ui(Activity a, Runnable r) {
        a.runOnUiThread(r);
    }

    static void keepAwake(Activity a, boolean on) {
        if (on) {
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    static void fitSystemBars(View root, final LinearLayout content, final int pad) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            content.setPadding(pad, pad + top, pad, pad + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    static String signerDigest(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo pi = pm.getPackageInfo(c.getPackageName(), flags);
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28 && pi.signingInfo != null) {
                sigs = pi.signingInfo.getApkContentsSigners();
            } else {
                sigs = pi.signatures;
            }
            if (sigs == null || sigs.length == 0) {
                return "";
            }
            return hex(MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray()));
        } catch (Exception e) {
            return "";
        }
    }

    static LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
