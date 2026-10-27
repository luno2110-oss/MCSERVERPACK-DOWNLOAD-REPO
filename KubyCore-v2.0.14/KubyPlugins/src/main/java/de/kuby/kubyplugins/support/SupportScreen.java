package de.kuby.kubyplugins.support;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.util.I18n;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * /support nach der Referenz „Neue-referenzen“: Übersicht mit Kopf (Headset, Schriftzug, offene Tickets), drei Karten
 * (Frage stellen, Meine Tickets, Helfer werden bzw. für Helfer „Helfen“), „Letzte Tickets“, Bestenliste und
 * Discord-Ticket (Link). Unterseiten mit Zurück-Knopf: Neues Ticket (nur der Kopf, Felder und Knöpfe sind von
 * Minecraft), Bestenliste (Woche / Gesamt, zehn Plätze), Meine Tickets, Helfer werden (Spielzeit in zehn Stücken,
 * Discord, Knopf). In derselben Optik: die Ticket-Liste der Helfer, ein Ticket mit Verlauf und die Sterne-Bewertung.
 * Alles statisch aus Daten (Records), damit der Selbsttest und die Vorschau die Seiten ohne Server bauen können.
 */
public final class SupportScreen {

    public static final int W = 390, HEIGHT = 350;
    static final TextColor PURPLE = TextColor.color(0xB7A6FC), BLUE = TextColor.color(0x7BC4FC),
            GREEN = TextColor.color(0x6DE288), GOLD = TextColor.color(0xFCD45D), NAVY = TextColor.color(0x8B94FC),
            GRAY = TextColor.color(0x9FA1A7), BODY = TextColor.color(0xD1D2D5), WHITE = TextColor.color(0xFCFCFC),
            DIM = TextColor.color(0x797D84), SUB_GOLD = TextColor.color(0xFCC965),
            NAV = TextColor.color(0xC0C4CB), SILVER = TextColor.color(0xD0D4DB), BRONZE = TextColor.color(0xDD9059);
    /** Dunkle Schrift auf goldenen Schaltern und den Flächen der Minecraft-Knöpfe („Abschicken“, „Zurück“). */
    public static final TextColor ON = TextColor.color(0x1C122D);
    /** Plätze 1 bis 3 und Rest: Kasten und Farbe der Nummer. */
    private static final KitData.Box[] RANK_BOX = {KitData.Box.SU_LB_GOLD, KitData.Box.SU_LB_SILVER, KitData.Box.SU_LB_BRONZE};
    private static final TextColor[] RANK_COLOR = {GOLD, SILVER, BRONZE};
    /** Zeilen der Ticket-Listen (Kasten 32 hoch, Abstand 36) und Verlauf. */
    private static final int ROW_H = 32, ROW_DY = 36, LIST_MAX = 7, HISTORY_MAX = 14;

    /** Ein Ticket in Listen: Nummer, Titel, Zustand (Text und Farbe), Zeile darunter, neue Antwort. */
    public record Item(int id, String title, String state, TextColor stateColor, String info, boolean fresh) {}

    /**
     * Übersicht: offene Tickets / erlaubt, die letzten eigenen Tickets, Anzahl eigener und ungelesener, Helfer (dann
     * Karte „Helfen“ mit wartenden Fragen), Stunden für Helfer, Link zum Discord-Ticket.
     */
    public record Home(int open, int max, List<Item> latest, int mine, int unread, boolean helper, int waiting,
                       long hours, String discordUrl) {}

    /** Ein Platz der Bestenliste (Name null = frei). */
    public record Place(String name, int points) {}

    /** Bestenliste: Woche oder gesamt, Plätze, Zeile unten (z. B. „Du bist kein Ingame-Helfer.“). */
    public record Board(boolean week, List<Place> places, String footer) {}

    /** Helfer werden: Spielzeit in Minuten, nötige Stunden, Discord verlinkt, schon Helfer. */
    public record Become(long minutes, long hours, boolean discord, boolean helper) {}

    /** Ticket-Liste der Helfer: Reiter (offen, meine, team), Team sichtbar, Anzahl je Reiter, Tickets. */
    public record Queue(String tab, boolean team, int[] counts, List<Item> items) {}

    /** Eine Nachricht im Verlauf; role: spieler, helfer, team, system. */
    public record Line(String name, String role, String when, String text) {}

