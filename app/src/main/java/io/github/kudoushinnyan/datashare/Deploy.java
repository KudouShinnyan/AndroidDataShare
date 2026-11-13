package io.github.kudoushinnyan.datashare;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Deploy {

    private Deploy() {
    }

    private static String filteredRules(String rules, Set<String> gids) {
        if (rules == null || rules.isEmpty() || gids == null || gids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : rules.split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length > 2 && gids.contains(f[1])) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static final Pattern TPL_USER =
            Pattern.compile("/data/(?:media|user)/(\\d+)/");

    private static String targetUser(String[] f) {
        if ("R".equals(f[0])) {
            return f[7].isEmpty() ? "0" : f[7];
        }
        Matcher m = TPL_USER.matcher(f[12]);
        return m.find() ? m.group(1) : "0";
    }

    private static String forceStopLines(String rules) {
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : rules.split("\n")) {
            String[] f = line.split("\\|", -1);
            List<String> pkgs = new ArrayList<>();
            String user = "0";
            if (f.length > 8 && "R".equals(f[0])) {
                user = targetUser(f);
                pkgs.add(f[8]);
            } else if (f.length > 12 && "C".equals(f[0])) {
                user = targetUser(f);
                pkgs.add(f[7]);
                pkgs.add(f[8]);
            }
            for (String pkg : pkgs) {
                if (pkg.isEmpty() || !seen.add(user + "/" + pkg)) {
                    continue;
                }
                String cmd = "am force-stop --user " + user + " '" + pkg + "'";
                sb.append("ads_step stop \"").append(cmd).append("\"\n");
                sb.append("if command -v timeout >/dev/null 2>&1; then\n")
                        .append("  timeout 10 ").append(cmd).append(" 2>/dev/null || true\n")
                        .append("else\n")
                        .append("  ").append(cmd).append(" 2>/dev/null || true\n")
                        .append("fi\n");
            }
        }
        return sb.toString();
    }

    private static String moduleWrite(Model.State s, String rules) {
        return "engine_publish \"$ADS_MODULE_STATE\" "
                + Engine.heredoc("ADS_STATE", Model.toStateJson(s).trim()) + "\n"
                + "engine_publish \"$ADS_MODULE_RULES\" "
                + Engine.heredoc("ADS_MOD_RULES", rules.trim()) + "\n";
    }

    static Backend.Result snapshot() {
        if (!Backends.moduleInstalled()) {
            return Backend.Result.ok("");
        }
        Backend.Result r = Backends.current().exec("engine_read \"$ADS_MODULE_STATE\"\n");
        return new Backend.Result(r.ok, r.code, r.message, r.data.trim());
    }

    static Backend.Result apply(Model.State s, Set<String> releaseGids,
                                String clearRules, Backend.StepSink sink) {
        String rules = Model.toRules(s, true);
        String old = filteredRules(Session.lastRules(), releaseGids);
        String applyRules = filteredRules(rules, releaseGids);
        String clear = clearRules == null ? "" : clearRules.trim();
        StringBuilder sh = new StringBuilder();
        sh.append(sink == null ? "ADS_PROGRESS=0\n" : "ADS_PROGRESS=1\n");
        sh.append("ads_step phase \"force-stop targets\"\n");
        sh.append(forceStopLines(applyRules));
        if (!old.isEmpty()) {
            sh.append("ads_step phase \"release old rules\"\n");
            sh.append("engine_release_rules ").append(Engine.heredoc("ADS_OLD", old.trim())).append('\n');
        }
        if (!clear.isEmpty()) {
            sh.append("ads_step phase \"clear target directories\"\n");
            sh.append("engine_clear_targets ")
                    .append(Engine.heredoc("ADS_CLEAR", clear.trim())).append('\n');
        }
        sh.append("ads_step phase \"apply rules\"\n");
        sh.append("engine_apply_all ")
                .append(Engine.heredoc("ADS_RULES", applyRules.trim())).append('\n');
        sh.append("if [ -f \"$ADS_MODULE/module.prop\" ]; then\n");
        if (s.groups.isEmpty()) {
            sh.append("engine_module_config_remove\n");
        } else {
            sh.append(moduleWrite(s, rules));
        }
        sh.append("fi\n");
        sh.append("ads_step phase \"sync module config\"\n");
        sh.append("ads_step done \"all steps finished\"\n");
        Backend.Result r;
        try {
            r = sink == null ? Backends.current().exec(sh.toString())
                    : Backends.current().execLive(sh.toString(), sink);
        } catch (Exception e) {
            return Backend.Result.fail("E_IO", e.toString());
        }
        if (r.ok) {
            for (String line : r.data.split("\n")) {
                if (line.startsWith("ADS_RULE_FAIL|")) {
                    return new Backend.Result(true, "PARTIAL",
                            line.substring("ADS_RULE_FAIL|".length()).trim(), r.data);
                }
            }
        }
        return r;
    }

    static Backend.Result syncState(Model.State s) {
        if (!Backends.moduleInstalled()) {
            return Backend.Result.ok("");
        }
        if (s.groups.isEmpty()) {
            return Backends.current().exec("engine_module_config_remove\n");
        }
        return Backends.current().exec(moduleWrite(s, Model.toRules(s, true)));
    }

    static Backend.Result action(String op, String gid, String rules, Backend.StepSink sink) {
        String sh = (sink == null ? "ADS_PROGRESS=0\n" : "ADS_PROGRESS=1\n")
                + "ads_step phase \"force-stop targets\"\n"
                + forceStopLines(rules)
                + "ads_step phase \"run " + op + "\"\n"
                + "engine_action " + op + " " + gid + " "
                + Engine.heredoc("ADS_RULES", rules.trim()) + "\n"
                + "ads_step done \"action finished\"\n";
        if (sink == null) {
            return Backends.current().exec(sh);
        }
        return Backends.current().execLive(sh, sink);
    }

    static Backend.Result versions(String user, String pkg, String sentinel) {
        String sent = sentinel == null || sentinel.isEmpty() ? "-" : sentinel;
        return Backends.current().exec("engine_versions " + user + " " + pkg + " " + sent + "\n");
    }

    static Backend.Result scanItems(String user, String pkg) {
        return Backends.current().exec("engine_scan_items " + user + " " + pkg + "\n");
    }

    static Backend.Result linkstateRead() {
        return Backends.current().exec("engine_read \"$ADS_LINK_FILE\"\n");
    }

    static Backend.Result linkstateWrite(String json) {
        return Backends.current().exec(
                "engine_publish \"$ADS_LINK_FILE\" " + Engine.heredoc("ADS_LS", json) + "\n");
    }

    static Backend.Result linkstateRemove() {
        return Backends.current().exec("engine_linkstate_remove\n");
    }

    static Backend.Result listUsers() {
        return Backends.current().exec("engine_list_users\n");
    }

    static Backend.Result listPackages(String user) {
        return Backends.current().exec("engine_list_packages " + user + "\n");
    }

    static Backend.Result containerScan(String prefix, String rest) {
        return Backends.current().exec(
                "engine_container_scan '" + prefix + "' '" + rest + "'\n");
    }
}
