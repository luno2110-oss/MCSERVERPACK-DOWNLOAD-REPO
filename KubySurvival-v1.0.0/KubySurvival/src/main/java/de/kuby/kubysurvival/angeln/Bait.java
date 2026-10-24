package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.ui.KitData;
import org.bukkit.Material;

import java.util.Map;

/**
 * Köder: liegen im Köderbeutel (nur Zähler), ein aktiver Köder wird bei jedem Fang verbraucht.
 * Kaufen (10 Stück) oder basteln (Zutaten aus dem Inventar).
 */
public enum Bait {
    REGENWURM("Regenwurm", KitData.Icon.WORM, "-10 % Wartezeit", 0, 10, 0, false, 60, 8,
            Map.of(Material.DIRT, 4, Material.BONE_MEAL, 1)),
    GLITZER("Glitzerköder", KitData.Icon.LURE_GLITTER, "+10 % Glück", 10, 0, 0, false, 250, 4,
            Map.of(Material.GLOWSTONE_DUST, 2, Material.IRON_NUGGET, 2, Material.STRING, 1)),
    NACHT("Nachtköder", KitData.Icon.LURE_NIGHT, "+20 % Glück nachts, -5 % Wartezeit", 20, 5, 0, true, 220, 4,
            Map.of(Material.GLOW_INK_SAC, 1, Material.SPIDER_EYE, 1, Material.STRING, 1)),
    GOLD("Goldköder", KitData.Icon.LURE_GOLD, "+3 % Schatzchance, +5 % Glück", 5, 0, 3.0, false, 600, 2,
            Map.of(Material.GOLD_NUGGET, 6, Material.STRING, 1));

    public final String label;
    public final KitData.Icon icon;
    public final String effect;
    /** Glück in Prozent (bei Nachtköder nur nachts). */
    public final int luck;
    /** Wartezeit kürzer in Prozent. */
    public final int faster;
    /** Schatzchance plus Prozentpunkte. */
    public final double treasure;
    public final boolean nightLuck;
    /** Preis für 10 Stück. */
    public final long price10;
    /** So viele entstehen beim Basteln. */
    public final int craftAmount;
    public final Map<Material, Integer> recipe;

    Bait(String label, KitData.Icon icon, String effect, int luck, int faster, double treasure, boolean nightLuck,
         long price10, int craftAmount, Map<Material, Integer> recipe) {
        this.label = label;
        this.icon = icon;
        this.effect = effect;
        this.luck = luck;
        this.faster = faster;
        this.treasure = treasure;
        this.nightLuck = nightLuck;
        this.price10 = price10;
        this.craftAmount = craftAmount;
        this.recipe = recipe;
    }

    public static Bait of(String name) {
        if (name == null) return null;
        for (Bait b : values()) if (b.name().equalsIgnoreCase(name)) return b;
        return null;
    }
}
