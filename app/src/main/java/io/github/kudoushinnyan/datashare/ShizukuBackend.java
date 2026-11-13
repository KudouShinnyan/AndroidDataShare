package io.github.kudoushinnyan.datashare;

import android.content.Context;

import java.io.OutputStream;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

final class ShizukuBackend implements Backend {

    private static final Method NEW_PROCESS = newProcessMethod();
    private final Context ctx;
    private final boolean rootIdentity;

    ShizukuBackend(Context ctx, boolean rootIdentity) {
        this.ctx = ctx;
        this.rootIdentity = rootIdentity;
    }

    private static Method newProcessMethod() {
        try {
            Method m = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String id() {
        return rootIdentity ? "sui" : "shizuku";
    }

    @Override
    public Result ping() {
        Result r = run("id -u\n", null);
        if (!r.ok) {
            return Result.fail("E_BACKEND", r.message);
        }
        String uid = r.data.trim();
        boolean root = "0".equals(uid);
        return new Result(true, "OK", "", "uid=" + uid + " identity=" + (root ? "sui" : "shizuku"));
    }

    @Override
    public Result exec(String script) {
        return run(Engine.script(ctx, script), null);
    }

    @Override
    public Result execLive(String script, StepSink sink) {
        return run(Engine.script(ctx, script), sink);
    }

    private Result run(String script, StepSink sink) {
        try {
            if (!Shizuku.pingBinder()) {
                return Result.fail("E_NO_SHIZUKU", "Shizuku binder not ready");
            }
            if (NEW_PROCESS == null) {
                return Result.fail("E_NO_SHIZUKU", "Shizuku newProcess unavailable");
            }
            Process p = (Process) NEW_PROCESS.invoke(null,
                    new Object[]{new String[]{"sh", "-s"}, null, null});
            OutputStream os = p.getOutputStream();
            os.write(script.getBytes("UTF-8"));
            os.flush();
            os.close();
            return Shell.run(p, sink, "E_SHELL", "shell failed");
        } catch (Exception e) {
            return Result.fail("E_BACKEND", e.toString());
        }
    }
}
