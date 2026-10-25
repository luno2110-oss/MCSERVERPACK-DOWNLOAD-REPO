package de.kuby.kubysmp.command;

import org.bukkit.OfflinePlayer;

import java.util.UUID;
import org.bukkit.Bukkit;

import de.kuby.kubysmp.gui.BountiesMenu;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysmp.KubySMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BountyCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public BountyCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("bounties")) {
            new de.kuby.kubysmp.gui.BountiesMenu(plugin).open(p);
        } else {
            bounty(p, args);
        }
        return true;
    }

    private void bounty(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /bounty <Spieler> <Betrag>");
            return;
        }
        UUID target = resolve(args[0]);
        if (target == null) { Msg.send(p, "<red>Spieler nicht gefunden."); return; }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültiger Betrag.");
            return;
        }
        String err = KubySMP.get().bounties().place(p.getUniqueId(), target, amount);
        if (err != null) { Msg.send(p, "<red>" + err); return; }
        plugin.notifications().send("notify.duel", Msg.parse("<red>Kopfgeld! <gray>Auf <white>" + args[0] + " <gray>wurden "
                + "<gold>" + Msg.coins(amount) + "$ <gray>ausgesetzt (gesamt <gold>"
                + Msg.coins(KubySMP.get().bounties().of(target)) + "<gray>)."));
    }

    private static UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op == null ? null : op.getUniqueId();
    }
}
