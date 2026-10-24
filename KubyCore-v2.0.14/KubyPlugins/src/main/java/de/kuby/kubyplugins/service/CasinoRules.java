package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.Ui;
import de.kuby.kubyplugins.util.Msg;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Casino-Hilfe als Dialog-Fenster im Stil der Hilfe-Referenz: Kopf mit Icon, Abschnitt "So geht's", Befehle und Buttons.
 * Mit geladenem Grafik-Pack (KubyUI) kommen Rahmen, Icons und Buttons aus dem Pack, sonst die normale Ansicht.
 * Die Zahlen (Einsätze, Wartezeiten, Preis) kommen aus der config.yml.
 */
public final class CasinoRules {

    public static final List<String> GAMES = List.of("blackjack", "roulette", "slots", "crash");

    private static final int WIDTH = 320;
    private static final TextColor PINK = TextColor.color(0xFF5D73);
    private static final TextColor TITLE_RED = TextColor.color(0xFF4D67);
    private static final TextColor BODY = TextColor.color(0xE8E8E8);
    private static final TextColor SUB = TextColor.color(0xBDBDBD);
    private static final TextColor CMD = TextColor.color(0x5FE3C4);
    /** Platzhalter für Icons im Text. Ohne Grafik-Pack werden daraus normale Zeichen. */
    private static final Map<String, String> PLAIN_TOKENS = Map.of(
            "{club}", "♣", "{diamond}", "♦", "{heart}", "♥", "{star}", "★", "{seven}", "7");

    private record Btn(String label, String tooltip, boolean red, Consumer<Player> onClick) {}

    private record Page(String title, String subtitle, Ui.Icon icon, List<String> how, List<String[]> commands) {}

    /** Reihenfolge der Seiten zum Blättern (wie in der Hilfe-Referenz: "< vorherige | Hinreisen | nächste >"). */
    private static final List<String> ORDER = List.of("casino", "blackjack", "roulette", "slots", "crash", "rangliste");

    private CasinoRules() {
    }

    /** Öffnet die Übersicht (game = null) oder die Seite eines Spiels. False, wenn es das Spiel nicht gibt. */
    public static boolean open(KubyPlugins plugin, Player p, String game) {
        if (game != null && !GAMES.contains(game)) return false;
        show(plugin, p, game == null ? 0 : ORDER.indexOf(game));
        return true;
    }

    /** Rangliste "Am meisten Plus": Top 10 und der eigene Platz. */
    public static void openStats(KubyPlugins plugin, Player p) {
        show(plugin, p, ORDER.indexOf("rangliste"));
    }

    private static Page stats(KubyPlugins plugin, Player p) {
        List<String> lines = new ArrayList<>();
        List<Map.Entry<java.util.UUID, Long>> top = plugin.casinoLog().top(10);
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
        return new Page("RANGLISTE", "Am meisten Plus: Gewinne minus Einsätze", Ui.Icon.TROPHY, lines, List.of());
    }

