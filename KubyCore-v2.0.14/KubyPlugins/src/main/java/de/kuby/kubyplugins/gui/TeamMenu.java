package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class TeamMenu extends GuiMenu {

    public TeamMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Team")), 4);
    }

    @Override
    protected void build(Player p) {
        TeamService.Team t = plugin.teams().of(p.getUniqueId());

        if (t == null) {
            long cost = plugin.getConfig().getLong("team.create-cost", 1000);
            set(13, Items.make(Material.WHITE_BANNER, "<green>" + SmallCaps.of("Kein Team"), List.of(
                    "<gray>Gründe eins mit <white>/team create <Name>",
                    "<gray>Kosten: <gold>" + Msg.coins(cost) + "$",
                    "<gray>Einladungen kommen per <white>/team invite")), null);
            addBack(p);
            return;
        }

        boolean leader = t.leader().equals(p.getUniqueId());

        set(4, Items.make(Material.WHITE_BANNER, "<gold>" + SmallCaps.of(t.name()), List.of(
                "<gray>Mitglieder: <white>" + t.members().size() + "<gray>/<white>" + plugin.teams().maxMembers(),
                "<gray>Teamkasse: <gold>" + Msg.coins(t.bank()) + "$")), null);

        int slot = 19;
        for (UUID id : t.members()) {
            if (slot > 25) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            boolean isLeader = id.equals(t.leader());
            set(slot++, Items.head(op, (isLeader ? "<gold>★ " : "<white>") + name, List.of(
                    isLeader ? "<gray>Teamchef" : "<gray>Mitglied")), null);
        }

        set(11, Items.make(Material.RED_BED, "<red>" + SmallCaps.of("Teamhome"), List.of(
                        "<gray>Klick: <white>teleportieren",
                        "<gray>Shift + Klick: <white>hier setzen")),
                e -> {
                    if (e.isShiftClick()) {
                        String err = plugin.teams().setHome(p);
                        Msg.send(p, err == null ? "<green>Teamhome gesetzt." : "<red>" + err);
                        open(p);
                        return;
                    }
                    Location loc = plugin.teams().homeOf(p.getUniqueId());
                    if (loc == null) {
                        Msg.send(p, "<red>Noch kein Teamhome. Shift + Klick setzt eins.");
                        return;
                    }
                    p.closeInventory();
                    Teleports.delayed(plugin, p, loc, t.name());
                });

        set(13, Items.make(Material.GOLD_INGOT, "<gold>" + SmallCaps.of("Teamkasse"), List.of(
                        "<gray>Linksklick: <white>100$ einzahlen",
                        "<gray>Shift + Klick: <white>1.000$ einzahlen")),
                e -> {
                    long amount = e.isShiftClick() ? 1000 : 100;
                    String err = plugin.teams().deposit(p, amount);
                    Msg.send(p, err == null ? "<green>Eingezahlt: <gold>" + Msg.coins(amount) + "$" : "<red>" + err);
                    open(p);
                });

        set(15, Items.make(Material.RED_DYE, "<red>" + SmallCaps.of(leader ? "Team auflösen" : "Team verlassen"), List.of(
                        "<gray>Shift + Klick zum Bestätigen")),
                e -> {
                    if (!e.isShiftClick()) return;
                    String err = leader ? plugin.teams().disband(p) : plugin.teams().leave(p.getUniqueId());
                    Msg.send(p, err == null ? "<gray>Du hast das Team verlassen oder aufgelöst." : "<red>" + err);
                    p.closeInventory();
                });

        addBack(p);
    }
}
