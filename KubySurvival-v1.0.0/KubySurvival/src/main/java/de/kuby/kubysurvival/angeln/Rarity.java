package de.kuby.kubysurvival.angeln;

import net.kyori.adventure.text.format.TextColor;

/** Seltenheit eines Fisches: Farbe, Grundchance, Angel-Punkte und Preisspanne beim Händler (1 bis 5 Sterne). */
public enum Rarity {
    GEWOEHNLICH("Gewöhnlich", "GEWÖHNLICH!", 0xAAAAAA, 52.0, 5, 2, 8),
    UNGEWOEHNLICH("Ungewöhnlich", "UNGEWÖHNLICH!", 0x55FF55, 28.0, 10, 5, 19),
    SELTEN("Selten", "SELTEN!", 0x5555FF, 13.0, 25, 14, 53),
    EPISCH("Episch", "EPISCH!", 0xAA00AA, 5.0, 60, 45, 169),
    LEGENDAER("Legendär", "LEGENDÄR!", 0xFFAA00, 1.7, 150, 180, 675),
    MYTHISCH("Mythisch", "MYTHISCH!", 0xFF55FF, 0.3, 400, 900, 3375);

    public final String label;
    public final String title;
    public final int rgb;
    /** Grundgewicht beim Auswürfeln (Glück verschiebt es zu den seltenen). */
    public final double weight;
    public final int points;
    public final long minPrice, maxPrice;

    Rarity(String label, String title, int rgb, double weight, int points, long minPrice, long maxPrice) {
        this.label = label;
        this.title = title;
        this.rgb = rgb;
        this.weight = weight;
        this.points = points;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public TextColor color() {
        return TextColor.color(rgb);
    }

    public String hex() {
        return String.format("#%06X", rgb);
    }

    public boolean atLeast(Rarity r) {
        return ordinal() >= r.ordinal();
    }
}
