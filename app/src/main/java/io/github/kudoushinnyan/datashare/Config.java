package io.github.kudoushinnyan.datashare;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

final class Config {

    private Config() {
    }

    static List<String[]> rows(Context c, String asset) {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = c.getAssets().open(asset);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("[\\s,;]+");
                if (parts.length > 0) {
                    out.add(parts);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
