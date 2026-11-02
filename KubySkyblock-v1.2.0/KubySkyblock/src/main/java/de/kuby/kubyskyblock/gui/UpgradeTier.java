package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyskyblock.service.UpgradeLevels;
import net.kyori.adventure.text.format.TextColor;

/**
 * Anzeige der Upgrade-Stufen (Level 1-10 Stufe 1, 11-15 Stufe 2, 16-20 Stufe 3, 21-50 Stufe 4 nur mit Juwelen): Icon je
 * Stufe (Stufe 2 goldene, 3 lila, 4 Regenbogen-Sterne; Bilder art_&lt;name&gt;_t2/_t3/_t4 aus tools/build_hub_icons.py),
 * Farbe und Text (Stufe 4 in Regenbogen-Buchstaben).
 */
public final class UpgradeTier {

    static final TextColor T1 = TextColor.color(0x8AA8C0), T2 = TextColor.color(0xF8D35A), T3 = TextColor.color(0xD58CFF),
            T4 = TextColor.color(0x7FF0C8);
    /** Regenbogen für Stufe 4: rot, orange, gelb, grün, blau, lila. */
    static final TextColor[] RAINBOW = {TextColor.color(0xFF6A6A), TextColor.color(0xFFA24A), TextColor.color(0xFFE15A),
            TextColor.color(0x7FE08A), TextColor.color(0x6AB8FF), TextColor.color(0xD58CFF)};
    /** Juwelen-Preise (Stufe 4) */
    public static final TextColor JEWEL = TextColor.color(0x7FF0A0);

    private UpgradeTier() {
    }

    public static int of(int level) {
        return UpgradeLevels.tier(level);
    }

    /** Icon des Upgrades für dieses Level (Stufe 1 = normales Icon). */
    public static KitData.Sprite icon(KitData.Sprite base, int level) {
        int t = of(level);
        if (t < 2) return base;
        try {
            return KitData.Sprite.valueOf(base.name() + "_T" + t);
        } catch (IllegalArgumentException e) {
            return base;
        }
    }

    public static TextColor color(int level) {
        return switch (of(level)) {
            case 4 -> T4;
            case 3 -> T3;
            case 2 -> T2;
            default -> T1;
        };
    }

    /** Balken-Farbe der Stufe. */
    public static KitData.Fill fill(int level) {
        return switch (of(level)) {
            case 4 -> KitData.Fill.GREEN;
            case 3 -> KitData.Fill.PURPLE;
            case 2 -> KitData.Fill.GOLD;
            default -> KitData.Fill.CYAN;
        };
    }

    /** Text in der Farbe der Stufe; Stufe 4 Buchstabe für Buchstabe im Regenbogen. Gibt die Breite zurück. */
    public static int text(de.kuby.kubyplugins.ui.Canvas c, int x, int y, String s, int level, boolean bold) {
        if (of(level) < 4) return c.text(x, y, s, color(level), bold);
        int w = 0, k = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            w += c.text(x + w, y, ch, ch.isBlank() ? T4 : RAINBOW[k++ % RAINBOW.length], bold);
        }
        return w;
    }

    /** Rechtsbündig wie text(). */
    public static void textRight(de.kuby.kubyplugins.ui.Canvas c, int right, int y, String s, int level, boolean bold) {
        if (of(level) < 4) {
            c.textRight(right, y, s, color(level), bold);
            return;
        }
        text(c, right - de.kuby.kubyplugins.ui.Canvas.width(s, bold), y, s, level, bold);
    }

    /** „Lvl 12 · Stufe 2“ (für schmale Karten) */
    public static String shortLabel(int level) {
        return "Lvl " + level + " · Stufe " + of(level);
    }

    /** „Lvl 12/20 · Stufe 2“ */
    public static String label(int level, int max) {
        return "Lvl " + level + "/" + max + " · Stufe " + of(level);
    }
}
