package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

public final class AdminPlayersMenu extends AdminBase {

    public AdminPlayersMenu(KubyPlugins plugin) {
        super(plugin, "Spieler", 5);
    }

    @Override
    protected void build(Player p) {
        int slot = 9;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (slot > 35) break;
            set(slot++, Items.head(other, "<white>" + other.getName(), List.of(
                            "<gray>Klick: Details und Aktionen")),
                    e -> new AdminPlayerMenu(plugin, other.getUniqueId(), other.getName()).open(p));
        }
        set(38, Items.make(Material.COMPASS, "<aqua>Spieler suchen", List.of(
                        "<gray>Auch Offline-Spieler, die schon mal da waren")),
                e -> plugin.prompts().ask(p, "Spielername eingeben:", name -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
                    if (op == null) {
                        Msg.send(p, "<red>Spieler unbekannt.");
                        new AdminPlayersMenu(plugin).open(p);
                        return;
                    }
                    new AdminPlayerMenu(plugin, op.getUniqueId(), name).open(p);
                }));
        adminBack(p);
    }
}
