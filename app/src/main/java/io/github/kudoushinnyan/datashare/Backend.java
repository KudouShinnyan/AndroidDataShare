package io.github.kudoushinnyan.datashare;

interface Backend {

    final class Result {
        final boolean ok;
        final String code;
        final String message;
        final String data;

        Result(boolean ok, String code, String message, String data) {
            this.ok = ok;
            this.code = code == null ? "" : code;
            this.message = message == null ? "" : message;
            this.data = data == null ? "" : data;
        }

        static Result fail(String code, String message) {
            return new Result(false, code, message, "");
        }

        static Result ok(String data) {
            return new Result(true, "OK", "", data);
        }
    }

    String id();

    Result ping();

    Result exec(String script);

    default Result execRaw(String shell) {
        return exec(shell);
    }

    interface StepSink {
        void onStep(String line);
    }

    default Result execLive(String script, StepSink sink) {
        return exec(script);
    }
}
