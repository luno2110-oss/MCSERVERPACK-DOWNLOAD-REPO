package de.kuby.kubysmp.command;

import de.kuby.kubyplugins.service.StatsService;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysmp.KubySMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TrophyCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public TrophyCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        trophies(p);
        return true;
    }

    private void trophies(Player p) {
        int t = plugin.stats().get(p.getUniqueId()).trophies;
        String next = t >= 3000 ? "Maximale Liga erreicht" : t >= 1500 ? "Diamant bei 3000"
                : t >= 500 ? "Gold bei 1500" : "Silber bei 500";
        Msg.send(p, "<gold>Trophäen: <white>" + t + " <dark_gray>| <gray>Liga: <white>" + StatsService.league(t));
        Msg.send(p, "<gray>" + next);
    }

}
