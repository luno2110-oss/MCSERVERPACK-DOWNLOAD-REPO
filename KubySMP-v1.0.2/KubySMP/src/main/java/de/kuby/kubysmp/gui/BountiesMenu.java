package de.kuby.kubysmp.gui;

import org.bukkit.Material;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubyplugins.gui.GuiMenu;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Kopfgeld-Steckbriefe: Betrag, Kopf und die Position, wenn das Ziel online ist. */
public final class BountiesMenu extends GuiMenu {

    public BountiesMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Steckbriefe")), 5);
    }

    @Override
    protected void build(Player p) {
        Map<UUID, Long> all = KubySMP.get().bounties().allIn(plugin.modes().modeOf(p.getUniqueId()));
        int slot = 0;
        for (Map.Entry<UUID, Long> e : all.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .toList()) {
            if (slot >= 36) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            Player online = op.getPlayer();
            String location = online == null
                    ? "<gray>Position: <dark_gray>offline"
                    : "<gray>Position: <white>" + online.getWorld().getName() + " "
                      + online.getLocation().getBlockX() + " / " + online.getLocation().getBlockY()
                      + " / " + online.getLocation().getBlockZ();
            set(slot++, Items.head(op, "<red>" + SmallCaps.of("gesucht") + " <white>" + name, List.of(
                    "<gold>Kopfgeld: " + Msg.coins(e.getValue()) + "$",
                    location)), null);
        }
        if (slot == 0) {
            set(22, Items.make(org.bukkit.Material.BARRIER, "<gray>" + SmallCaps.of("Keine Steckbriefe"), List.of(
                    "<gray>/bounty <Spieler> <Betrag>")), null);
        }
        addBack(p);
    }
}
