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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Casino-Hilfe als Dialog-Fenster im Stil der Hilfe-Referenz: Kopf mit Icon, Abschnitt "So geht's", Befehle und Buttons.
 * Mit geladenem Grafik-Pack (KubyUI) im Casino-Stil auf dem Dialog-Kit (Reiter für alle Seiten), sonst die normale Ansicht.
 * Die Zahlen (Einsätze, Wartezeiten, Preis) kommen aus der config.yml.
 */
public final class CasinoRules {

    public static final List<String> GAMES = List.of("blackjack", "roulette", "slots", "crash", "plinko", "rennen", "losbude");

    private static final int WIDTH = 320;
    /** Platzhalter für Icons im Text. Ohne Grafik-Pack werden daraus normale Zeichen. */
    private static final Map<String, String> PLAIN_TOKENS = Map.of(
            "{club}", "♣", "{diamond}", "♦", "{heart}", "♥", "{star}", "★", "{seven}", "7");

    private record Btn(String label, String tooltip, boolean red, Consumer<Player> onClick) {}

    private record Page(String title, String subtitle, de.kuby.kubyplugins.ui.KitData.Icon icon, List<String> how, List<String[]> commands,
                        List<String[]> rows, String own) {
        Page(String title, String subtitle, de.kuby.kubyplugins.ui.KitData.Icon icon, List<String> how, List<String[]> commands) {
            this(title, subtitle, icon, how, commands, List.of(), null);
        }
    }

