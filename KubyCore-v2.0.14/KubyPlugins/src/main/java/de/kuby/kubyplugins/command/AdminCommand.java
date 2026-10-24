package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.AdminMenu;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public AdminCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Keine Berechtigung.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("kittest")) {
            de.kuby.kubyplugins.ui.KitDemo.open(p, 0);
            return true;
        }
        new AdminMenu(plugin).open(p);
        return true;
    }
}
