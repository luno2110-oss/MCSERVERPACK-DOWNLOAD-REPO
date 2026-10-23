package de.kuby.kubyplugins.service;

import org.bukkit.Material;

import java.util.Locale;

/** Ligen nach Trophäenzahl. Jede Liga hat eine einmalige Belohnung. */
public enum League {
    BRONZE("Bronze", 0, "<#cd7f32>", Material.COPPER_INGOT, 0, 0),
    SILBER("Silber", 100, "<gray>", Material.IRON_INGOT, 1000, 0),
    GOLD("Gold", 300, "<yellow>", Material.GOLD_INGOT, 2500, 5),
    PLATIN("Platin", 700, "<#8ff7e6>", Material.NETHERITE_INGOT, 5000, 15),
    DIAMANT("Diamant", 1300, "<aqua>", Material.DIAMOND, 10000, 40),
    CHAMPION("Champion", 1800, "<light_purple>", Material.AMETHYST_SHARD, 20000, 60);

    private final String label;
    private final int min;
    private final String color;
    private final Material icon;
    private final long coins;
    private final int jewels;

    League(String label, int min, String color, Material icon, long coins, int jewels) {
        this.label = label;
        this.min = min;
        this.color = color;
        this.icon = icon;
        this.coins = coins;
        this.jewels = jewels;
    }

    public String label() { return label; }
    public int min() { return min; }
    public Material icon() { return icon; }
    public long coins() { return coins; }
    public int jewels() { return jewels; }
    public String display() { return color + label; }
    public String tagId() { return "liga_" + name().toLowerCase(Locale.ROOT); }

    public static League of(int trophies) {
        League result = BRONZE;
        for (League l : values()) {
            if (trophies >= l.min) result = l;
        }
        return result;
    }

    /** Nächste Liga oder null, wenn die höchste erreicht ist. */
    public League next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }
}
