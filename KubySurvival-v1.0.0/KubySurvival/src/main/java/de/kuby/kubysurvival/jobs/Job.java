package de.kuby.kubysurvival.jobs;

import org.bukkit.Material;

/** Die Survival-Jobs. Nur der aktive Job bringt XP und Coins. */
public enum Job {
    HOLZFAELLER("Holzfäller", "Holzfaeller", Material.IRON_AXE, "<#C8884A>", "Baumstämme fällen"),
    MINER("Miner", "Miner", Material.IRON_PICKAXE, "<gray>", "Stein und Erze abbauen"),
    FARMER("Farmer", "Farmer", Material.IRON_HOE, "<yellow>", "Reife Pflanzen ernten"),
    JAEGER("Jäger", "Jaeger", Material.BOW, "<red>", "Monster und wilde Tiere jagen"),
    ANGLER("Angler", "Angler", Material.FISHING_ROD, "<aqua>", "Fische fangen");

    public final String display;
    /** Name ohne Umlaute (für die Survival-Leiste, die nur ASCII kennt). */
    public final String ascii;
    public final Material icon;
    public final String color;
    public final String desc;

    Job(String display, String ascii, Material icon, String color, String desc) {
        this.display = display;
        this.ascii = ascii;
        this.icon = icon;
        this.color = color;
        this.desc = desc;
    }

    public static Job of(String name) {
        if (name == null) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