    /**
     * Ein Ticket: eigenes (blau) oder fremdes (grün, Helfer-Sicht), Verlauf, Sterne (geschlossen) und welche Knöpfe.
     */
    public record Detail(int id, String title, String author, String created, String state, TextColor stateColor,
                         boolean own, List<Line> lines, int stars, boolean reply, boolean claim, boolean escalate,
                         boolean solve, boolean close) {}

    public interface Actions {
        Consumer<Player> home();

        Consumer<Player> ask();

        Consumer<Player> mine();

        Consumer<Player> board(boolean week);

        Consumer<Player> become();

        Consumer<Player> join();

        Consumer<Player> verify();

        Consumer<Player> queue(String tab);

        Consumer<Player> ticket(int id);

        Consumer<Player> reply(int id);

        Consumer<Player> claim(int id);

        Consumer<Player> escalate(int id);

        Consumer<Player> rate(int id);

        Consumer<Player> solve(int id, int stars);

        Consumer<Player> close(int id);
    }

    private SupportScreen() {
    }

    public static TextColor stateColor(SupportService.Status s) {
        return switch (s) {
            case OFFEN -> GOLD;
            case IN_ARBEIT -> BLUE;
            case TEAM -> PURPLE;
            case GELOEST -> GREEN;
            case GESCHLOSSEN -> GRAY;
        };
    }

    /** Titel über dem Dialog: „Support“, auf Unterseiten „Support › ꜱᴇɪᴛᴇ“. */
    private static Canvas canvas(String page) {
        Canvas c = new Canvas(W);
        if (page == null) return c.heading("Support", PURPLE);
        return c.heading(Component.text().append(Component.text("Support", PURPLE))
                .append(Component.text(" › " + Canvas.caps(page), GRAY)).build());
    }

    /** Text mittig um cx, links aufgerundet wie im Original. */
    private static void center(Canvas c, int cx, int y, String text, TextColor color, boolean bold) {
        c.text(cx - (Canvas.width(Canvas.clean(text), bold) + 1) / 2, y, text, color, bold);
    }

    /** Rechts im Kopf: Ticket, „Offene / Tickets“ und Anzahl (Kopf 51 hoch: top 15, 42 hoch: top 12). */
    private static void openCount(Canvas c, int top, int open, int max, TextColor color) {
        c.sprite(297, top + 3, KitData.Sprite.SU_TICKET);
        c.text(319, top, "Offene", GRAY, false);
        c.text(319, top + 10, "Tickets", GRAY, false);
        c.textRight(380, top + 5, List.of(Span.bold(String.valueOf(open), color), Span.of("/" + max, GRAY)));
    }

    /** Kopf der Unterseiten (42 hoch) mit Zurück-Knopf, Titel und Untertitel. */
    private static void head(Canvas c, KitData.Box box, Consumer<Player> back, String title, TextColor color, String sub,
                             TextColor subColor, int room) {
        c.box(0, 0, W, 42, box);
        c.sprite(8, 9, KitData.Sprite.SU_BACK);
        c.action(8, 9, 22, 22, back, "Zurück");
        c.text(40, 10, Canvas.fit(upper(title), true, room), color, true);
        c.text(40, 22, Canvas.fit(sub, false, room), subColor, false);
    }

    // =================================================================================================================
    // Übersicht
    // =================================================================================================================

