package de.kuby.kubyskyblock.service;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Items, die man im Überlebensmodus bekommen kann. Ausgeschlossen sind Creative- und Technik-Blöcke (Command-Blöcke,
 * Barrieren, Struktur-Blöcke, Spawn-Eier, Grundgestein ...), damit Loot sie nie verteilt und die Checkliste ohne sie
 * auf 100 % kommt.
 */
public final class Obtainable {

    private static final Set<Material> EXCLUDED = EnumSet.noneOf(Material.class);
    private static final List<Material> ALL = new ArrayList<>();

    static {
        String[] names = {"COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "BARRIER", "LIGHT",
                "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "DEBUG_STICK", "KNOWLEDGE_BOOK", "BEDROCK", "END_PORTAL_FRAME",
                "REINFORCED_DEEPSLATE", "BUDDING_AMETHYST", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "PETRIFIED_OAK_SLAB", "FARMLAND",
                "DIRT_PATH", "CHORUS_PLANT", "FROGSPAWN", "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "PLAYER_HEAD", "END_GATEWAY", "END_PORTAL",
                "NETHER_PORTAL", "WATER", "LAVA", "FIRE", "SOUL_FIRE", "MOVING_PISTON", "PISTON_HEAD", "BUBBLE_COLUMN", "POWDER_SNOW",
                "TALL_SEAGRASS", "CAVE_AIR", "VOID_AIR", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL"};
        for (String n : names) {
            Material m = Material.getMaterial(n);
            if (m != null) EXCLUDED.add(m);
        }
        for (Material m : Material.values()) {
            if (!m.isItem() || m.isAir() || m.isLegacy()) continue;
            String n = m.name();
            if (EXCLUDED.contains(m) || n.endsWith("_SPAWN_EGG") || n.startsWith("INFESTED_")) continue;
            ALL.add(m);
        }
    }

    private Obtainable() {
    }

    public static boolean is(Material m) {
        return m != null && m.isItem() && !m.isAir() && !EXCLUDED.contains(m) && !m.name().endsWith("_SPAWN_EGG")
                && !m.name().startsWith("INFESTED_");
    }

    /** Alle erreichbaren Items (feste Reihenfolge). */
    public static List<Material> all() {
        return ALL;
    }
}
