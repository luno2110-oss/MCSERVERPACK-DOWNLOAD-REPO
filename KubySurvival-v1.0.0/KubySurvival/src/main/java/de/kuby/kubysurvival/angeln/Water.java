package de.kuby.kubysurvival.angeln;

import org.bukkit.Location;
import org.bukkit.block.Biome;

import java.util.Locale;

/** Gewässer = Seite im Fischbuch. Welche Fische beißen, hängt vom Biom am Schwimmer ab. Mythen nur in Angel-Zonen. */
public enum Water {
    FLUSS("Flüsse & Seen"),
    OZEAN("Ozean"),
    WARM("Warme Gewässer"),
    KALT("Kalte Gewässer"),
    SUMPF("Sumpf & Höhlen"),
    MYTHOS("Mythen & Zone");

    public final String label;

    Water(String label) {
        this.label = label;
    }

    public String upper() {
        return label.toUpperCase(Locale.GERMANY);
    }

    /** Gewässer am Schwimmer: Höhlenwasser (tief unter der Oberfläche) zählt als Sumpf & Höhlen. */
    @SuppressWarnings("deprecation")
    public static Water at(Location hook) {
        if (hook == null || hook.getWorld() == null) return FLUSS;
        int surface = hook.getWorld().getHighestBlockYAt(hook);
        if (hook.getBlockY() < 50 && surface > hook.getBlockY() + 8) return SUMPF;
        Biome biome = hook.getBlock().getBiome();
        String b = biome.getKey().getKey();
        if (b.contains("swamp") || b.contains("caves") || b.equals("deep_dark")) return SUMPF;
        if (b.startsWith("warm") || b.contains("lukewarm") || b.contains("jungle") || b.contains("desert")
                || b.contains("savanna") || b.contains("badlands") || b.equals("mushroom_fields")) return WARM;
        if (b.contains("frozen") || b.contains("cold") || b.contains("snowy") || b.contains("ice") || b.contains("taiga")
                || b.equals("grove") || b.contains("peaks") || b.equals("snowy_slopes")) return KALT;
        if (b.contains("ocean") || b.equals("beach") || b.equals("stony_shore")) return OZEAN;
        return FLUSS;
    }
}
