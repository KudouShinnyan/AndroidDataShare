package io.github.kudoushinnyan.datashare;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class ModuleGen {

    private static final String TEMPLATE = "module-template.zip";
    private static final String OUT = "AndroidDataShare-module.zip";

    private ModuleGen() {
    }

    static File output(Context c) {
        return new File(c.getExternalFilesDir(null), OUT);
    }

    static boolean generate(Context c) {
        Model.State s = Session.get();
        String state = Model.toStateJson(s);
        String rules = Model.toRules(s, true);
        Set<String> replaced = new HashSet<>();
        replaced.add("config/state.json");
        replaced.add("config/rules.txt");
        try {
            File out = output(c);
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out));
            ZipInputStream zin = new ZipInputStream(c.getAssets().open(TEMPLATE));
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory() && !replaced.contains(e.getName())) {
                    ZipEntry ne = new ZipEntry(e.getName());
                    zos.putNextEntry(ne);
                    copy(zin, zos);
                    zos.closeEntry();
                }
                zin.closeEntry();
            }
            zin.close();
            put(zos, "config/state.json", state);
            put(zos, "config/rules.txt", rules);
            zos.close();
            Backend.Result r = Backends.current().exec(
                    "engine_publish \"$ADS_MODULE_ZIP\" '" + out.getAbsolutePath() + "'\n");
            if (!r.ok) {
                out.delete();
                return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void put(ZipOutputStream z, String name, String text) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(text.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static void copy(InputStream in, ZipOutputStream out) throws Exception {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            out.write(b, 0, n);
        }
    }

    static String license(Context c) {
        try {
            return Engine.assetText(c, "LICENSE.txt");
        } catch (Exception e) {
            return "";
        }
    }
}
