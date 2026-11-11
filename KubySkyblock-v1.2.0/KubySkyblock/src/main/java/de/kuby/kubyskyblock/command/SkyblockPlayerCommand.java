package de.kuby.kubyskyblock.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyskyblock.gui.SkyblockMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /skyblock: in Skyblock Checkliste und Erfolge, sonst Wechsel nach Skyblock. */
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
        // außerhalb von Skyblock: in den Modus wechseln (wie /smp und /survival), in Skyblock: Checkliste und Erfolge
        if (!de.kuby.kubyplugins.service.ModeService.SKYBLOCK.equals(core.modes().modeOf(p.getUniqueId()))) {
            de.kuby.kubyplugins.command.ModeSwitchCommand.go(core, p, de.kuby.kubyplugins.service.ModeService.SKYBLOCK);
            return true;
        }
        new de.kuby.kubyskyblock.gui.SkyScreen(core).open(p);
        return true;
    }
}
