package de.kuby.kubyplugins.gui;

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

public final class LeaderboardMenu extends GuiMenu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

    public LeaderboardMenu(KubyPlugins plugin) {
        super(plugin, Msg.parse("<dark_gray>" + SmallCaps.of("Rangliste")), 4);
    }

    @Override
    protected void build(Player p) {
        List<Map.Entry<UUID, Long>> top = plugin.economy().top(SLOTS.length);
        for (int i = 0; i < top.size(); i++) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(top.get(i).getKey());
            String name = op.getName() == null ? "Unbekannt" : op.getName();
            long value = top.get(i).getValue();
            set(SLOTS[i], Items.head(op, "<gold>#" + (i + 1) + " " + name, List.of(
                    "<gray>Vermögen: <gold>" + Msg.coins(value) + "$")), null);
        }
        addBack(p);
    }
}
