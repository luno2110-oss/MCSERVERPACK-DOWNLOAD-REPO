package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.StatsService;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Trophäen-Rangliste mit Liga, Kills und Tode. */
public final class RankingMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

    public RankingMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Rangliste Trophäen")), 4);
    }

    @Override
    protected void build(Player p) {
        List<Map.Entry<UUID, StatsService.Stats>> top = plugin.stats().topTrophies(SLOTS.length);
        for (int i = 0; i < top.size(); i++) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(top.get(i).getKey());
            StatsService.Stats s = top.get(i).getValue();
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            set(SLOTS[i], Items.head(op, "<gold>#" + (i + 1) + " " + name, List.of(
                    "<gray>Liga: <white>" + StatsService.league(s.trophies),
                    "<gray>Trophäen: <gold>" + s.trophies,
                    "<gray>Kills: <white>" + s.kills + " <gray>/ Tode: <white>" + s.deaths)), null);
        }
        addBack(p);
    }
}
