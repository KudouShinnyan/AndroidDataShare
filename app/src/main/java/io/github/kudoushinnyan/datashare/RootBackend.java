package io.github.kudoushinnyan.datashare;

import android.content.Context;

import java.io.OutputStream;

final class RootBackend implements Backend {

    private final Context ctx;

    RootBackend(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public String id() {
        return "root";
    }

    @Override
    public Result ping() {
        return run("echo root-ok\n", null);
    }

    @Override
    public Result exec(String script) {
        return run(Engine.script(ctx, script), null);
    }

    @Override
    public Result execRaw(String shell) {
        return run(shell, null);
    }

    @Override
    public Result execLive(String script, StepSink sink) {
        return run(Engine.script(ctx, script), sink);
    }

    private Result run(String script, StepSink sink) {
        Process p = null;
        try {
            p = new ProcessBuilder("su").start();
            OutputStream os = p.getOutputStream();
            os.write("sh -s\n".getBytes("UTF-8"));
            os.write(script.getBytes("UTF-8"));
            os.write("exit\n".getBytes("UTF-8"));
            os.flush();
            os.close();
            return Shell.run(p, sink, "E_SU", "su failed");
        } catch (Exception e) {
            return Result.fail("E_SU", e.toString());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
}
