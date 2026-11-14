package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Angeln aus dem Angel-Shop. Die Vorteile gelten, solange man mit der Angel fischt (zusätzlich zu den Upgrades).
 * Holz bis Netherite für Coins, Rainbow, Void und Göttlich nur für Juwelen (1.000 Juwelen = 10 Euro).
 *
 * @param luck        Glück in Prozent
 * @param faster      Wartezeit kürzer in Prozent
 * @param treasure    Schatzchance in Prozentpunkten
 * @param weight      Extra-Würfe aufs Gewicht (wie die Großfang-Rolle)
 * @param doubleCatch Chance auf zwei Fische in Prozent
 * @param points      Angel-Punkte mehr in Prozent
 * @param coins       Anteil des Fischwerts sofort als Coins (Prozent)
 * @param star        Chance auf einen Stern mehr (Prozent)
 */
public enum Rod {
    HOLZ("Holzangel", "holz", 0x9C7B3C, false, 250, 2, 0, 0, 0, 0, 0, 0, 0, "Die Angel für den Anfang."),
    STEIN("Steinangel", "stein", 0x9E9E9E, false, 1_000, 3, 3, 0, 0, 0, 0, 0, 0, "Schwer und zuverlässig."),
    KUPFER("Kupferangel", "kupfer", 0xE9956A, false, 2_500, 4, 5, 0, 0, 0, 0, 0, 0, "Bei Regen zusätzlich -10 % Wartezeit."),
    EISEN("Eisenangel", "eisen", 0xDCDCDC, false, 5_000, 6, 6, 0, 0, 0, 0, 0, 0, "Unzerstörbar."),
    GOLD("Goldangel", "gold", 0xF8D35A, false, 10_000, 6, 6, 0, 0, 0, 0, 15, 0, "Goldene Hand: +15 % des Fischwerts sofort als Coins."),
    LAPIS("Lapisangel", "lapis", 0x6A96F0, false, 16_000, 8, 8, 0, 0, 0, 25, 0, 0, "+25 % Angel-Punkte."),
    DIAMANT("Diamantangel", "diamant", 0x6FE3D2, false, 25_000, 10, 10, 0, 1, 0, 0, 0, 0, "+1 Gewichtswurf: schwerere Fische."),
    SMARAGD("Smaragdangel", "smaragd", 0x6CE86E, false, 40_000, 11, 10, 1.5, 0, 0, 0, 0, 0,
            "+1,5 % Schatzchance, Schätze auch außerhalb der Angel-Zone."),
    QUARZ("Quarzangel", "quarz", 0xF4ECE4, false, 60_000, 12, 11, 0, 0, 0, 0, 0, 0, "Nachts +12 % Glück, Nachtfische doppelt so oft."),
    AMETHYST("Amethystangel", "amethyst", 0xC7A0F8, false, 85_000, 13, 12, 0, 0, 8, 0, 0, 0, "Resonanz: 8 % Chance auf Doppelfang."),
    NETHERITE("Netheriteangel", "netherite", 0xA89AA8, false, 125_000, 15, 14, 0, 2, 0, 0, 0, 0, "+2 Gewichtswürfe, unzerstörbar."),
    RAINBOW("Rainbow-Angel", "rainbow", 0xFF8AD8, true, 1_000, 30, 30, 0, 0, 0, 30, 0, 30,
            "Regenbogen-Fang: 30 % Chance auf einen Stern mehr, +30 % Angel-Punkte, bunte Schnur."),
    VOID("Void-Angel", "void", 0xB46CFF, true, 1_500, 40, 35, 4, 1, 15, 0, 0, 0,
            "Mythos-Fische überall, Schätze überall (+4 %), 15 % Doppelfang."),
    GOETTLICH("Göttliche Angel", "goettlich", 0xFFE7A8, true, 2_000, 60, 45, 5, 3, 30, 50, 40, 35,
            "Segen: +60 % Glück, -45 % Wartezeit, 30 % Doppelfang, 35 % Stern mehr, +40 % Fischwert sofort.");

    public final String label, id, perk;
    public final int color;
    /** true: nur für Juwelen. */
    public final boolean jewels;
    public final long price;
    public final double luck, faster, treasure;
    public final int weight, doubleCatch, points, coins, star;

    Rod(String label, String id, int color, boolean jewels, long price, double luck, double faster, double treasure, int weight,
        int doubleCatch, int points, int coins, int star, String perk) {
        this.label = label;
        this.id = id;
        this.color = color;
        this.jewels = jewels;
        this.price = price;
        this.luck = luck;
        this.faster = faster;
        this.treasure = treasure;
        this.weight = weight;
        this.doubleCatch = doubleCatch;
        this.points = points;
        this.coins = coins;
        this.star = star;
        this.perk = perk;
    }

    public TextColor textColor() {
        return TextColor.color(color);
    }

    /** Ab Eisen unzerstörbar. */
    public boolean unbreakable() {
        return ordinal() >= EISEN.ordinal();
    }

    /** Schätze auch außerhalb der Angel-Zone. */
    public boolean treasureAnywhere() {
        return this == SMARAGD || this == VOID || this == GOETTLICH;
    }

    /** Mythos-Fische auch außerhalb der Angel-Zone. */
    public boolean mythosAnywhere() {
        return this == VOID || this == GOETTLICH;
    }

    public KitData.Icon icon() {
        return KitData.Icon.valueOf("ANGEL_" + id.toUpperCase(java.util.Locale.ROOT));
    }

    public String model() {
        return "kuby:angeln/rod_" + id;
    }

    /** Vorteil kurz für die kleine Karte im Shop. */
    public String shortPerk() {
        return switch (this) {
            case KUPFER -> "Bei Regen -10 % Wartezeit";
            case GOLD -> "+15 % Fischwert als Coins";
            case DIAMANT -> "+1 Gewichtswurf";
            case SMARAGD -> "Schätze überall, +1,5 %";
            case QUARZ -> "Nachts +12 % Glück";
            case AMETHYST -> "8 % Doppelfang";
            case NETHERITE -> "+2 Gewichtswürfe";
            case RAINBOW -> "30 % Stern mehr, +30 % Punkte";
            case VOID -> "Mythos überall, 15 % Doppelfang";
            case GOETTLICH -> "Alles: 30 % Doppel, 35 % Stern";
            default -> perk;
        };
    }

    public static Rod byId(String id) {
        for (Rod r : values()) if (r.id.equalsIgnoreCase(id) || r.name().equalsIgnoreCase(id)) return r;
        return null;
    }

    /** Werte als kurze Zeile, z. B. "Glück +10 % · Tempo +10 %". */
    public String stats() {
        List<String> l = new ArrayList<>();
        l.add("Glück +" + AngelText.dec(luck) + " %");
        if (faster > 0) l.add("Tempo +" + AngelText.dec(faster) + " %");
        return String.join(" · ", l);
    }
}
