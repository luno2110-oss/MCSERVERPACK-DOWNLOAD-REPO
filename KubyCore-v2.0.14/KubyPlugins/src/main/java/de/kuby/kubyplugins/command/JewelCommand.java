package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.JewelShopMenu;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** /juwelen und /juwelenshop. /juwelen give ist für Admins. */
public final class JewelCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public JewelCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("juwelenshop")) {
            new de.kuby.kubyplugins.gui.JewelStoreMenu(plugin, plugin.modes().modeOf(p.getUniqueId()), 0).open(p);
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (!p.hasPermission("kuby.admin")) {
                Msg.send(p, "<red>Keine Berechtigung.");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            long amount;
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                Msg.send(p, "<red>Ungültige Menge.");
                return true;
            }
            if (target == null || amount <= 0) {
                Msg.send(p, "<red>Spieler offline oder Menge ungültig.");
                return true;
            }
            plugin.jewels().add(target.getUniqueId(), amount);
            Msg.send(p, "<green>" + amount + " Juwelen an " + target.getName() + " vergeben.");
            Msg.send(target, "<aqua>Du hast " + amount + " Juwelen erhalten!");
            return true;
        }
        Msg.send(p, "<gray>Deine Juwelen: <aqua>" + Msg.coins(plugin.jewels().balance(p.getUniqueId())));
        Msg.send(p, "<gray>Juwelen kaufst du mit <white>/juwelenshop");
        return true;
    }
}
