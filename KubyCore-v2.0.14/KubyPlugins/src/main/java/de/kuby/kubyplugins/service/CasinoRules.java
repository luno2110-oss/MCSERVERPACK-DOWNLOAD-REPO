package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Regelbuch fürs Casino. Die Zahlen (Einsätze, Wartezeiten, Preis) kommen aus der config.yml, damit das Buch
 * immer zu den eingestellten Werten passt. game = null öffnet das ganze Buch, sonst nur die Seiten dieses Spiels.
 */
public final class CasinoRules {

    public static final List<String> GAMES = List.of("blackjack", "roulette", "slots", "crash");

    private CasinoRules() {
    }

    /** Öffnet das Regelbuch. Gibt false zurück, wenn es das Spiel nicht gibt. */
    public static boolean open(KubyPlugins plugin, Player p, String game) {
        if (game != null && !GAMES.contains(game)) return false;
        FileConfiguration c = plugin.getConfig();
        List<String> pages = new ArrayList<>();
        if (game == null) {
            pages.add(overview(plugin));
            pages.add(jackpot());
        }
        if (game == null || game.equals("blackjack")) pages.addAll(blackjack(c));
        if (game == null || game.equals("roulette")) pages.add(roulette(c));
        if (game == null || game.equals("slots")) pages.add(slots(c));
        if (game == null || game.equals("crash")) pages.add(crash(c));
        pages.add(fairPlay());
        List<Component> components = new ArrayList<>();
        for (String page : pages) components.add(Msg.parse(page));
        p.openBook(Book.book(Component.text("Casino-Regeln"), Component.text("Kuby"), components));
        return true;
    }

    private static String overview(KubyPlugins plugin) {
        return "<dark_red><b>KUBY CASINO</b></dark_red>\n\n"
                + "<black>Gespielt wird mit <dark_blue><b>Chips</b></dark_blue>.\n\n"
                + "<black>1 Chip = <dark_green>" + Msg.coins(plugin.casino().price()) + "$</dark_green>\n\n"
                + "<black>Tauschen am Kassentisch:\n"
                + "<dark_gray>/casino kaufen <n>\n/casino verkaufen <n>\n\n"
                + "<black>Nur in <dark_green>Survival</dark_green>.";
    }

    private static String jackpot() {
        return "<dark_red><b>JACKPOT</b></dark_red>\n\n"
                + "<black>Von jedem Einsatz geht <dark_blue>1 %</dark_blue> in den Jackpot.\n\n"
                + "<black>Er schwebt über dem Kassentisch.\n\n"
                + "<black>Drei <dark_red><b>7</b></dark_red> bei Slots knacken ihn. Das Geld kommt direkt aufs Konto.";
    }

    private static List<String> blackjack(FileConfiguration c) {
        long bet = c.getLong("casino.blackjack.bet", 1);
        return List.of(
                "<dark_red><b>BLACKJACK</b></dark_red>\n\n"
                        + "<black>Ziel: näher an <dark_blue>21</dark_blue> als der Dealer, ohne drüber.\n\n"
                        + "<black>Rechtsklick auf den Tisch startet. Einsatz: <dark_blue>" + bet + " Chip(s)</dark_blue>\n\n"
                        + "<dark_gray>/casino hit<black> = Karte\n"
                        + "<dark_gray>/casino stand<black> = halten",
                "<dark_red><b>BLACKJACK</b></dark_red>\n\n"
                        + "<black>Bildkarten zählen 10, Asse 1 oder 11.\n"
                        + "Der Dealer zieht bis 17.\n\n"
                        + "<dark_green>Sieg:</dark_green> <black>doppelter Einsatz\n"
                        + "<dark_green>Blackjack:</dark_green> <black>2,5-fach\n"
                        + "<dark_gray>Gleichstand:</dark_gray> <black>Einsatz zurück\n\n"
                        + "<black>Wer geht, hält automatisch.");
    }

    private static String roulette(FileConfiguration c) {
        long max = c.getLong("casino.roulette.max-bet", 50);
        long wait = c.getLong("casino.roulette.wait-seconds", 20);
        return "<dark_red><b>ROULETTE</b></dark_red>\n\n"
                + "<dark_gray>/casino setzen <Einsatz> <rot|schwarz|0-36>\n\n"
                + "<black>Bis <dark_blue>" + max + " Chips</dark_blue>. Nach <dark_blue>" + wait + " s</dark_blue> dreht das Rad.\n\n"
                + "<dark_green>Zahl:</dark_green> <black>36-fach\n"
                + "<dark_green>Rot/Schwarz:</dark_green> <black>doppelt\n\n"
                + "<black>Die <dark_green>0</dark_green> ist grün.";
    }

    private static String slots(FileConfiguration c) {
        long bet = c.getLong("casino.slots.bet", 1);
        return "<dark_red><b>SLOTS</b></dark_red>\n\n"
                + "<black>Rechtsklick auf den Hebel. Einsatz: <dark_blue>" + bet + " Chip(s)</dark_blue>\n\n"
                + "<black>Drei gleiche gewinnen:\n"
                + "<dark_green>♣</dark_green> <black>8-fach\n"
                + "<dark_aqua>♦</dark_aqua> <black>12-fach\n"
                + "<dark_red>♥</dark_red> <black>25-fach\n"
                + "<gold>★</gold> <black>60-fach\n"
                + "<dark_red><b>7</b></dark_red> <black>100-fach + Jackpot";
    }

    private static String crash(FileConfiguration c) {
        long bet = c.getLong("casino.crash.bet", 10);
        long wait = c.getLong("casino.crash.wait-seconds", 10);
        long cap = Math.round(c.getDouble("casino.crash.max-multiplier", 50));
        return "<dark_red><b>CRASH</b></dark_red>\n\n"
                + "<black>Rechtsklick steigt ein. Einsatz: <dark_blue>" + bet + " Chips</dark_blue>\n\n"
                + "<black>Nach <dark_blue>" + wait + " s</dark_blue> steigt der Multiplikator.\n\n"
                + "<black>Rechtsklick steigt aus: Einsatz mal Multiplikator.\n\n"
                + "<black>Wer beim Crash drin ist, verliert. Höchstens <dark_blue>" + cap + "x</dark_blue>.";
    }

    private static String fairPlay() {
        return "<dark_red><b>FAIR PLAY</b></dark_red>\n\n"
                + "<black>Ausloggen oder Weggehen bringt keinen Vorteil: Die Runde wird fertig gespielt.\n\n"
                + "<black>Bugs ausnutzen ist verboten und wird bestraft.\n\n"
                + "<dark_gray>Viel Glück!";
    }
}
