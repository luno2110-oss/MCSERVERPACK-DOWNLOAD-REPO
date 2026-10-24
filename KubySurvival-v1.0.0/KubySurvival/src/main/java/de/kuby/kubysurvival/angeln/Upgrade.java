package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.ui.KitData;

/**
 * Dauerhafte Verbesserungen der Angel (gelten für jede Rute). Die erste Stufe kostet den Grundpreis, jede weitere
 * die Hälfte mehr (Glückshaken: 5.000, 7.500, 10.000, 12.500, 15.000).
 */
public enum Upgrade {
    GLUECKSHAKEN("Glückshaken", KitData.Icon.RABBIT_FOOT, 5, 5000, "Glück"),
    SCHNELLSPULE("Schnellspule", KitData.Icon.FEATHER, 5, 4000, "Wartezeit"),
    SCHATZSINN("Schatzsinn", KitData.Icon.HEART_SEA, 3, 10000, "Schatzchance"),
    GROSSFANG("Großfang-Rolle", KitData.Icon.COD, 3, 12000, "Extra-Würfe aufs Gewicht");

    public final String label;
    public final KitData.Icon icon;
    public final int max;
    public final long basePrice;
    public final String what;

    Upgrade(String label, KitData.Icon icon, int max, long basePrice, String what) {
        this.label = label;
        this.icon = icon;
        this.max = max;
        this.basePrice = basePrice;
        this.what = what;
    }

    /** Preis für die Stufe level + 1. */
    public long price(int level) {
        return basePrice * (2 + level) / 2;
    }

    /** Wirkung als Text für eine Stufe, z. B. "+8 % Glück". */
    public String effect(int level) {
        return switch (this) {
            case GLUECKSHAKEN -> "+" + (4 * level) + " % Glück";
            case SCHNELLSPULE -> "-" + (4 * level) + " % Wartezeit";
            case SCHATZSINN -> "+" + AngelText.dec(0.5 * level) + " % Schatzchance";
            case GROSSFANG -> level + " Extra-" + (level == 1 ? "Wurf" : "Würfe") + " aufs Gewicht";
        };
    }
}