    public static Canvas home(Home h, Actions a) {
        Canvas c = canvas(null);
        c.box(0, 0, W, 51, KitData.Box.SU_HEAD);
        c.sprite(10, 13, KitData.Sprite.SU_HEADSET);
        c.sprite(50, 15, KitData.Sprite.SU_TITLE);
        openCount(c, 15, h.open(), h.max(), PURPLE);

        // Karten
        String mineSub = h.mine() == 0 ? "Noch keine Tickets"
                : h.unread() > 0 ? h.unread() + (h.unread() == 1 ? " neue Antwort" : " neue Antworten")
                : h.open() > 0 ? h.open() + " offen" : h.mine() + (h.mine() == 1 ? " Ticket" : " Tickets");
        card(c, 0, KitData.Box.SU_CARD_PURPLE, KitData.Box.SU_CBTN_PURPLE, KitData.Sprite.SU_QUESTION, 41, 66,
                "Frage stellen", "Ein Helfer antwortet", "Schreiben", PURPLE, a.ask(), "Neues Ticket schreiben");
        card(c, 132, KitData.Box.SU_CARD_BLUE, KitData.Box.SU_CBTN_BLUE, KitData.Sprite.SU_TICKETS, 177, 77,
                "Meine Tickets", mineSub, "Ansehen", BLUE, a.mine(), "Deine Tickets ansehen");
        if (h.helper()) {
            card(c, 264, KitData.Box.SU_CARD_GREEN, KitData.Box.SU_CBTN_GREEN, KitData.Sprite.SU_SHIELD, 306, 66,
                    "Helfen", h.waiting() == 0 ? "Gerade nichts offen"
                            : h.waiting() + (h.waiting() == 1 ? " Frage wartet" : " Fragen warten"),
                    "Öffnen", GREEN, a.queue("offen"), "Fragen anderer Spieler beantworten");
        } else {
            card(c, 264, KitData.Box.SU_CARD_GREEN, KitData.Box.SU_CBTN_GREEN, KitData.Sprite.SU_SHIELD, 306, 66,
                    "Helfer werden", "Ab " + h.hours() + " h Spielzeit", "Prüfen", GREEN, a.become(), "Voraussetzungen ansehen");
        }

        // Letzte Tickets
        int y = 189;
        int n = Math.max(1, Math.min(3, h.latest().size()));
        int bh = 31 + 11 * n;
        c.box(0, y, W, bh, KitData.Box.SU_PURPLE);
        c.text(10, y + 10, Canvas.caps("Letzte Tickets"), GRAY, false);
        if (h.latest().isEmpty()) {
            c.text(10, y + 24, "Noch kein Ticket. Stell deine erste Frage.", BODY, false);
        } else {
            for (int i = 0; i < n; i++) {
                Item t = h.latest().get(i);
                int ly = y + 24 + 11 * i;
                int sw = Canvas.width(t.state(), true);
                c.text(10, ly, List.of(Span.of("#" + t.id() + " ", GRAY),
                        Span.of(Canvas.fit(t.title(), false, 360 - sw - 30), t.fresh() ? WHITE : BODY)));
                c.textRight(380, ly, t.state(), t.stateColor(), true);
                c.action(4, ly - 2, W - 8, 11, a.ticket(t.id()), "Ticket öffnen");
            }
        }
        y += bh + 3;

        // Bestenliste, Discord-Ticket
        row(c, y, KitData.Box.SU_GOLD, KitData.Sprite.SU_TROPHY, "Bestenliste", "Die Top 10 Helfer", GOLD);
        c.action(0, y, W, 32, a.board(true), "Bestenliste ansehen");
        y += 36;
        row(c, y, KitData.Box.SU_NAVY, KitData.Sprite.SU_DISCORD, "Discord-Ticket", "Reports, Strafen, Entbannung", NAVY);
        if (h.discordUrl() != null && !h.discordUrl().isBlank()) {
            c.actionUrl(0, y, W, 32, h.discordUrl(), Component.text()
                    .append(Component.text(I18n.ctx("Für Reports und Strafen: Discord-Ticket."), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text(I18n.ctx("Klick"), NamedTextColor.YELLOW))
                    .append(Component.text(I18n.ctx(" — Discord öffnen"), NamedTextColor.GRAY)).build());
        }
        return c;
    }

    private static void card(Canvas c, int x, KitData.Box box, KitData.Box btn, KitData.Sprite art, int ax, int ay,
                             String title, String sub, String button, TextColor color, Consumer<Player> click, String hover) {
        c.box(x, 54, 126, 128, box);
        c.sprite(ax, ay, art);
        center(c, x + 63, 117, Canvas.fit(upper(title), true, 118), color, true);
        center(c, x + 63, 135, Canvas.fit(sub, false, 118), BODY, false);
        c.box(x + 7, 156, 112, 20, btn, Canvas.Z_BUTTON);
        center(c, x + 59, 162, upper(button), color, true);
        c.text(x + 108, 162, "›", color, true);
        c.action(x, 54, 126, 128, click, hover);
    }

    /** Zeile 32 hoch: Grafik, Titel fett, Untertitel grau, Pfeil rechts. */
    private static void row(Canvas c, int y, KitData.Box box, KitData.Sprite icon, String title, String sub, TextColor color) {
        c.box(0, y, W, 32, box);
        c.sprite(10, y + 8, icon);
        c.text(34, y + 6, title, color, true);
        c.text(34, y + 18, sub, GRAY, false);
        c.text(376, y + 12, "›", color, true);
    }

    // =================================================================================================================
    // Neues Ticket (Felder und Knöpfe darunter sind von Minecraft)
    // =================================================================================================================

    public static Canvas ask(Actions a) {
        Canvas c = canvas("Neues Ticket");
        head(c, KitData.Box.SU_HEAD_PURPLE, a.home(), "Neues Ticket", PURPLE, "Für Reports und Strafen: Discord-Ticket.",
                SUB_GOLD, 316);
        c.sprite(364, 11, KitData.Sprite.SU_PENCIL);
        return c;
    }

    /** Kopf über dem Antwort-Feld: Ticket-Nummer, Titel, Stift. */
    public static Canvas reply(int id, String title, boolean own, Actions a) {
        Canvas c = canvas("Antworten");
        head(c, own ? KitData.Box.SU_HEAD_BLUE : KitData.Box.SU_HEAD_GREEN, a.ticket(id), "Antwort auf #" + id,
                own ? BLUE : GREEN, title, GRAY, 316);
        c.sprite(364, 11, KitData.Sprite.SU_PENCIL);
        return c;
    }

    // =================================================================================================================
    // Bestenliste
    // =================================================================================================================

    public static Canvas board(Board b, Actions a) {
        Canvas c = canvas("Bestenliste");
        head(c, KitData.Box.SU_HEAD_GOLD, a.home(), "Bestenliste", GOLD,
                b.week() ? "Neue Woche ab Montag 06:00" : "Alle Punkte seit dem Start", GRAY, 200);
        toggle(c, 248, "Woche", b.week(), a.board(true));
        toggle(c, 316, "Gesamt", !b.week(), a.board(false));
        for (int i = 0; i < 10; i++) {
            int y = 48 + 27 * i;
            c.box(0, y, W, 20, i < 3 ? RANK_BOX[i] : KitData.Box.SU_LB_GRAY);
            TextColor rc = i < 3 ? RANK_COLOR[i] : GRAY;
            c.textRight(34, y + 6, "#" + (i + 1), rc, true);
            Place p = i < b.places().size() ? b.places().get(i) : null;
            if (p == null || p.name() == null) {
                c.text(54, y + 6, Canvas.caps("Frei"), DIM, false);
                c.textRight(376, y + 6, "—", DIM, false);
            } else {
                c.text(54, y + 6, p.name(), WHITE, false);
                c.textRight(376, y + 6, p.points() + (p.points() == 1 ? " Punkt" : " Punkte"), rc, true);
            }
        }
        c.box(0, 327, W, 20, KitData.Box.SU_FOOTER);
        c.text(12, 333, Canvas.fit(b.footer(), false, 366), BODY, false);
        return c;
    }

    /** Schalter 64 x 19 (gewählt: gold mit dunkler Schrift, sonst grau), Text mittig. */
    private static void toggle(Canvas c, int x, String label, boolean on, Consumer<Player> click) {
        c.box(x, 12, 64, 19, on ? KitData.Box.SU_TOGGLE_ON : KitData.Box.SU_TOGGLE_OFF, Canvas.Z_BUTTON);
        c.textFlat(x + (64 - Canvas.width(Canvas.clean(label), false)) / 2, 18, label, on ? ON : BODY, false);
        if (!on) c.action(x, 12, 64, 19, click, label);
    }

    // =================================================================================================================
    // Meine Tickets, Ticket-Liste der Helfer
    // =================================================================================================================

    public static Canvas mine(int open, int max, List<Item> items, Actions a) {
        Canvas c = canvas(null);
        head(c, KitData.Box.SU_HEAD_BLUE, a.home(), "Meine Tickets", BLUE, "Deine Fragen · neueste zuerst", GRAY, 250);
        openCount(c, 12, open, max, BLUE);
        if (items.isEmpty()) {
            c.box(0, 45, W, 42, KitData.Box.SU_BLUE);
            c.sprite(10, 59, KitData.Sprite.SU_TICKET_ROW);
            c.text(34, 55, "Noch kein Ticket.", WHITE, true);
            c.text(34, 66, "Stell deine erste Frage über „Frage stellen“.", BODY, false);
            c.action(0, 45, W, 42, a.ask(), "Frage stellen");
            return c;
        }
        list(c, items, KitData.Box.SU_BLUE, a);
        return c;
    }

    public static Canvas queue(Queue q, Actions a) {
        Canvas c = canvas("Helfen");
        String[][] tabs = q.team() ? new String[][]{{"offen", "Offen"}, {"meine", "Meine"}, {"team", "Team"}}
                : new String[][]{{"offen", "Offen"}, {"meine", "Meine"}};
        int tx = W - 10 - 68 * tabs.length + 4;
        head(c, KitData.Box.SU_HEAD_GREEN, a.home(), "Helfen", GREEN, "Älteste Fragen zuerst", GRAY,
                tx - 48);
        for (int i = 0; i < tabs.length; i++) {
            String key = tabs[i][0];
            toggle(c, tx + 68 * i, tabs[i][1] + " " + q.counts()[i], key.equals(q.tab()), a.queue(key));
        }
        if (q.items().isEmpty()) {
            c.box(0, 45, W, 42, KitData.Box.SU_GREEN);
            c.sprite(9, 58, KitData.Sprite.SU_CHECK);
            c.text(34, 55, switch (q.tab()) {
                case "meine" -> "Du betreust gerade kein Ticket.";
                case "team" -> "Beim Team liegt nichts.";
                default -> "Gerade wartet keine Frage.";
            }, WHITE, true);
            c.text(34, 66, q.tab().equals("offen") ? "Neue Tickets meldet dir der Chat." : "Offene Fragen findest du unter „Offen“.",
                    BODY, false);
            return c;
        }
        list(c, q.items(), KitData.Box.SU_GREEN, a);
        return c;
    }

    private static void list(Canvas c, List<Item> items, KitData.Box box, Actions a) {
        int y = 45;
        for (int i = 0; i < items.size() && i < LIST_MAX; i++) {
            Item t = items.get(i);
            c.box(0, y, W, ROW_H, box);
            c.sprite(10, y + 10, KitData.Sprite.SU_TICKET_ROW);
            int sw = Canvas.width(t.state(), true);
            c.text(34, y + 6, List.of(Span.bold("#" + t.id() + " ", GRAY),
                    Span.bold(Canvas.fit(t.title(), true, 330 - sw - 34 - 20), WHITE)));
            c.textRight(366, y + 6, t.state(), t.fresh() ? GOLD : t.stateColor(), true);
            c.text(34, y + 18, Canvas.fit(t.info(), false, 330), GRAY, false);
            c.text(376, y + 12, "›", BODY, true);
            c.action(0, y, W, ROW_H, a.ticket(t.id()), "Ticket öffnen");
            y += ROW_DY;
        }
    }

    // =================================================================================================================
    // Helfer werden
    // =================================================================================================================

    public static Canvas become(Become b, Actions a) {
        Canvas c = canvas("Helfer werden");
        head(c, KitData.Box.SU_HEAD_GREEN, a.home(), "Helfer werden", GREEN,
                "Du beantwortest Fragen anderer Spieler und sammelst Punkte.", GRAY, 316);
        c.sprite(364, 11, KitData.Sprite.SU_CHECK_HEAD);

        c.box(0, 45, W, 96, KitData.Box.SU_GREEN);
        c.text(10, 55, Canvas.caps("Voraussetzungen"), GRAY, false);
        // Spielzeit: zehn Stücke bis zum Ziel
        long need = Math.max(1, b.hours()) * 60;
        boolean timeOk = b.minutes() >= need;
        c.sprite(11, 62, KitData.Sprite.SU_HOURGLASS);
        long h = b.minutes() / 60, m = b.minutes() % 60;
        String have = h >= 100 || m == 0 && h > 0 ? h + " h" : h > 0 ? h + " h " + m + " min" : m + " min";
        c.text(30, 71, List.of(Span.of("Spielzeit ", BODY), Span.of(have, WHITE), Span.of(I18n.context() ? " of " : " von ", BODY),
                Span.of(b.hours() + " h", WHITE)));
        int filled = (int) Math.min(10, b.minutes() * 10 / need);
        for (int i = 0; i < 10; i++) {
            c.sprite(182 + 20 * i, 71, i < filled ? KitData.Sprite.SU_SEG_ON : KitData.Sprite.SU_SEG_OFF);
        }
        // Discord
        if (b.discord()) {
            c.sprite(9, 89, KitData.Sprite.SU_CHECK);
            c.text(30, 93, "Discord verifiziert", GREEN, false);
            c.box(316, 87, 64, 19, KitData.Box.SU_OK, Canvas.Z_BUTTON);
            c.text(316 + (64 - Canvas.width(Canvas.clean("Erfüllt"), false)) / 2, 93, "Erfüllt", ON, false);
        } else {
            c.sprite(9, 89, KitData.Sprite.SU_CHECK, Canvas.Z_ICON, TextColor.color(0x707070));
            c.text(30, 93, "Discord nicht verifiziert", BODY, false);
            c.box(316, 87, 64, 19, KitData.Box.SU_TOGGLE_OFF, Canvas.Z_BUTTON);
            c.textFlat(316 + (64 - Canvas.width(Canvas.clean("Verlinken"), false)) / 2, 93, "Verlinken", BODY, false);
            c.action(316, 87, 64, 19, a.verify(), "Discord verlinken (/verify)");
        }
        List<String> info = Canvas.wrap("SMP und Survival zählen zusammen. Verifizieren auf unserem Discord. "
                + "Gelöste Tickets bringen 1 Punkt plus die Sterne der Bewertung.", 350);
        for (int i = 0; i < info.size() && i < 2; i++) c.text(30, 111 + 12 * i, info.get(i), DIM, false);

        // Knopf
        if (b.helper()) {
            c.box(65, 144, 260, 32, KitData.Box.SU_GREEN);
            c.sprite(76, 153, KitData.Sprite.SU_CHECK);
            c.text(99, 150, "Du bist Helfer", GREEN, true);
            c.text(99, 162, "Zu den offenen Fragen", BODY, false);
            c.action(65, 144, 260, 32, a.queue("offen"), "Fragen beantworten");
        } else if (timeOk && b.discord()) {
            c.box(65, 144, 260, 32, KitData.Box.SU_GREEN);
            c.sprite(76, 153, KitData.Sprite.SU_CHECK);
            c.text(99, 150, "Helfer werden", GREEN, true);
            c.text(99, 162, "Klick zum Freischalten", BODY, false);
            c.action(65, 144, 260, 32, a.join(), "Jetzt Helfer werden");
        } else {
            c.box(65, 144, 260, 32, KitData.Box.SU_LOCKED);
            c.sprite(77, 152, KitData.Sprite.SU_LOCK);
            c.text(99, 150, "Helfer werden", NAV, true);
            c.text(99, 162, "Erst beide Bedingungen erfüllen", GRAY, false);
        }
        return c;
    }

    // =================================================================================================================
    // Ticket und Bewertung
    // =================================================================================================================

    public static Canvas ticket(Detail d, Actions a) {
        Canvas c = canvas("Ticket #" + d.id());
        TextColor accent = d.own() ? BLUE : GREEN;
        KitData.Box box = d.own() ? KitData.Box.SU_BLUE : KitData.Box.SU_GREEN;
        int sw = Canvas.width(d.state(), true);
        head(c, d.own() ? KitData.Box.SU_HEAD_BLUE : KitData.Box.SU_HEAD_GREEN, d.own() ? a.mine() : a.queue("offen"),
                "#" + d.id() + " " + d.title(), accent, "von " + d.author() + " · " + d.created(), GRAY, 380 - sw - 50);
        c.textRight(380, 17, d.state(), d.stateColor(), true);

        // Verlauf: die letzten Zeilen, die passen
        List<String[]> lines = new ArrayList<>();
        for (Line l : d.lines()) {
            lines.add(new String[]{"H", l.name(), l.role(), l.when()});
            if (!l.role().equals("system")) for (String w : Canvas.wrap(l.text(), 350)) lines.add(new String[]{"T", w, l.role()});
        }
        if (lines.size() > HISTORY_MAX) lines = lines.subList(lines.size() - HISTORY_MAX, lines.size());
        int y = 45;
        int h = Math.max(42, 29 + 11 * lines.size());
        c.box(0, y, W, h, box);
        c.text(10, y + 10, Canvas.caps("Verlauf"), GRAY, false);
        int ly = y + 24;
        for (String[] l : lines) {
            if (l[0].equals("H")) {
                if (l[2].equals("system")) {
                    c.text(12, ly, List.of(Span.of(l[1], DIM), Span.of("  " + l[3], DIM)));
                } else {
                    List<Span> s = new ArrayList<>();
                    boolean player = l[2].equals("spieler");
                    s.add(Span.bold(l[1], player ? WHITE : accent));
                    if (!player) s.add(Span.of(l[2].equals("team") ? " · Team" : " · Helfer", l[2].equals("team") ? PURPLE : GREEN));
                    s.add(Span.of("  " + l[3], GRAY));
                    c.text(12, ly, s);
                }
            } else {
                c.text(22, ly, l[1], BODY, false);
            }
            ly += 11;
        }
        y += h + 4;

        // Knöpfe (drei je Zeile) oder Bewertung
        List<Object[]> buttons = new ArrayList<>();
        if (d.reply()) buttons.add(new Object[]{"Antworten", KitData.Box.SU_CBTN_BLUE, BLUE, a.reply(d.id()), "Nachricht schreiben"});
        if (d.claim()) buttons.add(new Object[]{"Übernehmen", KitData.Box.SU_CBTN_GREEN, GREEN, a.claim(d.id()), "Ich kümmere mich"});
        if (d.escalate()) buttons.add(new Object[]{"An Team", KitData.Box.SU_CBTN_PURPLE, PURPLE, a.escalate(d.id()), "Ich komme nicht weiter"});
        if (d.solve()) buttons.add(new Object[]{"Gelöst", KitData.Box.SU_CBTN_GREEN, GREEN, a.rate(d.id()), "Frage gelöst, Sterne vergeben"});
        if (d.close()) buttons.add(new Object[]{"Schließen", KitData.Box.SU_CBTN_PURPLE, PURPLE, a.close(d.id()),
                d.own() ? "Ticket zurückziehen" : "Ticket schließen"});
        for (int i = 0; i < buttons.size(); i++) {
            Object[] b = buttons.get(i);
            int bx = (i % 3) * 139, by = y + (i / 3) * 24;
            TextColor col = (TextColor) b[2];
            c.box(bx, by, 112, 20, (KitData.Box) b[1]);
            center(c, bx + 52, by + 6, upper((String) b[0]), col, true);
            c.text(bx + 101, by + 6, "›", col, true);
            @SuppressWarnings("unchecked")
            Consumer<Player> click = (Consumer<Player>) b[3];
            c.action(bx, by, 112, 20, click, (String) b[4]);
        }
        if (buttons.isEmpty() && d.stars() > 0) {
            c.text(4, y + 6, List.of(Span.of("Bewertung: ", GRAY), Span.bold("★".repeat(d.stars()), GOLD),
                    Span.of("☆".repeat(5 - d.stars()), DIM)));
        }
        return c;
    }

    private static final String[] RATE = {"Kaum geholfen", "Ging so", "Hat gepasst", "Gut geholfen", "Super, alles geklärt"};

    public static Canvas rate(int id, String helper, Actions a) {
        Canvas c = canvas("Bewerten");
        head(c, KitData.Box.SU_HEAD_GOLD, a.ticket(id), "Wie gut wurde dir geholfen?", GOLD,
                "Ticket #" + id + (helper == null ? "" : " · Helfer: " + helper), GRAY, 330);
        for (int i = 0; i < 5; i++) {
            int stars = 5 - i, y = 48 + 27 * i;
            KitData.Box box = stars >= 4 ? KitData.Box.SU_LB_GOLD : stars == 3 ? KitData.Box.SU_LB_SILVER
                    : stars == 2 ? KitData.Box.SU_LB_BRONZE : KitData.Box.SU_LB_GRAY;
            TextColor col = stars >= 4 ? GOLD : stars == 3 ? SILVER : stars == 2 ? BRONZE : GRAY;
            c.box(0, y, W, 20, box);
            c.text(12, y + 6, List.of(Span.bold("★".repeat(stars), col), Span.of("☆".repeat(5 - stars), DIM)));
            c.text(80, y + 6, RATE[stars - 1], BODY, false);
            c.text(376, y + 6, "›", col, true);
            c.action(0, y, W, 20, a.solve(id, stars), stars + (stars == 1 ? " Stern" : " Sterne"));
        }
        c.box(0, 48 + 27 * 5 + 6, W, 20, KitData.Box.SU_FOOTER);
        c.text(12, 48 + 27 * 5 + 12, "Der Helfer bekommt 1 Punkt plus die Sterne.", BODY, false);
        return c;
    }

    private static String upper(String text) {
        return I18n.ctx(text).toUpperCase(Locale.GERMAN);
    }
}
