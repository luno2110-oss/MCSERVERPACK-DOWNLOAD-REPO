package de.kuby.kubysurvival.angeln;

import net.kyori.adventure.text.format.TextColor;

/**
 * Seltenheit eines Fisches: Farbe, Grundchance, Angel-Punkte und Grundwert beim Händler. Preis = Grundwert x Sterne
 * (1 bis 6,5) x Gewicht (0,5 bis 2) x Variante (Silber 3, Gold 8, Diamant 25); minPrice/maxPrice = Spanne ohne Variante.
 */
public enum Rarity {
    GEWOEHNLICH("Gewöhnlich", "GEWÖHNLICH!", 0xAAAAAA, 52.0, 5, 100),
    UNGEWOEHNLICH("Ungewöhnlich", "UNGEWÖHNLICH!", 0x55FF55, 28.0, 10, 220),
    SELTEN("Selten", "SELTEN!", 0x5555FF, 13.0, 25, 520),
    EPISCH("Episch", "EPISCH!", 0xAA00AA, 5.0, 60, 1300),
    LEGENDAER("Legendär", "LEGENDÄR!", 0xFFAA00, 1.7, 150, 3200),
    MYTHISCH("Mythisch", "MYTHISCH!", 0xFF55FF, 0.3, 400, 7700);

    public final String label;
    public final String title;
    public final int rgb;
    /** Grundgewicht beim Auswürfeln (Glück verschiebt es zu den seltenen). */
    public final double weight;
    public final int points;
    /** Grundwert (1 Stern, mittleres Gewicht x1). */
    public final long base;
    /** Spanne ohne Variante: leichtester 1-Stern-Fisch bis schwerster 5-Stern-Fisch. */
    public final long minPrice, maxPrice;

    Rarity(String label, String title, int rgb, double weight, int points, long base) {
        this.label = label;
        this.title = title;
        this.rgb = rgb;
        this.weight = weight;
        this.points = points;
        this.base = base;
        this.minPrice = Math.round(base * 0.5);
        this.maxPrice = Math.round(base * 6.5 * 2.0);
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
