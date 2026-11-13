package io.github.kudoushinnyan.datashare;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

final class Shell {

    private Shell() {
    }

    static Backend.Result run(Process p, Backend.StepSink sink,
                              String failTag, String failMessage) throws Exception {
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread t = new Thread(() -> {
            try {
                InputStream es = p.getErrorStream();
                byte[] b = new byte[1024];
                int n;
                while ((n = es.read(b)) > 0) {
                    err.write(b, 0, n);
                }
            } catch (Exception ignored) {
            }
        });
        t.start();
        StringBuilder out = new StringBuilder();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("ADS_STEP|")) {
                if (sink != null) {
                    String[] f = line.split("\\|", 3);
                    if (f.length == 3) {
                        sink.onStep(f[2]);
                    }
                }
            } else {
                out.append(line).append('\n');
            }
        }
        int rc = p.waitFor();
        t.join(1000);
        String so = out.toString();
        String se = err.toString("UTF-8");
        if (rc != 0 && so.isEmpty()) {
            return Backend.Result.fail(failTag,
                    se.trim().isEmpty() ? failMessage : se.trim());
        }
        return new Backend.Result(true, "OK", se, so);
    }
}
