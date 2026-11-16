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
            Msg.send(p, "<gold>Poker <gray>(Texas Hold'em in der VIP-Lounge)");
            for (de.kuby.kubyplugins.casino.TableTier t : de.kuby.kubyplugins.casino.TableTier.values()) {
                long bb = t.minBet(plugin);
                Msg.send(p, "<gray>Tisch " + t.key + ": <white>Blinds " + Math.max(1, bb / 2) + "/" + bb + "<gray>, Buy-in bis <white>" + t.maxBet(plugin));
            }
            Msg.send(p, "<white>/poker sitzen <Chips> <gray>Hinsetzen am nächsten Tisch");
            Msg.send(p, "<white>/poker aufstehen <gray>Aufstehen, du nimmst deinen Stack mit");
            Msg.send(p, "<white>/poker fold | check | call | raise <Betrag> | allin <gray>Wenn du dran bist (oder Knöpfe auf dem Tisch)");
            Msg.send(p, "<white>/poker pause <gray>Hände aussetzen, Platz behalten · <white>/poker nachkaufen [Chips] <gray>zwischen den Händen"
                    + " · <white>/poker zeigen <gray>Karten nach einem Gewinn zeigen");
            return true;
        }
        String sub = args[0].toLowerCase();
        String err = switch (sub) {
            case "sitzen", "sit" -> {
                long bb = plugin.pokerFor(p).tier().minBet(plugin);
                long buyIn = bb * 100;
                if (args.length > 1) {
                    try {
                        buyIn = Long.parseLong(args[1]);
                    } catch (NumberFormatException e) {
                        yield "Ungültiger Betrag.";
                    }
                }
                int pos = -1;
                if (args.length > 2) {
                    try {
                        pos = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                yield plugin.pokerFor(p).sit(p, buyIn, pos);
            }
            case "aufstehen", "leave" -> plugin.pokerFor(p).stand(p.getUniqueId());
            case "fold", "check", "call", "allin" -> plugin.pokerFor(p).act(p, sub, 0);
            case "zeigen", "show" -> plugin.pokerFor(p).act(p, "show", 0);
            case "pause", "sitout" -> plugin.pokerFor(p).act(p, "sitout", 0);
            case "nachkaufen", "rebuy" -> {
                long bb = plugin.pokerFor(p).tier().minBet(plugin);
                long amt = bb * 100;
                if (args.length > 1) {
                    try {
                        amt = Long.parseLong(args[1]);
                    } catch (NumberFormatException e) {
                        yield "Ungültiger Betrag.";
                    }
                }
                yield plugin.pokerFor(p).topUp(p, amt);
            }
            case "raise" -> {
                if (args.length < 2) yield "Nutzung: /poker raise <Betrag> (Gesamteinsatz in dieser Runde)";
                try {
                    yield plugin.pokerFor(p).act(p, "raise", Long.parseLong(args[1]));
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
