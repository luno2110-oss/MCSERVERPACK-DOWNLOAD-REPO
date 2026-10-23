package de.kuby.kubyscoreboard;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.plugin.java.JavaPlugin;

/** Sidebar-Scoreboard als Addon von KubyPlugins. */
public final class KubyScoreboard extends JavaPlugin {

    @Override
    public void onEnable() {
        if (!(getServer().getPluginManager().getPlugin("KubyCore") instanceof KubyPlugins core)) {
            getLogger().severe("KubyCore fehlt oder ist nicht geladen. Addon wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        ScoreboardService service = new ScoreboardService(this, core);
        getServer().getPluginManager().registerEvents(new ScoreboardListener(this, service), this);
        core.setMoneyProvider(service::moneyText);
        getLogger().info("KubyScoreboard aktiv.");
    }
}
