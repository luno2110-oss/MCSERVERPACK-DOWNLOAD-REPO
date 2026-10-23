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
                "Im Casino spielst du mit Chips an Slots, Roulette, Blackjack und Crash.",
                "Chips tauschst du an der Kasse gegen Coins und wieder zurück. 1 Chip = "
                        + Msg.coins(plugin.casino().price()) + " Coins.",
                "Von jedem Einsatz geht 1 % in den Jackpot. Er schwebt über der Kasse. Drei Siebener bei Slots knacken ihn.",
                "Das Casino gibt es nur in Survival.",
                "Schleichen + Rechtsklick auf einen Tisch zeigt die Regeln dieses Spiels.");
        List<String[]> commands = List.of(
                new String[]{"/casino", "Ins Casino"},
                new String[]{"/casino buy <Anzahl>", "Chips kaufen"},
                new String[]{"/casino sell <Anzahl>", "Chips eintauschen"},
                new String[]{"/casino status", "Deine Chips und der Jackpot"},
                new String[]{"/casino regeln", "Diese Hilfe"});
        List<ActionButton> buttons = new ArrayList<>();
        for (String g : GAMES) {
            buttons.add(button("<white>" + title(g), "Regeln: " + title(g), 100,
                    pl -> pl.showDialog(gamePage(plugin, g))));
        }
        buttons.add(button("<red><b>Hinreisen</b>", "Zum Casino teleportieren", 100,
                pl -> plugin.casino().travel(pl)));
        return build("CASINO", "Slots, Roulette, Blackjack und Crash", how, commands, buttons);
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
                        "7 7 7: 100-fach und der Jackpot.");
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

    private static String title(String game) {
        return switch (game) {
            case "blackjack" -> "Blackjack";
            case "roulette" -> "Roulette";
            case "slots" -> "Slots";
            default -> "Crash";
        };
    }
}
