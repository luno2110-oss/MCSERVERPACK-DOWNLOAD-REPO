package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /casino: Chips kaufen und verkaufen, Jackpot ansehen.
 * Admins: /casino kasse (auf einen Block schauen), /casino jackpot <Betrag>.
 */
public final class CasinoCommand implements CommandExecutor {

    private final KubyPlugins plugin;

    public CasinoCommand(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "status" -> status(p);
            case "kaufen" -> trade(p, args, true);
            case "verkaufen" -> trade(p, args, false);
            case "jackpot" -> jackpot(p, args);
            case "kasse" -> kasse(p);
            case "tisch" -> tisch(p);
            case "hit" -> answer(p, plugin.blackjack().hit(p.getUniqueId()));
            case "stand" -> answer(p, plugin.blackjack().stand(p.getUniqueId()));
            case "setzen" -> rouletteBet(p, args);
            case "roulette-tisch" -> rouletteTable(p);
            case "automat" -> slotsMachine(p);
            case "crash" -> crashMachine(p);
            default -> Msg.send(p, "<red>Nutzung: /casino [status|kaufen <Anzahl>|verkaufen <Anzahl>|jackpot|hit|stand|setzen <Einsatz> <rot|schwarz|0-36>]");
        }
        return true;
    }

    private void status(Player p) {
        Msg.send(p, "<gold>Casino <gray>| <white>Chips: <yellow>" + plugin.casino().chips(p.getUniqueId())
                + " <gray>| Preis je Chip: <white>" + Msg.coins(plugin.casino().price()) + "$");
        Msg.send(p, "<gold>JACKPOT <yellow>" + Msg.coins(plugin.casino().jackpot()) + "$");
    }

    private void trade(Player p, String[] args, boolean buy) {
        if (args.length < 2) {
            Msg.send(p, "<red>Nutzung: /casino " + (buy ? "kaufen" : "verkaufen") + " <Anzahl>");
            return;
        }
        long count;
        try {
            count = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültige Anzahl.");
            return;
        }
        String blocked = plugin.casino().checkUse(p);
        if (blocked != null) {
            Msg.send(p, "<red>" + blocked);
            return;
        }
        String err = buy ? plugin.casino().buy(p, count) : plugin.casino().sell(p, count);
        if (err != null) {
            Msg.send(p, "<red>" + err);
            return;
        }
        Msg.send(p, (buy ? "<green>Gekauft: " : "<green>Verkauft: ") + count + " Chips");
    }

    private void jackpot(Player p, String[] args) {
        if (args.length < 2) {
            Msg.send(p, "<gold>JACKPOT <yellow>" + Msg.coins(plugin.casino().jackpot()) + "$");
            return;
        }
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können den Jackpot setzen.");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültiger Betrag.");
            return;
        }
        if (amount < 0) {
            Msg.send(p, "<red>Der Jackpot kann nicht negativ sein.");
            return;
        }
        plugin.casino().addJackpot(amount - plugin.casino().jackpot());
        Msg.send(p, "<green>Jackpot gesetzt auf " + Msg.coins(amount) + "$.");
    }

    private void kasse(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können den Kassentisch setzen.");
            return;
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null) {
            Msg.send(p, "<red>Schau auf den Block, der der Kassentisch wird.");
            return;
        }
        Location at = target.getLocation();
        plugin.casino().setKasse(at);
        Msg.send(p, "<green>Kassentisch gesetzt. Der Jackpot wird darüber angezeigt.");
    }

    private void tisch(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können den Blackjack-Tisch setzen.");
            return;
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null) {
            Msg.send(p, "<red>Schau auf den Block, der der Blackjack-Tisch wird.");
            return;
        }
        plugin.casino().setTable(target.getLocation());
        Msg.send(p, "<green>Blackjack-Tisch gesetzt. Rechtsklick auf den Block startet eine Runde.");
    }

    private void answer(Player p, String err) {
        if (err != null) Msg.send(p, "<red>" + err);
    }

    private void rouletteBet(Player p, String[] args) {
        if (args.length < 3) {
            Msg.send(p, "<red>Nutzung: /casino setzen <Einsatz> <rot|schwarz|0-36>");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Ungültiger Einsatz.");
            return;
        }
        String blocked = plugin.roulette().checkTable(p);
        if (blocked != null) {
            Msg.send(p, "<red>" + blocked);
            return;
        }
        String err = plugin.roulette().bet(p, args[2], amount);
        if (err != null) {
            Msg.send(p, "<red>" + err);
            return;
        }
        Msg.send(p, "<green>Einsatz gesetzt: " + amount + " Chip(s) auf " + args[2] + ".");
    }

    private void rouletteTable(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können den Roulette-Tisch setzen.");
            return;
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null) {
            Msg.send(p, "<red>Schau auf den Block, der der Roulette-Tisch wird.");
            return;
        }
        plugin.roulette().setTable(target.getLocation());
        Msg.send(p, "<green>Roulette-Tisch gesetzt.");
    }

    private void slotsMachine(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können den Slots-Automaten setzen.");
            return;
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null) {
            Msg.send(p, "<red>Schau auf den Block, der der Slots-Automat wird (am besten ein Hebel).");
            return;
        }
        plugin.slots().setMachine(target.getLocation());
        Msg.send(p, "<green>Slots-Automat gesetzt. Rechtsklick auf den Block startet eine Drehung.");
    }

    private void crashMachine(Player p) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können den Crash-Automaten setzen.");
            return;
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null) {
            Msg.send(p, "<red>Schau auf den Block, der der Crash-Automat wird.");
            return;
        }
        plugin.crash().setMachine(target.getLocation());
        Msg.send(p, "<green>Crash-Automat gesetzt. Rechtsklick steigt ein, während der Runde aus.");
    }
}
