package de.kuby.kubysmp.command;

import java.util.Locale;

import de.kuby.kubysmp.gui.TournamentMenu;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubysmp.KubySMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TurnierCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public TurnierCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        turnier(p, args);
        return true;
    }

    private void turnier(Player p, String[] args) {
        if (args.length == 0) {
            new TournamentMenu(plugin).open(p);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> turnierReply(p, KubySMP.get().tournaments().join(p));
            case "leave" -> turnierReply(p, KubySMP.get().tournaments().leave(p.getUniqueId()));
            case "open", "start", "cancel" -> {
                if (!p.hasPermission("kuby.admin")) {
                    Msg.send(p, "<red>Keine Berechtigung.");
                    return;
                }
                String err = switch (sub) {
                    case "open" -> KubySMP.get().tournaments().open();
                    case "start" -> KubySMP.get().tournaments().start();
                    default -> KubySMP.get().tournaments().cancel();
                };
                turnierReply(p, err);
            }
            default -> Msg.send(p, "<red>Nutzung: /turnier [join|leave|open|start|cancel]");
        }
    }

    private void turnierReply(Player p, String error) {
        if (error != null) Msg.send(p, "<red>" + error);
    }

}
