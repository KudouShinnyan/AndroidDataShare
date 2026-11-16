package io.github.kudoushinnyan.datashare;

import android.content.Context;

final class Actions {

    private Actions() {
    }

    static Backend.Result submitLive(Context c, String clearRules, Backend.StepSink sink) {
        Model.State s = Session.get();
        Session.bump();
        Backend.Result r = Deploy.apply(s, Session.modeChangedGids(), clearRules, sink);
        hints(c, r, sink);
        if (r.ok) {
            for (Model.Group g : s.groups) {
                if (g.enabled && !Model.MODE_COPY.equals(g.mode) && g.srcVersion.isEmpty()) {
                    Versions.refreshBaselines(c, g);
                }
            }
            Session.setLastRules(Model.toRules(s, true));
            Session.clearChangedGids();
            Session.save(c);
            writeLinkState(c);
        }
        return r;
    }

    static Backend.Result op(Context c, String gid, String op, Backend.StepSink sink) {
        String rules = Model.rulesForGroup(Session.get(), gid);
        Backend.Result r = Deploy.action(op, gid, rules, sink);
        hints(c, r, sink);
        if (!r.ok || "CANCELLED".equals(r.code)) {
            return r;
        }
        Model.Group g = Model.find(Session.get(), gid);
        if (g == null) {
            return r;
        }
        String status = null;
        boolean refresh = false;
        switch (op) {
            case "terminate":
                status = Model.STATUS_TERMINATED;
                break;
            case "pause":
                status = Model.STATUS_PAUSED;
                break;
            case "restore":
            case "resume":
                status = Model.STATUS_ACTIVE;
                break;
            case "rebuild":
                refresh = true;
                break;
            default:
                return r;
        }
        if ("restore".equals(op)) {
            refresh = true;
        }
        if (status != null) {
            g.status = status;
        }
        if (refresh) {
            Versions.refreshBaselines(c, g);
        }
        Session.save(c);
        submitQuiet(c);
        return r;
    }

    static void markApplied(Context c, String gid, Backend.Result r, boolean cleared) {
        Model.Group g = Model.find(Session.get(), gid);
        if (g == null) {
            return;
        }
        g.applied = r.ok && !cleared && !"CANCELLED".equals(r.code) && !"PARTIAL".equals(r.code)
                && !r.data.contains("ADS_LINK_UNSUPPORTED");
        Session.save(c);
    }

    private static void hints(Context c, Backend.Result r, Backend.StepSink sink) {
        if (sink == null) {
            return;
        }
        if (r.data.contains("ADS_LINK_UNSUPPORTED")) {
            sink.onStep(c.getString(R.string.hardlink_unsupported_hint));
        }
    }

    private static void submitQuiet(Context c) {
        Model.State s = Session.get();
        Session.bump();
        Deploy.syncState(s);
        Session.setLastRules(Model.toRules(s, true));
        Session.clearChangedGids();
        Session.save(c);
        writeLinkState(c);
    }

    static void writeLinkState(Context c) {
        Model.State hard = new Model.State();
        hard.instanceId = Session.get().instanceId;
        hard.revision = Session.get().revision;
        for (Model.Group g : Session.get().groups) {
            if (Model.MODE_HARDLINK.equals(g.mode)) {
                hard.groups.add(g);
            }
        }
        if (hard.groups.isEmpty()) {
            Deploy.linkstateRemove();
            return;
        }
        String head = "{\"schema\":1,\"instance_id\":\"" + hard.instanceId
                + "\",\"sig\":\"" + Util.signerDigest(c) + "\",\"revoked\":0}";
        Deploy.linkstateWrite(head + "\n" + Model.toStateJson(hard));
    }
}
