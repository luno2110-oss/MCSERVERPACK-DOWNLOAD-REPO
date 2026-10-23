package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Hauptmenü des Skyblock-Modus: Checkliste, Erfolge, Überblick. */
public final class SkyblockMenu extends de.kuby.kubyplugins.gui.GuiMenu {

    public SkyblockMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>Skyblock"), 3);
    }

    @Override
    protected void build(Player p) {
        var prog = KubySkyblock.get().progress();
        for (int i = 0; i < 27; i++) set(i, Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()), null);
        set(4, Items.make(Material.COMPASS, "<gold>Dein Fortschritt", List.of(
                "<gray>Checkliste: <white>" + prog.percent(p.getUniqueId()) + "%",
                "<gray>Erfolge: <white>" + prog.achieved(p.getUniqueId()).size() + "<dark_gray>/<white>"
                        + prog.achievements().size())), null);
        set(11, Items.make(Material.BOOK, "<aqua>Checkliste", List.of(
                "<gray>Alle Items, die es in Skyblock gibt",
                "<yellow>Klick: öffnen")), e -> new ChecklistMenu(plugin, 0).open(p));
        set(15, Items.make(Material.NETHER_STAR, "<gold>Erfolge", List.of(
                "<gray>Blöcke, Items, One-Block und Loot",
                "<yellow>Klick: öffnen")), e -> new AchievementMenu(plugin).open(p));
    }
}