    private static Page overview(KubyPlugins plugin) {
        List<String> how = List.of(
                "Im Casino spielst du mit Chips an Slots, Roulette, Blackjack und Crash. In der VIP-Lounge gibt es Poker.",
                "Chips tauschst du an der Kasse gegen Coins und wieder zurück. 1 Chip = 1 Coin, beim Auszahlen "
                        + plugin.getConfig().getInt("casino.cashout-tax-percent", 5) + " % Steuer.",
                "Jeden " + drawDay(plugin) + " um " + plugin.getConfig().getString("casino.jackpot.draw-time", "19:00")
                        + " Uhr wird der Jackpot ausgelost. Er wächst mit jedem Einsatz im Casino. Wer seit der letzten Ziehung"
                        + " einmal gespielt hat, ist dabei, und einer gewinnt den ganzen Topf.",
                "Im Casino läuft Musik. Mit /music suchst du dir Songs aus, das geht nur im Casino.",
                "In der VIP-Lounge geben dir die Barkeeper einmal am Tag "
                        + plugin.getConfig().getLong("casino.vip.daily-chips", 500) + " Chips. Rein darfst du mit dem Legend-Rang"
                        + " oder als einer der besten " + plugin.getConfig().getInt("casino.vip.top", 10)
                        + " in der Casino-Rangliste \"Am meisten Plus\".",
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
        return new Page("CASINO", "Slots, Roulette, Blackjack, Poker und Crash", Ui.Icon.DICE, how, commands);
    }

    private static Page gamePage(KubyPlugins plugin, String game) {
        FileConfiguration c = plugin.getConfig();
        List<String> how;
        List<String[]> commands = new ArrayList<>();
        String subtitle;
        Ui.Icon icon;
        switch (game) {
            case "blackjack" -> {
                subtitle = "Näher an 21 als der Dealer";
                icon = Ui.Icon.CARDS;
                how = List.of(
                        "Rechtsklick auf einen Hocker oder den Dealer: hinsetzen. Klick Chips aus dem Rack vor dir auf dein Feld ("
                                + c.getLong("casino.blackjack.min-bet", 10) + " bis " + c.getLong("casino.blackjack.max-bet", 10000)
                                + " Chips), der erste Einsatz startet die Runde.",
                        "Bildkarten zählen 10, Asse 1 oder 11. Bist du dran, klickst du Karte, Halten, Doppeln oder Teilen."
                                + " Der Dealer zieht bis 17.",
                        "Gewinn: 1:1. Blackjack: 3:2. Gleichstand: Einsatz zurück.",
                        "Aufstehen mit Schleichen. Wer mitten in der Runde aufsteht, hält automatisch.");
                commands.add(new String[]{"/casino karte", "Karte ziehen"});
                commands.add(new String[]{"/casino halten", "Halten"});
            }
            case "roulette" -> {
                subtitle = "Europäisches Roulette, 0 bis 36";
                icon = Ui.Icon.ROULETTE;
                how = List.of(
                        "Am Tisch oder beim Croupier öffnest du mit /roulette das Menü. Chip wählen, dann auf ein Feld klicken.",
                        "Der erste Einsatz startet die Runde, nach " + c.getLong("casino.roulette.wait-seconds", 20)
                                + " Sekunden läuft das Licht über die Zahlen und bleibt auf dem Ergebnis stehen.",
                        "Rot, Schwarz, Gerade, Ungerade, 1–18, 19–36: 1:1. Dutzend und Kolonne: 2:1. Zahl: 35:1.");
                commands.add(new String[]{"/roulette", "Roulette-Menü"});
                commands.add(new String[]{"/casino setzen <Einsatz> <rot|schwarz|0-36>", "Einsatz per Befehl"});
            }
            case "slots" -> {
                subtitle = "Drei gleiche gewinnen";
                icon = Ui.Icon.SLOTS;
                how = List.of(
                        "Rechtsklick auf den Hebel dreht. Einsatz: " + c.getLong("casino.slots.bet", 1) + " Chip(s).",
                        "{club} 8-fach   {diamond} 12-fach   {heart} 25-fach   {star} 60-fach",
                        c.getBoolean("casino.jackpot.seven-wins", false)
                                ? "{seven} {seven} {seven} 100-fach und der Jackpot." : "{seven} {seven} {seven} 100-fach.");
            }
            default -> {
                subtitle = "Rechtzeitig aussteigen";
                icon = Ui.Icon.CRASH;
                how = List.of(
                        "Rechtsklick auf den Automaten steigt ein. Einsatz: " + c.getLong("casino.crash.bet", 10) + " Chips.",
                        "Nach " + c.getLong("casino.crash.wait-seconds", 10) + " Sekunden steigt der Multiplikator.",
                        "Rechtsklick steigt aus: Einsatz mal Multiplikator. Wer beim Crash drin ist, verliert.",
                        "Höchstens " + Math.round(c.getDouble("casino.crash.max-multiplier", 50)) + "x.");
            }
        }
        return new Page(title(game).toUpperCase(), subtitle, icon, how, commands);
    }

    // ---------- Darstellung ----------

    /** Zeigt Seite index mit den drei Buttons darunter: vorherige Seite, Hinreisen, nächste Seite. */
    private static void show(KubyPlugins plugin, Player p, int index) {
        int size = ORDER.size();
        int i = Math.floorMod(index, size);
        Page page = switch (ORDER.get(i)) {
            case "casino" -> overview(plugin);
            case "rangliste" -> stats(plugin, p);
            default -> gamePage(plugin, ORDER.get(i));
        };
        int prev = Math.floorMod(i - 1, size);
        int next = Math.floorMod(i + 1, size);
        List<Btn> buttons = List.of(
                new Btn("< " + navName(prev), "Zurück: " + navName(prev), false, pl -> show(plugin, pl, prev)),
                new Btn("Hinreisen", "Zum Casino teleportieren", true, pl -> plugin.casino().travel(pl)),
                new Btn(navName(next) + " >", "Weiter: " + navName(next), false, pl -> show(plugin, pl, next)));
        p.showDialog(plugin.uiPack().active(p) ? textured(page, buttons) : plain(page, buttons));
    }

    private static String navName(int index) {
        String key = ORDER.get(index);
        return switch (key) {
            case "casino" -> "Casino";
            case "rangliste" -> "Rangliste";
            default -> title(key);
        };
    }

    /** Mit Grafik-Pack: Titelleiste, Kästen mit Rahmen und Icons, eigene Buttons. */
    private static Dialog textured(Page page, List<Btn> navButtons) {
        Ui.Panel head = new Ui.Panel()
                .headIcon(Ui.Icon.BACK, 8)
                .headIcon(page.icon(), 26)
                .text(48, page.title(), TITLE_RED, true)
                .text(48, page.subtitle(), SUB, false);
        Ui.Panel how = new Ui.Panel().text(7, "SO GEHT'S", PINK, true);
        for (String line : page.how()) how.bullet(line, BODY);
        List<Ui.Panel> panels = new ArrayList<>(List.of(head, how));
        if (!page.commands().isEmpty()) {
            Ui.Panel cmds = new Ui.Panel().text(7, "BEFEHLE", PINK, true);
            for (String[] cmd : page.commands()) cmds.columns(cmd[0], CMD, cmd[1], BODY, 120);
            panels.add(cmds);
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (Btn b : navButtons) buttons.add(button(Ui.button(b.label(), b.red()), b.tooltip(), b.onClick()));
        DialogBase base = DialogBase.builder(Ui.titleBar("Hilfe"))
                .canCloseWithEscape(true)
                .body(List.of(DialogBody.plainMessage(Ui.stack(panels), WIDTH)))
                .build();
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons).columns(3).build()));
    }

    /** Ohne Grafik-Pack: normale Texte. */
    private static Dialog plain(Page page, List<Btn> navButtons) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Msg.parse("<gray>" + page.subtitle()), WIDTH));
        StringBuilder sb = new StringBuilder("<red><b>SO GEHT'S</b></red>");
        for (String line : page.how()) {
            String text = line;
            for (Map.Entry<String, String> t : PLAIN_TOKENS.entrySet()) text = text.replace(t.getKey(), t.getValue());
            sb.append("\n<red>▪</red> <white>").append(Msg.escape(text)).append("</white>");
        }
        body.add(DialogBody.plainMessage(Msg.parse(sb.toString()), WIDTH));
        if (!page.commands().isEmpty()) {
            StringBuilder cmds = new StringBuilder("<red><b>BEFEHLE</b></red>");
            for (String[] cmd : page.commands()) {
                cmds.append("\n<aqua>").append(Msg.escape(cmd[0])).append("</aqua> <gray>").append(Msg.escape(cmd[1])).append("</gray>");
            }
            body.add(DialogBody.plainMessage(Msg.parse(cmds.toString()), WIDTH));
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (Btn b : navButtons) {
            String label = b.red() ? "<red><b>" + Msg.escape(b.label()) + "</b>" : "<white>" + Msg.escape(b.label());
            buttons.add(button(Msg.parse(label), b.tooltip(), b.onClick()));
        }
        DialogBase base = DialogBase.builder(Msg.parse("<red><b>" + page.title() + "</b>"))
                .canCloseWithEscape(true)
                .body(body)
                .build();
        return Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons).columns(3).build()));
    }

    private static ActionButton button(Component label, String tooltip, Consumer<Player> onClick) {
        return ActionButton.create(label, Msg.parse("<gray>" + tooltip), Ui.BUTTON_W,
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
