package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyskyblock.service.UpgradeLevels;
import net.kyori.adventure.text.format.TextColor;

/**
 * Anzeige der Upgrade-Stufen (Level 1-10 Stufe 1, 11-15 Stufe 2, 16-20 Stufe 3): Icon je Stufe (ab Stufe 2 mit goldenen,
 * ab Stufe 3 mit lila Sternen, Bilder art_&lt;name&gt;_t2/_t3 aus tools/build_hub_icons.py), Farbe und Text.
 */
public final class UpgradeTier {

    static final TextColor T1 = TextColor.color(0x8AA8C0), T2 = TextColor.color(0xF8D35A), T3 = TextColor.color(0xD58CFF);

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
            case 3 -> T3;
            case 2 -> T2;
            default -> T1;
        };
    }

    /** Balken-Farbe der Stufe. */
    public static KitData.Fill fill(int level) {
        return switch (of(level)) {
            case 3 -> KitData.Fill.PURPLE;
            case 2 -> KitData.Fill.GOLD;
            default -> KitData.Fill.CYAN;
        };
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
