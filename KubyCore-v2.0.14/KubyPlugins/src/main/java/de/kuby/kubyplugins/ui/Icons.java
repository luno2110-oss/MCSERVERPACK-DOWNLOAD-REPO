package de.kuby.kubyplugins.ui;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Passendes Kit-Icon zu einem Material (für Menü-Einträge, die ein Material als Symbol angeben). */
public final class Icons {

    private static final Map<String, String> ALIAS = new HashMap<>();

    static {
        String[][] a = {
                {"FISHING_ROD", "ROD"}, {"ENCHANTED_BOOK", "BOOK"}, {"BOOK", "BOOK_PLAIN"}, {"AMETHYST_SHARD", "AMETHYST"},
                {"WRITABLE_BOOK", "WRITABLE"}, {"LILY_PAD", "LILY"}, {"TOTEM_OF_UNDYING", "TOTEM"}, {"NETHER_STAR", "STAR"},
                {"EXPERIENCE_BOTTLE", "EXPERIENCE"}, {"HEART_OF_THE_SEA", "HEART_SEA"}, {"NAUTILUS_SHELL", "NAUTILUS"},
                {"TROPICAL_FISH", "TROPICAL"}, {"PUFFERFISH", "PUFFER"}, {"GOLDEN_HELMET", "GOLD_HELMET"},
                {"PRISMARINE_CRYSTALS", "PRISMARINE"}, {"GLOW_INK_SAC", "GLOW_INK"}, {"INK_SAC", "INK"},
                {"MUSIC_DISC_CAT", "MUSIC_DISC"}, {"MUSIC_DISC_13", "DISC_13"}, {"CLOCK", "CLOCK"},
                // Blöcke und Dinge ohne eigenes Bild: ähnliches Symbol
                {"SHIELD", "IRON_CHESTPLATE"}, {"ENCHANTING_TABLE", "ENCHANTING_TABLE"}, {"OAK_FENCE", "OAK_LOG"},
                {"PLAYER_HEAD", "ARMOR_STAND"}, {"FILLED_MAP", "MAP"}, {"SKELETON_SKULL", "BONE"}, {"GRAY_STAINED_GLASS_PANE", "BARRIER"},
                {"BEACON", "STAR"}, {"CHEST", "CHEST_BLOCK"}, {"JUKEBOX", "JUKEBOX_BLOCK"}, {"TRAPPED_CHEST", "CHEST_BLOCK"}, {"COMPARATOR", "COMPARATOR"}, {"REDSTONE_TORCH", "TORCH"},
        };
        for (String[] e : a) ALIAS.put(e[0], e[1]);
    }

    private Icons() {
    }

    /** Icon zum Material; Betten, Banner und ähnliche Familien auf ein Bild, sonst fallback. */
    public static KitData.Icon of(Material m, KitData.Icon fallback) {
        if (m == null) return fallback;
        String n = m.name();
        KitData.Icon ic = find(ALIAS.getOrDefault(n, n));
        if (ic != null) return ic;
        if (n.endsWith("_BED")) return KitData.Icon.RED_BED;
        if (n.endsWith("_BANNER")) return KitData.Icon.WHITE_BANNER;
        if (n.endsWith("_LOG") || n.endsWith("_WOOD")) return KitData.Icon.OAK_LOG;
        if (n.endsWith("_SAPLING")) return KitData.Icon.OAK_SAPLING;
        if (n.endsWith("_DOOR")) return KitData.Icon.OAK_DOOR;
        if (n.endsWith("_SIGN")) return KitData.Icon.OAK_SIGN;
        if (n.contains("SHULKER")) return KitData.Icon.SHULKER_SHELL;
        if (n.endsWith("_DYE")) return KitData.Icon.LIME_DYE;
        return fallback;
    }

    /** Graue Vanilla-Bilder, die Minecraft einfärbt (Seerosenblatt): Farbe zum Einfärben, sonst Weiß (unverändert). */
    public static net.kyori.adventure.text.format.TextColor tint(KitData.Icon icon) {
        return icon == KitData.Icon.LILY ? net.kyori.adventure.text.format.TextColor.color(0x5DA642)
                : net.kyori.adventure.text.format.NamedTextColor.WHITE;
    }

    public static KitData.Icon of(Material m) {
        return of(m, KitData.Icon.PAPER);
    }

    private static KitData.Icon find(String name) {
        try {
            return KitData.Icon.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
