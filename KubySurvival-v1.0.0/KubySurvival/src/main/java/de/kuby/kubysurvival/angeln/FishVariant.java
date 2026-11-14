package de.kuby.kubysurvival.angeln;

import net.kyori.adventure.text.format.TextColor;

/**
 * Seltene Varianten jeder Fischart: Silber, Gold und Diamant. Eigene glänzende Textur (kuby:fish/&lt;id&gt;_&lt;variante&gt;),
 * Preis mal mult, nie im Auto-Verkauf. Chancen je Fang in Prozent (Standard, einstellbar unter angeln.varianten).
 */
public enum FishVariant {
    NORMAL("", "", 0xFFFFFF, 1, 0, null),
    SILBER("silber", "Silber", 0xD8E2EE, 3, 1.2, "#F4F8FF"),
    GOLD("gold", "Gold", 0xFFC93A, 8, 0.3, "#FFF0A0"),
    DIAMANT("diamant", "Diamant", 0x5FF0F0, 25, 0.04, "#DFFFFF");

    /** ID für Textur und Speicherung ("" bei NORMAL). */
    public final String id;
    public final String label;
    public final int rgb;
    /** Preis-Multiplikator. */
    public final long mult;
    /** Standard-Chance je Fang in Prozent. */
    public final double chance;
    /** Helle Farbe für Titel-Verläufe. */
    public final String light;

    FishVariant(String id, String label, int rgb, long mult, double chance, String light) {
        this.id = id;
        this.label = label;
        this.rgb = rgb;
        this.mult = mult;
        this.chance = chance;
        this.light = light;
    }

    public TextColor color() {
        return TextColor.color(rgb);
    }

    public String hex() {
        return String.format("#%06X", rgb);
    }

    public boolean special() {
        return this != NORMAL;
    }

    /** Item-Modell der Art in dieser Variante. */
    public String model(FishSpecies s) {
        return this == NORMAL ? "fish/" + s.id() : "fish/" + s.id() + "_" + id;
    }

    /** Name mit Variante, z. B. „Gold-Hecht“. */
    public String name(FishSpecies s) {
        return this == NORMAL ? s.name() : label + "-" + s.name();
    }

    public static FishVariant byId(String id) {
        if (id == null || id.isEmpty()) return NORMAL;
        for (FishVariant v : values()) if (v.id.equals(id)) return v;
        return NORMAL;
    }
}
