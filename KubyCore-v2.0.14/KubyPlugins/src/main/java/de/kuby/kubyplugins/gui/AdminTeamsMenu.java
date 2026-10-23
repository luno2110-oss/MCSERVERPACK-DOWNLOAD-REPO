package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TeamService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

public final class AdminTeamsMenu extends AdminBase {

    public AdminTeamsMenu(KubyPlugins plugin) {
        super(plugin, "Teams", 5);
    }

    @Override
    protected void build(Player p) {
        int slot = 9;
        for (TeamService.Team t : plugin.teams().all()) {
            if (slot > 35) break;
            OfflinePlayer leader = Bukkit.getOfflinePlayer(t.leader());
            String leaderName = leader.getName() == null ? "?" : leader.getName();
            set(slot++, Items.make(Material.WHITE_BANNER, "<gold>" + t.name(), List.of(
                            "<gray>Chef: <white>" + leaderName,
                            "<gray>Mitglieder: <white>" + t.members().size(),
                            "<gray>Kasse: <gold>" + Msg.coins(t.bank()) + "$",
                            "<red>Klick: Team auflösen")),
                    e -> {
                        String err = plugin.teams().adminDisband(t);
                        done(p, err == null ? "<gray>Team aufgelöst." : "<red>" + err);
                    });
        }
        adminBack(p);
    }
}
