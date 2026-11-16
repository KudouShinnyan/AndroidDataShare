package io.github.kudoushinnyan.datashare;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

final class Model {

    static final String WHOLE = "-";
    static final String MODE_MOUNT = "mount";
    static final String MODE_HARDLINK = "hardlink";
    static final String MODE_COPY = "copy";
    static final String STATUS_ACTIVE = "active";
    static final String STATUS_PAUSED = "paused";
    static final String STATUS_TERMINATED = "terminated";
    static final String KIND_NATIVE = "native";
    static final String KIND_CONTAINER = "container";

    static final Pattern USER = Pattern.compile("\\d+");
    static final Pattern PKG = Pattern.compile("[A-Za-z0-9_.]+");
    static final Pattern REL = Pattern.compile("[A-Za-z0-9._/-]+");
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private Model() {
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    static final class Entry {
        String kind = KIND_NATIVE;
        String user = "0";
        String pkg = "";
        String host = "";
        String container = "";
        String inner = "";
        String tpl = "";

        boolean isContainer() {
            return KIND_CONTAINER.equals(kind);
        }

        String targetKey() {
            if (isContainer()) {
                return "C|" + host + "|" + container + "|" + inner;
            }
            return "R|" + user + "|" + pkg;
        }

    }

    static final class Group {
        String id = newId();
        boolean enabled = true;
        String name = "group-" + id.substring(0, 6);
        String mode = MODE_HARDLINK;
        String status = STATUS_ACTIVE;
        Entry primary = new Entry();
        final List<String> dirs = new ArrayList<>();
        final List<String> mountDirs = new ArrayList<>();
        final List<String> extraLinks = new ArrayList<>();
        String extraMode = MODE_HARDLINK;
        boolean hintsDismissed = false;
        final List<Entry> targets = new ArrayList<>();
        String srcVersion = "";
        final List<String> dstVersions = new ArrayList<>();
        String sentinel = "";
        int versionFlag = 0;
        boolean applied = true;

        boolean hasWhole() {
            return isMount() ? mountDirs.contains(WHOLE) : dirs.contains(WHOLE);
        }

        boolean isMount() {
            return MODE_MOUNT.equals(mode);
        }

    }

    static final class State {
        int revision = 0;
        String instanceId = newId();
        final List<Group> groups = new ArrayList<>();
    }

    static String toStateJson(State s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"revision\":").append(s.revision)
                .append(",\"instance_id\":\"").append(s.instanceId)
                .append("\",\"groups\":[\n");
        for (int i = 0; i < s.groups.size(); i++) {
            Group g = s.groups.get(i);
            sb.append("{\"id\":\"").append(g.id)
                    .append("\",\"enabled\":").append(g.enabled ? 1 : 0)
                    .append(",\"name\":\"").append(g.name)
                    .append("\",\"mode\":\"").append(g.mode)
                    .append("\",\"status\":\"").append(g.status)
                    .append("\",\"src_user\":\"").append(g.primary.user)
                    .append("\",\"src_pkg\":\"").append(g.primary.pkg)
                    .append("\",\"mount_dirs\":\"").append(String.join("|", g.mountDirs))
                    .append("\",\"extra_links\":\"").append(String.join("|", g.extraLinks))
                    .append("\",\"dirs\":\"").append(String.join("|", g.dirs))
                    .append("\",\"src_version\":\"").append(g.srcVersion)
                    .append("\",\"dst_versions\":\"").append(String.join(",", g.dstVersions))
                    .append("\",\"sentinel\":\"").append(g.sentinel)
                    .append("\",\"version_flag\":").append(g.versionFlag)
                    .append(",\"applied\":").append(g.applied ? 1 : 0)
                    .append(",\"extra_mode\":\"").append(g.extraMode)
                    .append("\",\"hints_dismissed\":").append(g.hintsDismissed ? 1 : 0)
                    .append(",\"targets\":[\n");
            for (int j = 0; j < g.targets.size(); j++) {
                Entry e = g.targets.get(j);
                sb.append("{\"kind\":\"").append(e.kind)
                        .append("\",\"user\":\"").append(e.user)
                        .append("\",\"pkg\":\"").append(e.pkg);
                if (e.isContainer()) {
                    sb.append("\",\"host\":\"").append(e.host)
                            .append("\",\"container\":\"").append(e.container)
                            .append("\",\"inner\":\"").append(e.inner)
                            .append("\",\"tpl\":\"").append(e.tpl);
                }
                sb.append("\"}");
                if (j + 1 < g.targets.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append("]}");
            if (i + 1 < s.groups.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("]}\n");
        return sb.toString();
    }

    static State parseState(String text) {
        State s = new State();
        try {
            JSONObject doc = new JSONObject(text);
            s.revision = doc.optInt("revision", 0);
            s.instanceId = doc.optString("instance_id", newId());
            JSONArray a = doc.optJSONArray("groups");
            if (a == null) {
                return s;
            }
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Group g = new Group();
                g.id = o.optString("id", newId());
                g.enabled = o.optInt("enabled", 1) != 0;
                g.name = o.optString("name", "group-" + g.id.substring(0, 6));
                g.mode = o.optString("mode", MODE_HARDLINK);
                g.status = o.optString("status", STATUS_ACTIVE);
                g.srcVersion = o.optString("src_version", "");
                g.sentinel = o.optString("sentinel", "");
                g.versionFlag = o.optInt("version_flag", 0);
                g.applied = o.optInt("applied", 1) != 0;
                g.extraMode = o.optString("extra_mode", MODE_HARDLINK);
                g.hintsDismissed = o.optInt("hints_dismissed", 0) != 0;
                g.primary.user = o.optString("src_user", "0");
                g.primary.pkg = o.optString("src_pkg", "");
                fillList(o.optString("mount_dirs", ""), g.mountDirs);
                fillList(o.optString("extra_links", ""), g.extraLinks);
                fillList(o.optString("dirs", ""), g.dirs);
                String dv = o.optString("dst_versions", "");
                if (!dv.isEmpty()) {
                    for (String v : dv.split(",")) {
                        g.dstVersions.add(v);
                    }
                }
                JSONArray ts = o.optJSONArray("targets");
                if (ts != null) {
                    for (int j = 0; j < ts.length(); j++) {
                        JSONObject e = ts.optJSONObject(j);
                        if (e == null) {
                            continue;
                        }
                        Entry en = new Entry();
                        en.kind = e.optString("kind", KIND_NATIVE);
                        en.user = e.optString("user", "0");
                        en.pkg = e.optString("pkg", "");
                        en.host = e.optString("host", "");
                        en.container = e.optString("container", "");
                        en.inner = e.optString("inner", "");
                        en.tpl = e.optString("tpl", "");
                        g.targets.add(en);
                    }
                }
                while (g.dstVersions.size() < g.targets.size()) {
                    g.dstVersions.add("");
                }
                s.groups.add(g);
            }
        } catch (Exception ignored) {
            return new State();
        }
        return s;
    }

    private static void fillList(String text, List<String> out) {
        if (text.isEmpty()) {
            return;
        }
        for (String d : text.split("\\|")) {
            if (!d.isEmpty()) {
                out.add(d);
            }
        }
    }

    static Group find(State s, String gid) {
        for (Group g : s.groups) {
            if (g.id.equals(gid)) {
                return g;
            }
        }
        return null;
    }

    static String toRules(State s, boolean activeOnly) {
        StringBuilder sb = new StringBuilder();
        for (Group g : s.groups) {
            if (activeOnly && !g.enabled) {
                continue;
            }
            String st = g.isMount() ? g.status : STATUS_ACTIVE;
            if (g.isMount()) {
                for (String rel : g.mountDirs) {
                    emit(sb, g, "mount", rel, st);
                }
                String extraAct = MODE_COPY.equals(g.extraMode) ? "copy" : "link";
                for (String rel : g.extraLinks) {
                    emit(sb, g, extraAct, rel, st);
                }
            } else {
                String act = MODE_HARDLINK.equals(g.mode) ? "link" : "copy";
                for (String rel : g.dirs) {
                    emit(sb, g, act, rel, st);
                }
            }
        }
        return sb.toString();
    }

    static String rulesForGroup(State s, String gid) {
        State one = new State();
        one.revision = s.revision;
        one.instanceId = s.instanceId;
        Group g = find(s, gid);
        if (g != null) {
            one.groups.add(g);
        }
        return toRules(one, false);
    }

    private static void emit(StringBuilder sb, Group g, String act, String rel, String st) {
        for (Entry t : g.targets) {
            if (t.isContainer()) {
                sb.append("C|").append(g.id).append('|').append(g.mode).append('|').append(act)
                        .append('|').append(g.primary.user).append('|').append(g.primary.pkg)
                        .append('|').append(rel).append('|').append(t.host).append('|')
                        .append(t.container).append('|').append(t.inner).append('|').append(rel)
                        .append('|').append(st).append('|')
                        .append(t.tpl)
                        .append('\n');
            } else {
                sb.append("R|").append(g.id).append('|').append(g.mode).append('|').append(act)
                        .append('|').append(g.primary.user).append('|').append(g.primary.pkg)
                        .append('|').append(rel).append('|').append(t.user).append('|')
                        .append(t.pkg).append('|').append(rel).append('|').append(st).append("|-\n");
            }
        }
    }

    static String validate(Group g) {
        if (!NAME.matcher(g.name).matches()) {
            return "err_group_gid";
        }
        if (!USER.matcher(g.primary.user).matches() || !PKG.matcher(g.primary.pkg).matches()) {
            return "err_pkg";
        }
        List<String> chosen = g.isMount() ? g.mountDirs : g.dirs;
        if (chosen.isEmpty()) {
            return "err_no_dirs";
        }
        for (String d : chosen) {
            if (!WHOLE.equals(d) && !REL.matcher(d).matches()) {
                return "err_dir";
            }
        }
        for (String d : g.extraLinks) {
            if (!REL.matcher(d).matches()) {
                return "err_dir";
            }
        }
        if (g.targets.isEmpty()) {
            return "err_no_target";
        }
        for (Entry t : g.targets) {
            if (t.isContainer()) {
                if (!PKG.matcher(t.host).matches() || !PKG.matcher(t.container).matches()
                        || !PKG.matcher(t.inner).matches()) {
                    return "err_pkg";
                }
            } else {
                if (!USER.matcher(t.user).matches() || !PKG.matcher(t.pkg).matches()) {
                    return "err_pkg";
                }
                if (t.user.equals(g.primary.user) && t.pkg.equals(g.primary.pkg)) {
                    return "err_same_path";
                }
            }
        }
        return "";
    }
}
