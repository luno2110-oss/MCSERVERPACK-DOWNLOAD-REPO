package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** /coinflip <Betrag>: 50/50, Gewinn verdoppelt den Einsatz. */
public final class CoinflipCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public CoinflipCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (args.length < 1) {
            Msg.send(p, "<red>Nutzung: /coinflip <Betrag>");
            return true;
        }
        long bet;
        try {
            bet = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültiger Betrag.");
            return true;
        }
        long max = plugin.getConfig().getLong("coinflip.max-bet", 5000);
        if (bet <= 0 || bet > max) {
            Msg.send(p, "<red>Einsatz zwischen 1 und " + max + "$.");
            return true;
        }
        if (!plugin.economy().withdraw(p.getUniqueId(), bet)) {
            Msg.send(p, "<red>Nicht genug$.");
            return true;
        }
        if (ThreadLocalRandom.current().nextBoolean()) {
            plugin.economy().deposit(p.getUniqueId(), bet * 2);
            Msg.send(p, "<green>Gewonnen! <gold>+" + bet + "$"); Sfx.play(plugin, p, Sfx.Kind.WIN);
            // große Gewinne gehen an alle mit "Coinflip-Mitteilungen"
            if (bet >= plugin.getConfig().getLong("coinflip.announce-min", 2500)) {
                plugin.notifications().send("notify.coinflip", "<#FCC965><b>COINFLIP</b> <dark_gray>▶ <white>"
                        + p.getName() + " <gray>gewinnt <gold>" + Msg.coins(bet * 2) + "$<gray>! <dark_gray>(/coinflip)", o -> !o.equals(p));
            }
        } else {
            Msg.send(p, "<red>Verloren. <gray>-" + bet + "$"); Sfx.play(plugin, p, Sfx.Kind.LOSE);
        }
        return true;
    }
}
