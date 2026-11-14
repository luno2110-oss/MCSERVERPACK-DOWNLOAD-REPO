package de.kuby.kubyplugins.bedrock;

import de.kuby.kubyplugins.ui.KitData;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Was das Bedrock-Pack (tools/build_bedrock_pack.py) enthält: je Zeichen der Java-Icon-Schriften (kuby:k/i…) und der
 * Item-Schriften (kuby:k/x…) ein Bild für Formular-Knöpfe und, für kleine Icons, ein Glyph-Zeichen für Text (Formulare,
 * Scoreboard, Chat). Die Liste kommt als resources/bedrock/glyphs.tsv mit: Art (i/x) TAB Java-Zeichen (hex) TAB
 * Bedrock-Zeichen (hex oder -) TAB Bildname (oder -).
 */
public final class BedrockAssets {

    private static final Map<Character, String> ICON_GLYPH = new HashMap<>(), ICON_IMAGE = new HashMap<>();
    private static final Map<Character, String> ITEM_GLYPH = new HashMap<>(), ITEM_IMAGE = new HashMap<>();
    /** Scoreboard-Icons und Modus-Logos (Schrift kubyplugins:sb). */
    private static final Map<Character, String> SB_GLYPH = new HashMap<>();
    /** Gleiches Kit-Icon in anderer Größe: Zeichen -> Zeichen der 16-px-Fassung (und der 32-px-Fassung für Bilder). */
    private static final Map<Character, Character> TO16 = new HashMap<>(), TO32 = new HashMap<>();
    private static boolean loaded;

    private BedrockAssets() {
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        for (KitData.Icon icon : KitData.Icon.values()) {
            for (char c : new char[] {icon.c8, icon.c16, icon.c32}) {
                if (c == 0) continue;
                if (icon.c16 != 0) TO16.put(c, icon.c16);
                if (icon.c32 != 0) TO32.put(c, icon.c32);
            }
        }
        try (InputStream in = BedrockAssets.class.getResourceAsStream("/bedrock/glyphs.tsv")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length < 4 || p[0].startsWith("#")) continue;
                char c = (char) Integer.parseInt(p[1], 16);
                if (p[0].equals("s")) {
                    if (!p[2].equals("-")) SB_GLYPH.put(c, String.valueOf((char) Integer.parseInt(p[2], 16)));
                    continue;
                }
                boolean item = p[0].equals("x");
                if (!p[2].equals("-")) (item ? ITEM_GLYPH : ICON_GLYPH).put(c, String.valueOf((char) Integer.parseInt(p[2], 16)));
                if (!p[3].equals("-")) (item ? ITEM_IMAGE : ICON_IMAGE).put(c, "textures/kuby/ui/" + p[3]);
            }
        } catch (Exception ignored) {
            // ohne Liste: Formulare ohne Bilder und Text-Icons
        }
    }

    /** Glyph-Zeichen für Text (kind 1 = Kit-Icon, 2 = Item) oder null. */
    public static String glyph(int kind, char c) {
        load();
        if (kind == 2) {
            String g = ITEM_GLYPH.get(c);
            return g != null ? g : ITEM_GLYPH.get((char) (c ^ 0x0800));
        }
        String g = ICON_GLYPH.get(c);
        if (g == null && TO16.containsKey(c)) g = ICON_GLYPH.get(TO16.get(c));
        return g;
    }

    /** Bild-Pfad im Bedrock-Pack für Formular-Knöpfe (größte vorhandene Fassung) oder null. */
    public static String image(int kind, char c) {
        load();
        if (kind == 2) {
            String big = ITEM_IMAGE.get((char) (c < 0xE800 ? c : c - 0x0800));
            return big != null ? big : ITEM_IMAGE.get(c);
        }
        if (TO32.containsKey(c) && ICON_IMAGE.containsKey(TO32.get(c))) return ICON_IMAGE.get(TO32.get(c));
        return ICON_IMAGE.get(c);
    }

    /** Glyph eines Kit-Icons nach Namen (für Scoreboard und Chat), z. B. glyph(KitData.Icon.GOLD_NUGGET). */
    public static String glyph(KitData.Icon icon) {
        char c = icon.c16 != 0 ? icon.c16 : icon.c32;
        String g = c == 0 ? null : glyph(1, c);
        return g == null ? "" : g;
    }

    /** Glyph des Item-Bilds zu einem Material (16 px) oder "". */
    public static String glyph(org.bukkit.Material m) {
        de.kuby.kubyplugins.ui.ItemIcons.Entry e = de.kuby.kubyplugins.ui.ItemIcons.of(m);
        if (e == null) {
            KitData.Icon kit = de.kuby.kubyplugins.ui.Icons.exact(m);
            return kit == null ? "" : glyph(kit);
        }
        String g = glyph(2, (char) (0xE800 + e.index()));
        return g == null ? "" : g;
    }

    /** Scoreboard-Icon (Zeichen der Schrift kubyplugins:sb, z. B. ScoreboardText.I_COIN) als Bedrock-Glyph oder "". */
    public static String scoreboard(char c) {
        load();
        return SB_GLYPH.getOrDefault(c, "");
    }

    public static boolean available() {
        load();
        return !ICON_IMAGE.isEmpty() || !ITEM_IMAGE.isEmpty();
    }
}
