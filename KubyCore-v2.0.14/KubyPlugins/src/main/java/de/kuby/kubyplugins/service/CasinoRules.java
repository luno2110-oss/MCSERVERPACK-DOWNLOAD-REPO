package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Casino-Hilfe als Dialog-Fenster im Stil der Hilfe-Referenz: Titel, Abschnitt "So geht's", Befehle und Buttons.
 * Die Übersicht verlinkt auf eine Seite je Spiel. Die Zahlen (Einsätze, Wartezeiten, Preis) kommen aus der config.yml.
 */
public final class CasinoRules {

    public static final List<String> GAMES = List.of("blackjack", "roulette", "slots", "crash");

    private static final int WIDTH = 320;

    private CasinoRules() {
    }

    /** Öffnet die Übersicht (game = null) oder die Seite eines Spiels. False, wenn es das Spiel nicht gibt. */
    public static boolean open(KubyPlugins plugin, Player p, String game) {
        if (game != null && !GAMES.contains(game)) return false;
        p.showDialog(game == null ? overview(plugin) : gamePage(plugin, game));
        return true;
    }

    private static Dialog overview(KubyPlugins plugin) {
        List<String> how = List.of(
                "Im Casino spielst du mit Chips an Slots, Roulette, Blackjack und Crash. In der VIP-Lounge gibt es Poker.",
                "Chips tauschst du an der Kasse gegen Coins und wieder zurück. 1 Chip = "
                        + Msg.coins(plugin.casino().price()) + " Coins.",
                "Jeden " + drawDay(plugin) + " um " + plugin.getConfig().getString("casino.jackpot.draw-time", "19:00")
                        + " Uhr wird der Jackpot ausgelost. Er wächst mit jedem Einsatz im Casino. Wer seit der letzten Ziehung"
                        + " einmal gespielt hat, ist dabei, und einer gewinnt den ganzen Topf.",
                "Im Casino läuft Musik. Mit /music suchst du dir Songs aus, das geht nur im Casino.",
                "In der VIP-Lounge geben dir die Barkeeper einmal am Tag "
                        + plugin.getConfig().getLong("casino.vip.daily-chips", 500) + " Chips. Rein darfst du mit dem Legend-Rang"
                        + " oder als einer der besten " + plugin.getConfig().getInt("casino.vip.top", 10)
                        + " in der Casino-Rangliste „Am meisten Plus“.",
                "In der VIP-Lounge steht ein Pokertisch: Texas Hold'em gegen andere Spieler, Blinds ab "
                        + plugin.getConfig().getLong("casino.poker.small-blind", 10) + "/" + plugin.getConfig().getLong("casino.poker.big-blind", 20)
                        + " Chips. Du setzt dich mit " + plugin.getConfig().getLong("casino.poker.min-buyin-bb", 40) + " bis "
                        + plugin.getConfig().getLong("casino.poker.max-buyin-bb", 200) + " Big Blinds hin und nimmst deinen Stack beim Aufstehen wieder mit.",
                "Das Casino gibt es nur in Survival.",
                "Schleichen + Rechtsklick auf einen Tisch zeigt die Regeln dieses Spiels.");
        List<String[]> commands = List.of(
                new String[]{"/casino", "Ins Casino"},
                new String[]{"/casino buy <Anzahl>", "Chips kaufen"},
                new String[]{"/casino sell <Anzahl>", "Chips eintauschen"},
                new String[]{"/casino stats", "Rangliste"},
                new String[]{"/music", "Casino-Musik"},
                new String[]{"/poker", "Poker in der VIP-Lounge"},
                new String[]{"/casino status", "Deine Chips und der Jackpot"},
                new String[]{"/casino regeln", "Diese Hilfe"});
        List<ActionButton> buttons = new ArrayList<>();
        for (String g : GAMES) {
            buttons.add(button("<white>" + title(g), "Regeln: " + title(g), 100,
                    pl -> pl.showDialog(gamePage(plugin, g))));
        }
        buttons.add(button("<white>Rangliste", "Am meisten Plus", 100, pl -> openStats(plugin, pl)));
        buttons.add(button("<red><b>Hinreisen</b>", "Zum Casino teleportieren", 100,
                pl -> plugin.casino().travel(pl)));
        return build("CASINO", "Slots, Roulette, Blackjack, Poker und Crash", how, commands, buttons);
    }

