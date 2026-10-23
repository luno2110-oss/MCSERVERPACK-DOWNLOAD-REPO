package de.kuby.kubysurvival.jobs;

import org.bukkit.Material;

import java.util.List;

/** Die acht Survival-Jobs. Nur der aktive Job bringt XP und Coins. */
public enum Job {
    HOLZFAELLER("Holzfäller", "Holzfaeller", Material.IRON_AXE, "<#C8884A>", "Baumstämme fällen",
            List.of("Baumstämme aller Holzarten fällen", "Selbst gesetzte Stämme zählen nicht")),
    MINER("Miner", "Miner", Material.IRON_PICKAXE, "<gray>", "Stein und Erze abbauen",
            List.of("Erze abbauen: Diamant, Smaragd und Antiker Schrott zahlen am meisten",
                    "Stein, Tiefenschiefer, Netherrack und Co. bringen XP", "Steine aus Generatoren zählen nicht")),
    GRAEBER("Gräber", "Graeber", Material.IRON_SHOVEL, "<#B08850>", "Erde, Sand und Kies graben",
            List.of("Erde, Gras, Sand, Kies, Ton, Schlamm und Seelensand graben", "Selbst gesetzte Blöcke zählen nicht")),
    FARMER("Farmer", "Farmer", Material.IRON_HOE, "<yellow>", "Reife Pflanzen ernten",
            List.of("Reifen Weizen, Karotten, Kartoffeln, Rote Bete, Netherwarzen, Kakao ernten",
                    "Melonen, Kürbisse, Zuckerrohr, Kakteen und Bambus ernten")),
    JAEGER("Jäger", "Jaeger", Material.BOW, "<red>", "Monster und wilde Tiere jagen",
            List.of("Monster besiegen (Enderman, Hexen, Lohen zahlen mehr)", "Wilde Tiere jagen",
                    "Mobs aus Spawnern und gezüchtete Tiere zählen nicht")),
    ANGLER("Angler", "Angler", Material.FISHING_ROD, "<aqua>", "Fische fangen",
            List.of("Fische und Schätze aus dem Wasser ziehen")),
    ZUECHTER("Züchter", "Zuechter", Material.WHEAT, "<#E8A0C8>", "Tiere züchten und scheren",
            List.of("Tiere züchten", "Schafe scheren")),
    VERZAUBERER("Verzauberer", "Verzauberer", Material.ENCHANTING_TABLE, "<light_purple>", "Gegenstände verzaubern",
            List.of("Am Zaubertisch verzaubern: je mehr Stufen, desto mehr Lohn"));

    public final String display;
    /** Name ohne Umlaute (für die Survival-Leiste, die nur ASCII kennt, und für Befehle). */
    public final String ascii;
    public final Material icon;
    public final String color;
    public final String desc;
    public final List<String> tasks;

    Job(String display, String ascii, Material icon, String color, String desc, List<String> tasks) {
        this.display = display;
        this.ascii = ascii;
        this.icon = icon;
        this.color = color;
        this.desc = desc;
        this.tasks = tasks;
    }

    public static Job of(String name) {
        if (name == null) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Sucht einen Job nach Namen, egal ob mit Umlauten, ohne oder in welcher Schreibweise. */
    public static Job find(String name) {
        if (name == null) return null;
        for (Job j : values()) {
            if (j.name().equalsIgnoreCase(name) || j.ascii.equalsIgnoreCase(name) || j.display.equalsIgnoreCase(name)) return j;
        }
        return null;
    }
}
