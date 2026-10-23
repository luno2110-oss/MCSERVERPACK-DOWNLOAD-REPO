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
            // Münz- und XP-Icon aus dem Pack KubyUI, ohne Pack nur Text.
            boolean icons = plugin.uiPack().active(p);
            String coin = icons ? "<font:kubyplugins:sb>\uE010</font> " : "";
            String star = icons ? "<font:kubyplugins:sb>\uE012</font> " : "<#ff9a1f>XP ";
            p.sendActionBar(Msg.parse(coin + plugin.moneyText(p)
                    + " <dark_gray>|</dark_gray> " + star + "<#ff9a1f>" + Msg.coins(xp)));
        }
    }
}
