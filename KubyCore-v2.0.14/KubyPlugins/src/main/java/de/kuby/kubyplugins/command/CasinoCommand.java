package de.kuby.kubyplugins.command;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoRules;
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
        if (args.length == 0) {
            plugin.casino().travel(p);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> status(p);
            case "kaufen", "buy" -> trade(p, args, true);
            case "verkaufen", "sell" -> trade(p, args, false);
            case "hilfe" -> CasinoRules.open(plugin, p, null);
            case "admin" -> de.kuby.kubyplugins.service.CasinoAdmin.open(plugin, p);
            case "stats" -> CasinoRules.openStats(plugin, p);
            case "poker-tisch" -> {
                if (!p.hasPermission("kuby.admin")) {
                    Msg.send(p, "<red>Nur Admins können den Poker-Tisch setzen.");
                } else {
                    Block target = p.getTargetBlockExact(6);
                    if (target == null) {
                        Msg.send(p, "<red>Schau auf den Block, der der Poker-Tisch wird.");
                    } else {
                        plugin.poker().setTable(target.getLocation());
                        Msg.send(p, "<green>Poker-Tisch gesetzt. Hinsetzen mit /poker sitzen <Chips>.");
                    }
                }
            }
            case "barkeeper" -> {
                if (!p.hasPermission("kuby.admin")) {
                    Msg.send(p, "<red>Nur Admins können Barkeeper setzen.");
                } else if (args.length > 1 && args[1].equalsIgnoreCase("entfernen")) {
                    Msg.send(p, plugin.casinoVip().removeBarkeeper(p)
                            ? "<green>Barkeeper entfernt." : "<red>Kein Barkeeper im Umkreis von 5 Blöcken.");
                } else {
                    plugin.casinoVip().spawnBarkeeper(p);
                    Msg.send(p, "<green>Barkeeper gesetzt. Entfernen mit /casino barkeeper entfernen.");
                }
            }
            case "jackpot" -> jackpot(p, args);
            case "kasse" -> kasse(p);
            case "npc" -> plugin.casinoNpc().command(p, args);
            case "wechseln", "chips" -> {
                String blocked = plugin.casino().checkUse(p);
                if (blocked != null) Msg.send(p, "<red>" + blocked);
                else new de.kuby.kubyplugins.casino.KasseMenu(plugin).open(p);
            }
            case "tisch" -> Msg.send(p, "<gray>Blackjack-Tische baust du jetzt mit <white>/casino blackjack tisch [Radius]</white>.");
            case "hit", "karte" -> answer(p, plugin.blackjack().action(p, 0));
            case "stand", "halten" -> answer(p, plugin.blackjack().action(p, 1));
            case "blackjack" -> plugin.blackjack().command(p, args);
            case "setzen" -> rouletteBet(p, args);
            case "roulette-tisch" -> rouletteTable(p, args);
            case "automat" -> Msg.send(p, "<gray>Slot-Automaten baust du jetzt mit <white>/casino slots automat [Breite]</white>.");
            case "slots" -> plugin.slots().command(p, args);
            case "crash" -> plugin.crash().command(p, args);
            case "cashout", "auszahlen" -> {
                String err = plugin.crash().cashout(p);
                if (err != null) de.kuby.kubyplugins.casino.CasinoText.error(p, err);
            }
            case "regeln" -> {
                String game = args.length > 1 ? args[1].toLowerCase() : null;
                if (!CasinoRules.open(plugin, p, game)) {
                    Msg.send(p, "<red>Nutzung: /casino regeln [" + String.join("|", CasinoRules.GAMES) + "]");
                }
            }
            default -> Msg.send(p, "<red>Nutzung: /casino [status|buy <Anzahl>|sell <Anzahl>|jackpot|hit|stand|setzen <Einsatz> <rot|schwarz|0-36>|stats|regeln]");
        }
        return true;
    }

    private void status(Player p) {
        Msg.send(p, "<gold>Casino <gray>| <white>Chips: <yellow>" + Msg.coins(plugin.casino().chips(p.getUniqueId()))
                + " <gray>| 1 Chip = 1 Coin");
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
        Msg.send(p, (buy ? "<green>Gekauft: " : "<green>Ausgezahlt: ") + Msg.coins(count) + " Chips");
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
            Msg.send(p, "<red>Nutzung: /casino setzen <Einsatz> <rot|schwarz|gruen|gerade|ungerade|niedrig|hoch|0-36>");
            return;
        }
        long amount = de.kuby.kubyplugins.casino.CasinoInput.parse(args[1]);
        if (amount <= 0) {
            Msg.send(p, "<red>Ungültiger Einsatz.");
            return;
        }
        String blocked = plugin.casino().checkMode(p);
        String err = blocked != null ? blocked : plugin.roulette().betByName(p, args[2], amount);
        if (err != null) {
            Msg.send(p, "<red>" + err);
            return;
        }
        de.kuby.kubyplugins.casino.CasinoText.send(p, "Einsatz gesetzt: <#F2C94C>" + Msg.coins(amount) + " Chips</#F2C94C> auf "
                + Msg.escape(args[2]) + ".");
    }

    /** /casino roulette-tisch [breite] [tiefe]: Mitte = Block, auf den du schaust; Breite quer zu deinem Blick. */
    private void rouletteTable(Player p, String[] args) {
        if (!p.hasPermission("kuby.admin")) {
            Msg.send(p, "<red>Nur Admins können den Roulette-Tisch setzen.");
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("entfernen")) {
            plugin.roulette().removeTable();
            Msg.send(p, "<green>Anzeigen des Roulette-Tischs entfernt (bis zum nächsten Setzen).");
            return;
        }
        Block target = p.getTargetBlockExact(8);
        if (target == null) {
            Msg.send(p, "<red>Schau auf die Mitte der Tischplatte.");
            return;
        }
        double width = 10, depth = 8;
        try {
            if (args.length > 1) width = Double.parseDouble(args[1].replace(',', '.'));
            if (args.length > 2) depth = Double.parseDouble(args[2].replace(',', '.'));
        } catch (NumberFormatException e) {
            Msg.send(p, "<red>Nutzung: /casino roulette-tisch [Breite] [Tiefe]");
            return;
        }
        Location center = target.getLocation().add(0.5, 1.0, 0.5);
        if (Math.abs(target.getBoundingBox().getMaxY() - target.getY() - 1.0) > 0.01) {
            center.setY(target.getBoundingBox().getMaxY());
        }
        float yaw = Math.round(p.getLocation().getYaw() / 90f) * 90f;
        plugin.roulette().setTable(center, width, depth, yaw);
        Msg.send(p, "<green>Roulette-Tisch aufgebaut (" + width + " x " + depth + " Blöcke). Croupier: /casino npc croupier");
    }

}