    /** Rangliste "Am meisten Plus": Top 10 und der eigene Platz. */
    public static void openStats(KubyPlugins plugin, Player p) {
        List<String> lines = new ArrayList<>();
        List<java.util.Map.Entry<java.util.UUID, Long>> top = plugin.casinoLog().top(10);
        if (top.isEmpty()) lines.add("Noch hat niemand gespielt.");
        for (int i = 0; i < top.size(); i++) {
            String name = org.bukkit.Bukkit.getOfflinePlayer(top.get(i).getKey()).getName();
            long net = top.get(i).getValue();
            lines.add((i + 1) + ". " + (name == null ? "?" : name) + "  " + (net >= 0 ? "+" : "") + net + " Chips");
        }
        int rank = plugin.casinoLog().rankOf(p.getUniqueId());
        long mine = plugin.casinoLog().netOf(p.getUniqueId());
        lines.add(rank == 0 ? "Du hast noch nicht gespielt."
                : "Dein Platz: " + rank + "  (" + (mine >= 0 ? "+" : "") + mine + " Chips)");
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("<white>< Casino", "Zurück zur Übersicht", 100, pl -> pl.showDialog(overview(plugin))));
        buttons.add(button("<red><b>Hinreisen</b>", "Zum Casino teleportieren", 100, pl -> plugin.casino().travel(pl)));
        p.showDialog(build("RANGLISTE", "Am meisten Plus: Gewinne minus Einsätze", lines, List.of(), buttons));
    }

    private static Dialog gamePage(KubyPlugins plugin, String game) {
        FileConfiguration c = plugin.getConfig();
        List<String> how;
        List<String[]> commands = new ArrayList<>();
        String subtitle;
        switch (game) {
            case "blackjack" -> {
                subtitle = "Näher an 21 als der Dealer";
                how = List.of(
                        "Rechtsklick auf den Tisch startet eine Runde. Einsatz: " + c.getLong("casino.blackjack.bet", 1) + " Chip(s).",
                        "Bildkarten zählen 10, Asse 1 oder 11. Der Dealer zieht bis 17.",
                        "Sieg: doppelter Einsatz. Blackjack: 2,5-fach. Gleichstand: Einsatz zurück.",
                        "Wer weggeht oder ausloggt, hält automatisch.");
                commands.add(new String[]{"/casino hit", "Karte ziehen"});
                commands.add(new String[]{"/casino stand", "Halten"});
            }
            case "roulette" -> {
                subtitle = "Europäisches Roulette, 0 bis 36";
                how = List.of(
                        "Setze am Tisch bis zu " + c.getLong("casino.roulette.max-bet", 50) + " Chips auf eine Zahl oder eine Farbe.",
                        "Nach " + c.getLong("casino.roulette.wait-seconds", 20) + " Sekunden dreht das Rad.",
                        "Zahl: 36-fach. Rot oder Schwarz: doppelt. Die 0 ist grün.");
                commands.add(new String[]{"/casino setzen <Einsatz> <rot|schwarz|0-36>", "Einsatz setzen"});
            }
            case "slots" -> {
                subtitle = "Drei gleiche gewinnen";
                how = List.of(
                        "Rechtsklick auf den Hebel dreht. Einsatz: " + c.getLong("casino.slots.bet", 1) + " Chip(s).",
                        "♣ 8-fach · ♦ 12-fach · ♥ 25-fach · ★ 60-fach",
                        plugin.getConfig().getBoolean("casino.jackpot.seven-wins", false)
                                ? "7 7 7: 100-fach und der Jackpot." : "7 7 7: 100-fach.");
            }
            default -> {
                subtitle = "Rechtzeitig aussteigen";
                how = List.of(
                        "Rechtsklick auf den Automaten steigt ein. Einsatz: " + c.getLong("casino.crash.bet", 10) + " Chips.",
                        "Nach " + c.getLong("casino.crash.wait-seconds", 10) + " Sekunden steigt der Multiplikator.",
                        "Rechtsklick steigt aus: Einsatz mal Multiplikator. Wer beim Crash drin ist, verliert.",
                        "Höchstens " + Math.round(c.getDouble("casino.crash.max-multiplier", 50)) + "x.");
            }
        }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("<white>< Casino", "Zurück zur Übersicht", 100, pl -> pl.showDialog(overview(plugin))));
        buttons.add(button("<red><b>Hinreisen</b>", "Zum Casino teleportieren", 100, pl -> plugin.casino().travel(pl)));
        return build(title(game).toUpperCase(), subtitle, how, commands, buttons);
    }

    private static Dialog build(String title, String subtitle, List<String> how, List<String[]> commands,
                                List<ActionButton> buttons) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Msg.parse("<gray>" + subtitle), WIDTH));
        StringBuilder sb = new StringBuilder("<red><b>SO GEHT'S</b></red>");
        for (String line : how) sb.append("\n<red>▪</red> <white>").append(Msg.escape(line)).append("</white>");
        body.add(DialogBody.plainMessage(Msg.parse(sb.toString()), WIDTH));
        if (!commands.isEmpty()) {
            StringBuilder cmds = new StringBuilder("<red><b>BEFEHLE</b></red>");
            for (String[] cmd : commands) {
                cmds.append("\n<aqua>").append(Msg.escape(cmd[0])).append("</aqua> <gray>").append(Msg.escape(cmd[1])).append("</gray>");
            }
            body.add(DialogBody.plainMessage(Msg.parse(cmds.toString()), WIDTH));
        }
        DialogBase base = DialogBase.builder(Msg.parse("<red><b>" + title + "</b>"))
                .canCloseWithEscape(true)
                .body(body)
                .build();
        return Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(2).build()));
    }

    private static ActionButton button(String label, String tooltip, int width, Consumer<Player> onClick) {
        return ActionButton.create(Msg.parse(label), Msg.parse("<gray>" + tooltip), width,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player pl) onClick.accept(pl);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));
    }

    private static String drawDay(KubyPlugins plugin) {
        return switch (plugin.getConfig().getString("casino.jackpot.draw-day", "SATURDAY").toUpperCase(java.util.Locale.ROOT)) {
            case "MONDAY" -> "Montag";
            case "TUESDAY" -> "Dienstag";
            case "WEDNESDAY" -> "Mittwoch";
            case "THURSDAY" -> "Donnerstag";
            case "FRIDAY" -> "Freitag";
            case "SUNDAY" -> "Sonntag";
            default -> "Samstag";
        };
    }

    private static String title(String game) {
        return switch (game) {
            case "blackjack" -> "Blackjack";
            case "roulette" -> "Roulette";
            case "slots" -> "Slots";
            default -> "Crash";
        };
    }
}
