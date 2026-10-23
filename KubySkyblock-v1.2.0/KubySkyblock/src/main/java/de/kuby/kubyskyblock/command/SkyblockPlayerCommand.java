package de.kuby.kubyskyblock.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyskyblock.gui.SkyblockMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /skyblock: Checkliste und Erfolge. */
public final class SkyblockPlayerCommand implements CommandExecutor {

    private final KubyPlugins core;

    public SkyblockPlayerCommand(KubyPlugins core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        new SkyblockMenu(core).open(p);
        return true;
    }
}
