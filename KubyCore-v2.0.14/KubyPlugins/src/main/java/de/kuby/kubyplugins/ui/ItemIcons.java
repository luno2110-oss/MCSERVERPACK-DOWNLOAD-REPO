package de.kuby.kubyplugins.ui;

import org.bukkit.Material;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Icons für (fast) alle Vanilla-Items aus einem Bild (tools/build_item_icons.py): Blöcke in 3D wie im Inventar, Items
 * flach. Je Item eine Nummer; Zeichen E000 + Nummer (32 px) und E800 + Nummer (16 px) in den Schriften kuby:k/x&lt;s&gt;.
 * Spezial-Modelle (Truhen, Betten, Banner, Köpfe, Schild) fehlen, dafür gibt es eigene Kit-Icons (Icons.of).
 */
public final class ItemIcons {

    /** Nummer und Vorschub bei 32 und 16 px. */
    public record Entry(int index, int adv32, int adv16) {}

    private static final Map<Material, Entry> MAP = new EnumMap<>(Material.class);

    static {
        try (InputStream in = ItemIcons.class.getResourceAsStream("/ui/items.tsv")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] p = line.split("\t");
                    Material m = Material.getMaterial(p[0]);
                    if (m != null && p.length >= 4) MAP.put(m, new Entry(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                }
            }
        } catch (Exception ignored) {
            // ohne Tabelle gibt es nur die Kit-Icons
        }
    }

    private ItemIcons() {
    }

    public static Entry of(Material m) {
        return m == null ? null : MAP.get(m);
    }

    public static boolean has(Material m) {
        return of(m) != null;
    }

    public static int count() {
        return MAP.size();
    }
}
