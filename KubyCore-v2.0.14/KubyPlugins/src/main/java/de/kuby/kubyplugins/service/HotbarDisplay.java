package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.entity.Player;

/** Zeigt Geld (mit Zählanimation) und XP über der Hotbar. Im Handel wird die Anzeige nicht überschrieben. */
public final class HotbarDisplay {

    private final KubyPlugins plugin;

    public HotbarDisplay(KubyPlugins plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("display.hotbar", true)) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.trades().inTrade(p.getUniqueId())) continue;
            if (plugin.modes().isHub(p.getWorld())) continue;
            long xp = plugin.stats().get(p.getUniqueId()).xp;
            p.sendActionBar(Msg.parse("<font:kubyplugins:sb>\uE040</font> " + plugin.moneyText(p)
                    + " <dark_gray>|</dark_gray> <font:kubyplugins:sb>\uE041</font> <#ff9a1f>"
                    + Msg.coins(xp)));
        }
    }
}
