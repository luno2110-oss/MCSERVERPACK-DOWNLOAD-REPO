package de.kuby.kubysmp.command;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubysmp.gui.DuelMenu;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DuelCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public DuelCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (args.length == 0) {
            new DuelMenu(plugin).open(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("accept")) {
            String err = KubySMP.get().duels().accept(p);
            if (err != null) Msg.send(p, "<red>" + err);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Msg.send(p, "<red>Spieler nicht online.");
            return true;
        }
        long bet = 0;
        if (args.length > 1) {
            try {
                bet = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                Msg.send(p, "<red>Ungültiger Einsatz.");
                return true;
            }
        }
        String err = KubySMP.get().duels().challenge(p, target, bet);
        if (err != null) {
            Msg.send(p, "<red>" + err);
        } else {
            Msg.send(p, "<green>Herausforderung an <white>" + target.getName() + " <green>gesendet.");
        }
        return true;
    }
}
