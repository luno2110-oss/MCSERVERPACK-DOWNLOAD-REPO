package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /poker: hinsetzen, aufstehen und die Aktionen am Poker-Tisch (fold, check, call, raise, allin). */
public final class PokerCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public PokerCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        if (args.length == 0) {
            long bb = plugin.getConfig().getLong("casino.poker.big-blind", 20);
            Msg.send(p, "<gold>Poker <gray>(Texas Hold'em in der VIP-Lounge, Blinds "
                    + plugin.getConfig().getLong("casino.poker.small-blind", 10) + "/" + bb + ")");
            Msg.send(p, "<white>/poker sitzen <Chips> <gray>Hinsetzen ("
                    + bb * plugin.getConfig().getLong("casino.poker.min-buyin-bb", 40) + " bis "
                    + bb * plugin.getConfig().getLong("casino.poker.max-buyin-bb", 200) + " Chips)");
            Msg.send(p, "<white>/poker aufstehen <gray>Aufstehen, du nimmst deinen Stack mit");
            Msg.send(p, "<white>/poker fold | check | call | raise <Betrag> | allin <gray>Wenn du dran bist (auch per Klick)");
            return true;
        }
        String sub = args[0].toLowerCase();
        String err = switch (sub) {
            case "sitzen", "sit" -> {
                long bb = plugin.getConfig().getLong("casino.poker.big-blind", 20);
                long buyIn = bb * 100;
                if (args.length > 1) {
                    try {
                        buyIn = Long.parseLong(args[1]);
                    } catch (NumberFormatException e) {
                        yield "Ungültiger Betrag.";
                    }
                }
                yield plugin.poker().sit(p, buyIn);
            }
            case "aufstehen", "leave" -> plugin.poker().stand(p.getUniqueId());
            case "fold", "check", "call", "allin" -> plugin.poker().act(p, sub, 0);
            case "raise" -> {
                if (args.length < 2) yield "Nutzung: /poker raise <Betrag> (Gesamteinsatz in dieser Runde)";
                try {
                    yield plugin.poker().act(p, "raise", Long.parseLong(args[1]));
                } catch (NumberFormatException e) {
                    yield "Ungültiger Betrag.";
                }
            }
            default -> "Unbekannt. /poker zeigt die Befehle.";
        };
        if (err != null) Msg.send(p, "<red>" + err);
        return true;
    }
}
