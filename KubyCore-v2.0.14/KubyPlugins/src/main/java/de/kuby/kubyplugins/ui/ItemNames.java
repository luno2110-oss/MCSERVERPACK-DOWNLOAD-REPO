package de.kuby.kubyplugins.ui;

import org.bukkit.Material;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Deutsche Item-Namen für Dialoge (tools/build_item_names.py), sonst ein lesbarer Name aus der ID. */
public final class ItemNames {

    private static final Map<Material, String> DE = new EnumMap<>(Material.class);

    static {
        try (InputStream in = ItemNames.class.getResourceAsStream("/ui/names_de.tsv")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    int tab = line.indexOf('\t');
                    if (tab < 0) continue;
                    Material m = Material.getMaterial(line.substring(0, tab));
                    if (m != null) DE.put(m, line.substring(tab + 1).trim());
                }
            }
        } catch (Exception ignored) {
            // ohne Tabelle: Namen aus der ID
        }
    }

    private ItemNames() {
    }

    public static String de(Material m) {
        if (m == null) return "?";
        String n = DE.get(m);
        if (n != null) return n;
        StringBuilder b = new StringBuilder();
        for (String w : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }
}
