package de.kuby.kubysmp.command;

import org.bukkit.block.Block;

import de.kuby.kubysmp.gui.InsuranceMenu;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysmp.KubySMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class VersichernCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public VersichernCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        versichern(p, args);
        return true;
    }

    private void versichern(Player p, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("liste")) {
            new InsuranceMenu(plugin).open(p);
            return;
        }
        Block target = p.getTargetBlockExact(5);
        if (target == null) {
            Msg.send(p, "<red>Schau auf eine Kiste, die du versichern willst.");
            return;
        }
        String err = KubySMP.get().insurance().insure(p, target);
        if (err != null) {
            Sfx.play(plugin, p, de.kuby.kubyplugins.util.Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
        } else {
            Sfx.play(plugin, p, de.kuby.kubyplugins.util.Sfx.Kind.SUCCESS);
        }
    }

}
