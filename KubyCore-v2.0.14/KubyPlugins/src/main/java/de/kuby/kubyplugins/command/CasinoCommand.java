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
public final class CasinoCommand implements CommandExecutor, org.bukkit.command.TabCompleter {

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
            case "werfen" -> {
                String err = plugin.roulette().throwBall(p);
                if (err != null) Msg.send(p, "<red>" + err);
            }
            case "waehrung", "währung", "juwelen" -> {
                de.kuby.kubyplugins.service.CasinoPlay cp = plugin.casinoPlay();
                de.kuby.kubyplugins.service.CasinoPlay.Cur want = args.length > 1
                        ? (args[1].toLowerCase().startsWith("j") ? de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS : de.kuby.kubyplugins.service.CasinoPlay.Cur.CHIPS)
                        : (cp.mode(p.getUniqueId()) == de.kuby.kubyplugins.service.CasinoPlay.Cur.CHIPS ? de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS
                        : de.kuby.kubyplugins.service.CasinoPlay.Cur.CHIPS);
                String err = cp.setMode(p, want);
                if (err != null) {
                    Msg.send(p, "<red>" + err);
                } else if (want == de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS) {
                    Msg.send(p, "<#7FE3FF>◆ Du spielst jetzt mit Juwelen <gray>(nur " + cp.jewelGamesLabel() + ", alle anderen Spiele bleiben bei Chips). Einsatz " + cp.jewelMin() + " bis " + cp.jewelMax()
                            + " je Runde, " + cp.volumePeriodLabel() + " noch " + cp.volumeLeft(p.getUniqueId()) + " Juwelen Spielvolumen.");
                } else {
                    Msg.send(p, "<#F2C94C>● Du spielst jetzt mit Chips.");
                }
            }
            case "bonus", "serie" -> {
                de.kuby.kubyplugins.service.CasinoPlay cp = plugin.casinoPlay();
                int st = cp.streak(p.getUniqueId());
                Msg.send(p, "<gold><b>CASINO-BONUS</b>");
                Msg.send(p, "<gray>Siegesserie: <#FF8A3D>" + st + "</#FF8A3D> · nächster Sieg zahlt <white>x"
                        + de.kuby.kubyplugins.service.CasinoPlay.fmt(cp.streakMult(st)) + "</white> · beste Serie <white>" + cp.bestStreak(p.getUniqueId()));
                Msg.send(p, "<gray>" + cp.goldenLabel());
                Msg.send(p, "<gray>Niederlagen in Folge: <white>" + cp.lossStreak(p.getUniqueId()) + "</white> (ab 3 gibt der nächste Sieg einen Comeback-Bonus), "
                        + "jeder Verlust gibt Cashback zurück.");
                Msg.send(p, "<gray>Währung: <white>" + (cp.mode(p.getUniqueId()) == de.kuby.kubyplugins.service.CasinoPlay.Cur.JEWELS ? "Juwelen (nur " + cp.jewelGamesLabel() + ")" : "Chips")
                        + (cp.vip(p) ? " <dark_gray>· <gray>wechseln: <white>/casino waehrung" : ""));
            }
            case "kaufen", "buy" -> trade(p, args, true);
            case "verkaufen", "sell" -> trade(p, args, false);
            case "hilfe" -> CasinoRules.open(plugin, p, null);
            case "admin" -> de.kuby.kubyplugins.service.CasinoAdmin.open(plugin, p);
            case "stats" -> CasinoRules.openStats(plugin, p);
            case "poker-tisch" -> {
                if (!p.hasPermission("kuby.admin")) {
                    Msg.send(p, "<red>Nur Admins können den Poker-Tisch setzen.");
                } else {
                    de.kuby.kubyplugins.casino.TableTier tier = args.length > 1 && de.kuby.kubyplugins.casino.TableTier.parse(args[1]) != null
                            ? de.kuby.kubyplugins.casino.TableTier.parse(args[1]) : de.kuby.kubyplugins.casino.TableTier.GRUEN;
                    de.kuby.kubyplugins.service.PokerService pk = switch (tier) {
                        case GRUEN -> plugin.poker();
                        case ROT -> plugin.pokerHr();
                        case VIOLETT -> plugin.pokerVio();
                    };
                    pk.setTable(p.getLocation());
                    Msg.send(p, "<green>Poker-Tisch (" + tier.key + ") aufgebaut: Mitte da, wo du stehst,"
                            + " der Dealer an der Seite, auf die du schaust (Tisch 5,4 x 2,7 Blöcke, sechs Plätze). Big Blind "
                            + tier.minBet(plugin) + ", Buy-in bis " + tier.maxBet(plugin) + " Chips."
                            + " Andere Farben: /casino poker-tisch gruen|rot|violett");
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
            case "shop", "chipshop", "chip-shop" -> plugin.chipShop().open(p);
            case "rundgang", "tour" -> {
                if (!plugin.casinoTour().play(p)) Msg.send(p, "<red>Hier gibt es nichts zu zeigen – stell dich ins Casino (im Umkreis von 90 Blöcken).");
            }
            case "npc" -> plugin.casinoNpc().command(p, args);
            case "einrichten", "setup" -> plugin.casinoSetup().command(p, args);
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
            case "plinko" -> plugin.plinko().command(p, args);
            case "rennen", "pferderennen" -> plugin.race().command(p, args);
            case "crash" -> plugin.crash().command(p, args);
            case "holo" -> plugin.casinoHolos().command(p, args);
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

    /**
     * /casino roulette-tisch [entfernen]: Las-Vegas-Tisch vor dir. Du stehst auf der Spielerseite und schaust zum Croupier;
     * die Tischmitte liegt 2 Blöcke vor dir, der Kessel links, Platzbedarf 6,3 x 3,5 Blöcke (mit Croupier und Tafel).
     */
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
        float yaw = Math.round(p.getLocation().getYaw() / 90f) * 90f;
        double r = Math.toRadians(yaw);
        Location feet = p.getLocation();
        Location center = new Location(feet.getWorld(), feet.getBlockX() + 0.5 - Math.sin(r) * 2.0, feet.getY(),
                feet.getBlockZ() + 0.5 + Math.cos(r) * 2.0);
        plugin.roulette().setTable(center, yaw);
        Msg.send(p, "<green>Roulette-Tisch (Las Vegas) aufgebaut: Kessel links, Croupier gegenüber. Gesetzt wird per Rechtsklick "
                + "aufs Tableau, Linksklick nimmt Chips zurück, Schleichen + Rechtsklick öffnet das Menü.");
    }


    /** Tab-Ergänzung: Unterbefehle, für Admins auch Tisch-Aufbau mit Farbe (grün, rot, violett). */
    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("kuby.admin");
        java.util.List<String> opts = new java.util.ArrayList<>();
        if (args.length == 1) {
            opts.addAll(java.util.List.of("status", "kaufen", "verkaufen", "waehrung", "serie", "jackpot", "regeln", "stats", "rundgang",
                    "cashout", "hilfe", "plinko", "rennen"));
            if (admin) opts.addAll(java.util.List.of("blackjack", "poker-tisch", "roulette-tisch", "automat", "slots", "crash", "npc",
                    "holo", "kasse", "barkeeper", "admin", "einrichten"));
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("rennen")) {
            opts.addAll(java.util.List.of("bildschirm", "entfernen"));
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("plinko")) {
            opts.addAll(java.util.List.of("automat", "entfernen", "liste"));
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("blackjack")) {
            opts.addAll(java.util.List.of("tisch", "entfernen", "liste"));
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("einrichten")) {
            opts.addAll(java.util.List.of("ok", "spawn", "neu", "abbrechen"));
        } else if (admin && args.length == 2 && args[0].equalsIgnoreCase("poker-tisch")) {
            opts.addAll(java.util.List.of("gruen", "rot", "violett"));
        } else if (admin && args.length >= 3 && args[0].equalsIgnoreCase("blackjack") && args[1].equalsIgnoreCase("tisch")) {
            opts.addAll(java.util.List.of("gruen", "rot", "violett", "3"));
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        opts.removeIf(o -> !o.startsWith(last));
        return opts;
    }
}