    /** Reihenfolge der Seiten zum Blättern (wie in der Hilfe-Referenz: "< vorherige | Hinreisen | nächste >"). */
    private static final List<String> ORDER = List.of("casino", "blackjack", "roulette", "slots", "crash", "plinko", "rennen", "losbude", "rangliste");

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
        String own = rank == 0 ? "Du hast noch nicht gespielt."
                : "Dein Platz: " + rank + "  (" + (mine >= 0 ? "+" : "") + de.kuby.kubyplugins.casino.CasinoText.num(mine) + " Chips)";
        lines.add(own);
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            String name = org.bukkit.Bukkit.getOfflinePlayer(top.get(i).getKey()).getName();
            long net = top.get(i).getValue();
            rows.add(new String[]{"#" + (i + 1), name == null ? "?" : name,
                    (net >= 0 ? "+" : "-") + de.kuby.kubyplugins.casino.CasinoText.num(Math.abs(net)) + " Chips"});
        }
        return new Page("RANGLISTE", "Die besten Spieler im Casino", de.kuby.kubyplugins.ui.KitData.Icon.TOTEM, lines, List.of(), rows, own);
    }

    private static Page overview(KubyPlugins plugin) {
        List<String> how = List.of(
                "Im Casino spielst du mit Chips an Slots, Roulette, Blackjack, Crash, Plinko und beim Pferderennen. In der VIP-Lounge gibt es Poker.",
                "Chips tauschst du an der Kasse gegen Coins und wieder zurück. 1 Chip = 1 Coin, beim Auszahlen "
                        + plugin.getConfig().getInt("casino.cashout-tax-percent", 5) + " % Steuer.",
                "Jeden " + drawDay(plugin) + " um " + plugin.getConfig().getString("casino.jackpot.draw-time", "18:00")
                        + " Uhr wird der Jackpot ausgelost. Er wächst mit jedem Einsatz im Casino. Wer seit der letzten Ziehung"
                        + " einmal gespielt hat, ist dabei, und einer gewinnt den ganzen Topf in Chips.",
                "Im Casino läuft Musik. Bei DJ Mia oder mit /music suchst du dir Songs aus oder schaltest sie aus.",
                "An der Losbude gibt es Rubbellose für Chips: aufdecken, drei gleiche Symbole gewinnen.",
                "Am Glücksrad drehst du alle " + plugin.getConfig().getLong("casino.gluecksrad.stunden", 6) + " Stunden einmal gratis:"
                        + " Chips, Juwelen, Kisten-Schlüssel, Booster oder der Hauptgewinn. /gluecksrad zeigt, wann du wieder darfst.",
                "Im Chip-Shop (an der Kasse oder /casino shop) gibt es für Chips Casino-Hüte, Effekte, Booster, Ränge und"
                        + " Titel, von 30.000 bis über 1,5 Mio. Chips. Hüte und Titel von dort gelten in allen Modi.",
                "In der VIP-Lounge geben dir die Barkeeper einmal am Tag "
                        + plugin.getConfig().getLong("casino.vip.daily-chips", 500) + " Chips. Rein darfst du mit dem Legend-Rang"
                        + " oder als einer der besten " + plugin.getConfig().getInt("casino.vip.top", 10)
                        + " in der Casino-Rangliste \"Am meisten Plus\".",
                "In der VIP-Lounge stehen Pokertische in drei Farben: grün (Big Blind " + de.kuby.kubyplugins.casino.TableTier.GRUEN.minBet(plugin)
                        + ", Buy-in bis " + de.kuby.kubyplugins.casino.TableTier.GRUEN.maxBet(plugin) + "), rot (" + de.kuby.kubyplugins.casino.TableTier.ROT.minBet(plugin) + " / "
                        + de.kuby.kubyplugins.casino.TableTier.ROT.maxBet(plugin) + ") und violett (" + de.kuby.kubyplugins.casino.TableTier.VIOLETT.minBet(plugin) + " / " + de.kuby.kubyplugins.casino.TableTier.VIOLETT.maxBet(plugin)
                        + "). Deinen Stack nimmst du beim Aufstehen wieder mit.",
                "Das Casino gibt es nur in Survival.",
                "Schleichen + Rechtsklick auf einen Tisch zeigt die Regeln dieses Spiels.");
        List<String[]> commands = List.of(
                new String[]{"/casino", "Ins Casino"},
                new String[]{"/casino buy <Anzahl>", "Chips kaufen"},
                new String[]{"/casino sell <Anzahl>", "Chips eintauschen"},
                new String[]{"/casino shop", "Chip-Shop"},
                new String[]{"/gluecksrad", "Glücksrad: Wartezeit"},
                new String[]{"/casino stats", "Rangliste"},
                new String[]{"/music", "Casino-Musik"},
                new String[]{"/poker", "Poker in der VIP-Lounge"},
                new String[]{"/casino status", "Deine Chips und der Jackpot"},
                new String[]{"/casino regeln", "Diese Hilfe"});
        return new Page("CASINO", "Slots, Roulette, Blackjack, Poker, Crash und Plinko", de.kuby.kubyplugins.ui.KitData.Icon.CHIP_RED, how, commands);
    }

    private static Page gamePage(KubyPlugins plugin, String game) {
        FileConfiguration c = plugin.getConfig();
        List<String> how;
        List<String[]> commands = new ArrayList<>();
        String subtitle;
        de.kuby.kubyplugins.ui.KitData.Icon icon;
        switch (game) {
            case "blackjack" -> {
                subtitle = "Näher an 21 als der Dealer";
                icon = de.kuby.kubyplugins.ui.KitData.Icon.CHIP_BLACK;
                how = List.of(
                        "Rechtsklick auf einen Hocker oder den Dealer: hinsetzen. Klick Chips aus dem Rack vor dir auf dein Feld ("
                                + "grüner Tisch " + de.kuby.kubyplugins.casino.CasinoText.num(de.kuby.kubyplugins.casino.TableTier.GRUEN.minBet(plugin)) + " bis "
                                + de.kuby.kubyplugins.casino.CasinoText.num(de.kuby.kubyplugins.casino.TableTier.GRUEN.maxBet(plugin)) + ", roter "
                                + de.kuby.kubyplugins.casino.CasinoText.num(de.kuby.kubyplugins.casino.TableTier.ROT.minBet(plugin)) + " bis "
                                + de.kuby.kubyplugins.casino.CasinoText.num(de.kuby.kubyplugins.casino.TableTier.ROT.maxBet(plugin)) + ", violetter "
                                + de.kuby.kubyplugins.casino.CasinoText.num(de.kuby.kubyplugins.casino.TableTier.VIOLETT.minBet(plugin)) + " bis "
                                + de.kuby.kubyplugins.casino.CasinoText.num(de.kuby.kubyplugins.casino.TableTier.VIOLETT.maxBet(plugin))
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
                icon = de.kuby.kubyplugins.ui.KitData.Icon.ROULETTE_WHEEL;
                how = List.of(
                        "Am Tisch oder beim Croupier öffnest du mit /roulette das Menü. Chip wählen, dann auf ein Feld klicken.",
                        "Der erste Einsatz startet die Runde, nach " + c.getLong("casino.roulette.wait-seconds", 20)
                                + " Sekunden läuft das Licht über die Zahlen und bleibt auf dem Ergebnis stehen.",
                        "Rot, Schwarz, Gerade, Ungerade, 1–18, 19–36: 1:1. Dutzend und Kolonne: 2:1. Zahl: 31:1.",
                        "Nichts geht mehr: 1 bis 3 Blitz-Zahlen zahlen bei Volltreffer x2 bis x5 (also bis 160:1). Wer am meisten setzt, wirft die Kugel.");
                commands.add(new String[]{"/roulette", "Roulette-Menü"});
                commands.add(new String[]{"/casino setzen <Einsatz> <rot|schwarz|0-36>", "Einsatz per Befehl"});
            }
            case "slots" -> {
                subtitle = "Drei gleiche auf der Mittellinie";
                icon = de.kuby.kubyplugins.ui.KitData.Icon.SLOT_BIG;
                how = List.of(
                        "Rechtsklick auf den Hebel dreht die Walzen. Auf dem Hocker davor sitzt du bequem und hast den Automaten für dich.",
                        "Der Knopf am Bedienfeld wählt den Einsatz: Klick erhöht, Schleichen + Klick senkt (10 bis 5.000 Chips).",
                        "Drei gleiche zahlen 8- bis 80-fach, Kirschen schon einzeln. Den Gewinnplan zeigt das Schild am Automaten.",
                        "Drei Siebener knacken den Jackpot. Ein Teil jedes Drehs wandert hinein, voller Einsatz holt den größten Anteil.");
            }
            case "plinko" -> {
                subtitle = "Kugel durch zwölf Stiftreihen in eins von 13 Fächern";
                icon = de.kuby.kubyplugins.ui.KitData.Icon.PLINKO_BIG;
                how = List.of(
                        "Am Plinko-Brett wählst du unten das Risiko (Niedrig, Mittel, Hoch) und den Einsatz (Klick höher, Schleichen + Klick"
                                + " tiefer, 10 bis 5.000 Chips). Der Knopf BALL lässt eine Kugel fallen, bis zu zehn gleichzeitig.",
                        "An jedem Stift geht es zufällig links oder rechts. Die Mitte ist häufig und zahlt wenig, die Ränder sind selten"
                                + " und zahlen viel: Niedrig bis 10x, Mittel bis 25x, Hoch bis 150x.",
                        "Klick aufs Brett öffnet das Plinko-Fenster: Gewinne je Fach mit Chance, eigener Einsatz und deine letzten Kugeln.",
                        "Ein Spieler hat das Brett für sich, bis er 30 Sekunden keine Kugel mehr wirft oder weggeht.");
                commands.add(new String[]{"/casino plinko", "Plinko-Fenster am nächsten Brett"});
            }
            case "rennen" -> {
                subtitle = "Pferderennen: Sieg oder Platz";
                icon = de.kuby.kubyplugins.ui.KitData.Icon.RACE_BIG;
                how = List.of(
                        "Rechtsklick auf den Rennbildschirm öffnet die Wetten. Jedes Rennen hat neue Quoten: Favoriten zahlen wenig,"
                                + " Außenseiter viel. Die Sterne zeigen die Form.",
                        "Sieg: dein Pferd wird Erster. Platz: dein Pferd kommt unter die ersten zwei (kleinere Quote). Chip wählen, dann"
                                + " auf die Quote klicken; mehrere Wetten pro Rennen sind erlaubt.",
                        "Die erste Wette startet den Countdown (" + c.getLong("casino.rennen.wait-seconds", 25) + " s), dann öffnen die Startboxen."
                                + " Ausgezahlt wird, sobald der Zweite im Ziel ist: Einsatz mal Quote.",
                        "Bis zum Start kannst du deine Wetten zurücknehmen.");
                commands.add(new String[]{"/casino rennen", "Wetten am Rennbildschirm"});
            }
            case "losbude" -> {
                subtitle = "Rubbellose für Chips";
                icon = de.kuby.kubyplugins.ui.KitData.Icon.CHIP_PINK;
                how = List.of(
                        "Rechtsklick auf die Figur an der Losbude. Bronze-Los 100, Silber-Los 1.000, Gold-Los 10.000 Chips.",
                        "Feld für Feld freirubbeln oder alles auf einmal aufdecken. Drei gleiche Symbole gewinnen.",
                        "Kirsche 1-fach, Zitrone 2-fach, Glocke 5-fach, Stern 10-fach, Sieben 50-fach des Lospreises."
                                + " Etwa jedes dritte Los gewinnt.");
            }
            default -> {
                subtitle = "Rechtzeitig aussteigen";
                icon = de.kuby.kubyplugins.ui.KitData.Icon.CHIP_PURPLE;
                how = List.of(
                        "Einsatz beim Crash-Creeper oder an den Schildern unter dem Bildschirm setzen ("
                                + c.getLong("casino.crash.min-bet", 10) + " bis " + de.kuby.kubyplugins.casino.CasinoText.num(c.getLong("casino.crash.max-bet", 100000)) + " Chips).",
                        "Nach " + c.getLong("casino.crash.wait-seconds", 10) + " Sekunden steigt der Multiplikator, je höher, desto schneller.",
                        "Steig aus mit Schleichen + Klick, Klick oder Schlag aufs Schild, dem Chat-Knopf oder /cashout:"
                                + " Einsatz mal Multiplikator. Wer beim Crash noch drin ist, verliert den Einsatz.",
                        "Bei " + Math.round(c.getDouble("casino.crash.max-multiplier", 50)) + "x wird automatisch ausgezahlt.");
                commands.add(new String[]{"/cashout", "Jetzt aussteigen"});
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
        if (plugin.uiPack().active(p)) {
            // Casino-Stil auf dem Dialog-Kit: Reiter für alle Seiten statt Blättern
            List<String> tabs = new ArrayList<>();
            for (int k = 0; k < size; k++) tabs.add(navName(k));
            boolean ranking = ORDER.get(i).equals("rangliste");
            de.kuby.kubyplugins.casino.CasinoScreens.HelpView view = new de.kuby.kubyplugins.casino.CasinoScreens.HelpView(
                    page.title(), page.subtitle(), page.icon(), ranking ? List.of() : clean(page.how()),
                    page.commands(), page.rows(), ranking ? page.own() : null, tabs, i);
            de.kuby.kubyplugins.ui.UiDialog.show(p, de.kuby.kubyplugins.casino.CasinoScreens.help(view,
                    new de.kuby.kubyplugins.casino.CasinoScreens.HelpActions() {
                        @Override
                        public Consumer<Player> tab(int k) {
                            return pl -> show(plugin, pl, k);
                        }

                        @Override
                        public Consumer<Player> travel() {
                            return pl -> plugin.casino().travel(pl);
                        }
                    }), 330);
            return;
        }
        de.kuby.kubyplugins.ui.UiDialog.open(p, plain(page, buttons));
    }

    /** Platzhalter für Icons ({club} ...) durch Zeichen ersetzen. */
    private static List<String> clean(List<String> how) {
        List<String> out = new ArrayList<>();
        for (String line : how) {
            String text = line;
            for (Map.Entry<String, String> t : PLAIN_TOKENS.entrySet()) text = text.replace(t.getKey(), t.getValue());
            out.add(text);
        }
        return out;
    }

    private static String navName(int index) {
        String key = ORDER.get(index);
        return switch (key) {
            case "casino" -> "Casino";
            case "rangliste" -> "Rangliste";
            default -> title(key);
        };
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
            case "losbude" -> "Losbude";
            case "plinko" -> "Plinko";
            case "rennen" -> "Rennen";
            default -> "Crash";
        };
    }
}
