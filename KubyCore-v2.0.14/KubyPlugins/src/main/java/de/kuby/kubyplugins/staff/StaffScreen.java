package de.kuby.kubyplugins.staff;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Team-Bereich im Casino-Look: roter Kopf mit Goldrand und großer Goldschrift, dunkle Karten mit Goldrand, grüne
 * Reiter, goldene Knöpfe. Nur Layout; StaffMenus liefert die Daten (Views) und die Klicks (Nav mit Schlüsseln wie
 * "kanal:allgemein", "meldung:12", "akte:&lt;uuid&gt;").
 */
public final class StaffScreen {

    public static final int W = 390, HEIGHT = 330;

    static final TextColor HEAD = TextColor.color(0xF2B829);
    static final TextColor GRAY = TextColor.color(0xACA48E);
    static final TextColor DIM = TextColor.color(0x7E7868);
    static final TextColor LIGHT = TextColor.color(0xE8E0D8);
    static final TextColor GOLD = TextColor.color(0xF4D46A);
    static final TextColor GOLD_SOFT = TextColor.color(0xF2DA90);
    static final TextColor ON_GOLD = TextColor.color(0x2C1C06);
    static final TextColor ON_GREEN = TextColor.color(0x06240E);
    static final TextColor BTN_GRAY = TextColor.color(0x8E8A84);
    static final TextColor WHITE = NamedTextColor.WHITE;
    static final TextColor RED = TextColor.color(0xFF5A5A);
    static final TextColor GREEN = TextColor.color(0x6BF07A);
    static final TextColor ORANGE = TextColor.color(0xFF9A4A);
    static final TextColor SEP = TextColor.color(0x3A2C22);

    private static final int TOP = 76;

    private StaffScreen() {
    }

    /** Klick-Ziele: ein Schlüssel je Aktion. */
    @FunctionalInterface
    public interface Nav {
        Consumer<Player> go(String key);
    }

    public enum Tab {
        HOME("Übersicht", "home"), CHANNELS("Kanäle", "kanal"), REPORTS("Meldungen", "meldungen"), TICKETS("Tickets", "tickets"),
        PLAYERS("Spieler", "spieler"), KADMIN("Kadmin", "kadmin");

        public final String label, key;

        Tab(String label, String key) {
            this.label = label;
            this.key = key;
        }
    }

    /** Kopfzeile und Reiter: Rolle, Zähler für die Reiter und welche Reiter es gibt. */
    public record Frame(String role, TextColor roleColor, int online, int unread, int openReports, int teamTickets,
                        boolean tickets, boolean admin) {}

    public static TextColor color(String hex) {
        return TextColor.fromHexString(hex);
    }

    // =================================================================================================================
    // Bausteine
    // =================================================================================================================

    private static Canvas base(String heading) {
        return new Canvas(W).heading(heading, HEAD);
    }

    private static boolean titleOk(String text) {
        for (char ch : text.toUpperCase(Locale.ROOT).toCharArray()) if (KitData.TITLE_CHARS.indexOf(ch) < 0) return false;
        return true;
    }

    /** Kopf: Icon 32 px, großer Titel (Goldschrift; Namen mit Sonderzeichen in fetter Schrift), graue Unterzeile. */
    private static void head(Canvas c, KitData.Icon icon, String title, String sub, int subMax) {
        c.box(0, 0, W, 51, KitData.Box.CASINO);
        c.icon(10, 10, icon, true);
        // Platz bis zu den Knöpfen rechts (subMax ist die Breite der Unterzeile daneben)
        if (titleOk(title) && Canvas.titleWidth(title) <= subMax) c.title(46, 11, title);
        else c.text(47, 14, Canvas.fit(title, true, subMax), GOLD, true);
        c.text(47, 33, Canvas.fit(sub, false, subMax), GRAY, false);
    }

    private static void tabs(Canvas c, Frame f, Tab active, Nav nav) {
        List<Tab> list = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Tab t : Tab.values()) {
            if (t == Tab.TICKETS && !f.tickets()) continue;
            if (t == Tab.KADMIN && !f.admin()) continue;
            int n = switch (t) {
                case CHANNELS -> f.unread();
                case REPORTS -> f.openReports();
                case TICKETS -> f.teamTickets();
                default -> 0;
            };
            list.add(t);
            labels.add(n > 0 ? t.label + " " + Math.min(99, n) : t.label);
        }
        int n = list.size(), gap = 2, ty = 55;
        boolean bold = true;
        int text = 0;
        for (String l : labels) text += Canvas.width(l, true);
        if (text + 6 * n + gap * (n - 1) > W) {
            bold = false;
            text = 0;
            for (String l : labels) text += Canvas.width(l, false);
        }
        int spare = W - gap * (n - 1) - text, x = 0;
        for (int i = 0; i < n; i++) {
            String label = labels.get(i);
            int w = Canvas.width(label, bold) + spare / n + (i < spare % n ? 1 : 0);
            boolean on = list.get(i) == active;
            c.smallButton(x, ty, w, on ? KitData.Small.GREEN : KitData.Small.DARK, label, on ? ON_GREEN : LIGHT, bold);
            if (!on || list.get(i) != Tab.HOME) c.action(x, ty, w, 16, nav.go(list.get(i).key), list.get(i).label + " öffnen");
            x += w + gap;
        }
    }

    /** Kleiner Knopf mit Beschriftung; act null = gesperrt (grau). Gibt die Breite zurück. */
    private static int button(Canvas c, int x, int y, int w, String label, KitData.Small style, TextColor color,
                              Consumer<Player> act, String hover) {
        if (w <= 0) w = Canvas.width(label, true) + 14;
        if (act == null) {
            c.smallButton(x, y, w, KitData.Small.GOLD_GRAY, label, BTN_GRAY, true);
            if (hover != null) c.action(x, y, w, 16, null, hover);
        } else {
            c.smallButton(x, y, w, style, label, color, true);
            c.action(x, y, w, 16, act, hover);
        }
        return w;
    }

    private static int gold(Canvas c, int x, int y, String label, Consumer<Player> act, String hover) {
        return button(c, x, y, 0, label, KitData.Small.GOLD, GOLD_SOFT, act, hover);
    }

    private static int dark(Canvas c, int x, int y, String label, Consumer<Player> act, String hover) {
        return button(c, x, y, 0, label, KitData.Small.DARK, LIGHT, act, hover);
    }

    private static int width(String label) {
        return Canvas.width(label, true) + 14;
    }

    /** Filter-Chips (aktiver grün), rechtsbündig bis right. */
    private static void chips(Canvas c, int right, int y, List<String> labels, List<String> keys, int active, Nav nav) {
        int total = 0;
        for (String l : labels) total += width(l) + 3;
        int x = right - total + 3;
        for (int i = 0; i < labels.size(); i++) {
            boolean on = i == active;
            int w = width(labels.get(i));
            c.smallButton(x, y, w, on ? KitData.Small.GREEN : KitData.Small.DARK, labels.get(i), on ? ON_GREEN : LIGHT, true);
            if (!on) c.action(x, y, w, 16, nav.go(keys.get(i)), labels.get(i) + " zeigen");
            x += w + 3;
        }
    }

    private static void card(Canvas c, int x, int y, int w, int h, String caps) {
        c.box(x, y, w, h, KitData.Box.CASINO_DARK);
        if (caps != null) c.text(x + 10, y + 8, Canvas.caps(caps), GOLD, true);
    }

    private static void pager(Canvas c, int y, int page, int pages, Nav nav, String prefix) {
        if (pages <= 1) return;
        String label = "Seite " + (page + 1) + " / " + pages;
        int lw = Canvas.width(label, false);
        int cx = W / 2;
        c.textCenter(cx, y + 4, label, GRAY, false);
        int lx = cx - lw / 2 - 8 - 24, rx = cx + lw / 2 + 8;
        button(c, lx, y, 24, "‹", KitData.Small.DARK, LIGHT, page > 0 ? nav.go(prefix + (page - 1)) : null,
                page > 0 ? "Vorherige Seite" : null);
        button(c, rx, y, 24, "›", KitData.Small.DARK, LIGHT, page + 1 < pages ? nav.go(prefix + (page + 1)) : null,
                page + 1 < pages ? "Nächste Seite" : null);
    }

    // =================================================================================================================
    // Übersicht
    // =================================================================================================================

    public record ChannelRow(String key, String desc, int unread, boolean write) {}

    /** Teamler oder Helfer: Name, Rolle, Farbe, Zusatz (Welt, Punkte …), Schlüssel für die Akte. */
    public record Member(String name, String role, TextColor color, String info, String key) {}

    public record Feed(String kind, String text, String ago) {}

    public record Home(Frame f, List<ChannelRow> channels, int openReports, int inWork, int teamTickets, int openTickets,
                       List<Member> team, List<Feed> feed) {}

    public static Canvas home(Home v, Nav nav) {
        Canvas c = base("Team-Bereich");
        Frame f = v.f();
        head(c, KitData.Icon.SPYGLASS, "TEAM-BEREICH", f.role() + " · " + f.online() + (f.online() == 1 ? " Teamler" : " Teamler")
                + " online · alles fürs Team an einem Ort", 330);
        tabs(c, f, Tab.HOME, nav);
        int y = TOP;
        int rows = Math.max(4, v.channels().size());
        int h = 26 + 16 * rows;
        // Kanäle
        card(c, 0, y, 193, h, "Kanäle");
        c.textRight(183, y + 8, "neu", DIM, false);
        for (int i = 0; i < v.channels().size(); i++) {
            ChannelRow ch = v.channels().get(i);
            int ry = y + 21 + 16 * i;
            c.text(12, ry + 4, "#", DIM, true);
            c.text(20, ry + 4, ch.key(), ch.unread() > 0 ? WHITE : LIGHT, ch.unread() > 0);
            if (ch.unread() > 0) {
                String n = String.valueOf(Math.min(99, ch.unread()));
                int bw = Math.max(18, Canvas.width(n, true) + 10);
                c.smallButton(184 - bw, ry, bw, KitData.Small.GOLD_ON, n, ON_GOLD, true);
            } else {
                c.textRight(182, ry + 4, "–", DIM, false);
            }
            c.action(4, ry, 185, 16, nav.go("kanal:" + ch.key()), "#" + ch.key() + " · " + ch.desc());
        }
        // Lage
        card(c, 197, y, 193, h, "Lage");
        List<Object[]> stats = new ArrayList<>();
        stats.add(new Object[]{"Offene Meldungen", v.openReports(), v.openReports() > 0 ? RED : GREEN, "meldungen", "Meldungen ansehen"});
        stats.add(new Object[]{"Meldungen in Arbeit", v.inWork(), v.inWork() > 0 ? GOLD : GREEN, "meldungen", "Meldungen ansehen"});
        if (f.tickets()) {
            stats.add(new Object[]{"Tickets beim Team", v.teamTickets(), v.teamTickets() > 0 ? RED : GREEN, "tickets:team", "Tickets ansehen"});
            stats.add(new Object[]{"Offene Tickets", v.openTickets(), v.openTickets() > 0 ? GOLD : GREEN, "tickets:offen", "Tickets ansehen"});
        } else {
            stats.add(new Object[]{"Team online", f.online(), GREEN, "spieler", "Spieler ansehen"});
        }
        for (int i = 0; i < stats.size(); i++) {
            Object[] s = stats.get(i);
            int ry = y + 21 + 16 * i;
            c.text(209, ry + 4, (String) s[0], LIGHT, false);
            c.textRight(368, ry + 4, String.valueOf(s[1]), (TextColor) s[2], true);
            c.text(374, ry + 4, "›", DIM, true);
            c.action(201, ry, 185, 16, nav.go((String) s[3]), (String) s[4]);
        }
        y += h + 3;
        // Team online
        List<int[]> pos = new ArrayList<>();
        int lx = 12, line = 0;
        for (Member m : v.team()) {
            int w = Canvas.width(m.name(), true);
            if (lx + w > 378 && lx > 12) {
                line++;
                lx = 12;
            }
            pos.add(new int[]{lx, line});
            lx += w + 14;
        }
        int lines = Math.min(3, line + 1);
        int th = 26 + 11 * lines;
        card(c, 0, y, W, th, "Team online");
        c.textRight(380, y + 8, "Klick: Akte", DIM, false);
        if (v.team().isEmpty()) c.text(12, y + 21, "Gerade ist niemand vom Team online.", GRAY, false);
        for (int i = 0; i < v.team().size(); i++) {
            int[] p = pos.get(i);
            if (p[1] >= 3) break;
            Member m = v.team().get(i);
            int ty = y + 21 + 11 * p[1];
            c.text(p[0], ty, m.name(), m.color(), true);
            c.action(p[0] - 2, ty - 2, Canvas.width(m.name(), true) + 4, 11, nav.go(m.key()),
                    m.name() + " · " + m.role() + (m.info() == null ? "" : " · " + m.info()));
        }
        y += th + 3;
        // Aktivität
        int room = HEIGHT - y;
        int max = Math.max(1, (room - 26) / 10);
        int n = Math.min(max, v.feed().size());
        int fh = 26 + 10 * Math.max(1, n);
        card(c, 0, y, W, fh, "Aktivität");
        if (v.feed().isEmpty()) c.text(12, y + 21, "Noch ist nichts passiert.", GRAY, false);
        for (int i = 0; i < n; i++) {
            Feed e = v.feed().get(i);
            int ry = y + 21 + 10 * i;
            c.text(12, ry, "▶", color(StaffService.kindColor(e.kind())), false);
            int agoW = Canvas.width(e.ago(), false);
            c.text(22, ry, Canvas.fit(e.text(), false, 356 - agoW - 22), LIGHT, false);
            c.textRight(380, ry, e.ago(), DIM, false);
        }
        return c;
    }

    // =================================================================================================================
    // Kanäle
    // =================================================================================================================

    public record Line(String author, TextColor color, String ago, String text) {}

    /**
     * @param lines  Nachrichten in zeitlicher Reihenfolge (die neuesten offset weggelassen); gezeigt werden so viele
     *               der letzten, wie passen
     */
    public record ChannelView(Frame f, List<ChannelRow> channels, String key, String desc, boolean write, boolean muted,
                              List<Line> lines, int offset) {}

    public static Canvas channel(ChannelView v, Nav nav) {
        Canvas c = base("Team-Bereich");
        Frame f = v.f();
        int bw = v.write() ? width("Schreiben") : width("Nur lesen");
        String mute = v.muted() ? "Chat: aus" : "Chat: an";
        int mw = width(mute);
        head(c, KitData.Icon.WRITABLE, "#" + v.key(), v.desc(), 378 - Math.max(bw, mw) - 8 - 47);
        if (v.write()) gold(c, 380 - bw, 9, "Schreiben", nav.go("schreiben:" + v.key()), "Nachricht in #" + v.key() + " schreiben");
        else button(c, 380 - bw, 9, bw, "Nur lesen", KitData.Small.DARK, LIGHT, null, "Hier schreiben nur Admins");
        dark(c, 380 - mw, 28, mute, nav.go("stumm:" + v.key()), v.muted()
                ? "Neue Nachrichten wieder im Chat zeigen" : "Neue Nachrichten nicht mehr im Chat zeigen (nur hier)");
        tabs(c, f, Tab.CHANNELS, nav);
        int y = TOP, h = HEIGHT - TOP;
        // Kanalliste
        card(c, 0, y, 106, h, "Kanäle");
        for (int i = 0; i < v.channels().size(); i++) {
            ChannelRow ch = v.channels().get(i);
            int ry = y + 21 + 18 * i;
            boolean on = ch.key().equals(v.key());
            String label = "#" + ch.key();
            boolean bold = Canvas.width(label, true) <= 84;
            if (on) {
                c.smallButton(5, ry, 96, KitData.Small.GREEN);
                c.text(11, ry + 4, label, ON_GREEN, bold);
            } else {
                String n = ch.unread() > 0 ? String.valueOf(Math.min(99, ch.unread())) : "";
                int room = 87 - (n.isEmpty() ? 0 : Canvas.width(n, true) + 4);
                boolean b = ch.unread() > 0 && Canvas.width(label, true) <= room;
                c.text(11, ry + 4, Canvas.fit(label, b, room), ch.unread() > 0 ? WHITE : LIGHT, b);
                if (!n.isEmpty()) c.textRight(98, ry + 4, n, GOLD, true);
                c.action(5, ry, 96, 16, nav.go("kanal:" + ch.key()), label + " · " + ch.desc());
            }
        }
        c.text(11, y + h - 32, "/sc <Text>", DIM, false);
        c.text(11, y + h - 21, "schreibt in", DIM, false);
        c.text(11, y + h - 10 - 1, "#allgemein", DIM, false);
        // Nachrichten
        int mx = 110, mw2 = W - mx;
        card(c, mx, y, mw2, h, "Nachrichten");
        int areaTop = y + 24, areaBottom = y + h - 9;
        int space = areaBottom - areaTop;
        int textX = mx + 12, textW = mw2 - 24;
        List<List<String>> wrapped = new ArrayList<>();
        int used = 0, shown = 0;
        for (int i = v.lines().size() - 1; i >= 0; i--) {
            Line l = v.lines().get(i);
            List<String> w = Canvas.wrap(l.text(), textW);
            int bh = 10 + 10 * w.size() + 4;
            if (used + bh > space && shown > 0) break;
            if (used + bh > space) w = w.subList(0, Math.max(1, (space - 14) / 10));
            wrapped.add(0, w);
            used += 10 + 10 * w.size() + 4;
            shown++;
        }
        boolean older = shown < v.lines().size();
        int nx = W - 8;
        if (v.offset() > 0) {
            String l = "▼ Neuere";
            int w = width(l);
            dark(c, nx - w, y + 4, l, nav.go("kanal:" + v.key() + ":" + Math.max(0, v.offset() - Math.max(1, shown))), "Neuere Nachrichten");
            nx -= w + 3;
        }
        if (older) {
            String l = "▲ Ältere";
            int w = width(l);
            dark(c, nx - w, y + 4, l, nav.go("kanal:" + v.key() + ":" + (v.offset() + shown)), "Ältere Nachrichten");
        }
        if (v.lines().isEmpty()) {
            c.textCenter(mx + mw2 / 2, areaTop + space / 2 - 10, "Noch keine Nachrichten.", GRAY, false);
            if (v.write()) c.textCenter(mx + mw2 / 2, areaTop + space / 2 + 2, "Schreib die erste mit „Schreiben“.", DIM, false);
        }
        int ty = areaBottom - used;
        int first = v.lines().size() - shown;
        for (int k = 0; k < shown; k++) {
            Line l = v.lines().get(first + k);
            c.text(textX, ty, l.author(), l.color(), true);
            c.textRight(W - 12, ty, l.ago(), DIM, false);
            ty += 10;
            for (String s : wrapped.get(k)) {
                c.text(textX, ty, s, LIGHT, false);
                ty += 10;
            }
            ty += 4;
        }
        return c;
    }

    /** Kopf für „Nachricht schreiben“: Kanal und die letzten Nachrichten als Erinnerung. */
    public static Canvas write(String key, String desc, List<Line> last) {
        Canvas c = base("Team-Bereich");
        head(c, KitData.Icon.WRITABLE, "#" + key, "Nachricht schreiben · " + desc, 330);
        int y = 55;
        int n = Math.min(4, last.size());
        int h = 24 + 10 * Math.max(1, n);
        card(c, 0, y, W, h, "Zuletzt");
        if (n == 0) c.text(12, y + 20, "Noch keine Nachrichten.", GRAY, false);
        for (int i = 0; i < n; i++) {
            Line l = last.get(last.size() - n + i);
            int ry = y + 20 + 10 * i;
            int aw = c.text(12, ry, l.author(), l.color(), true);
            int agoW = Canvas.width(l.ago(), false);
            c.text(16 + aw, ry, Canvas.fit(l.text(), false, 380 - agoW - 8 - 16 - aw), LIGHT, false);
            c.textRight(380, ry, l.ago(), DIM, false);
        }
        return c;
    }

    /** Kopf für eine Eingabe (Grund, Notiz, Suche, Ankündigung). */
    public static Canvas prompt(KitData.Icon icon, String title, String sub, List<String> info) {
        Canvas c = base("Team-Bereich");
        head(c, icon, title, sub, 330);
        if (!info.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (String s : info) lines.addAll(Canvas.wrap(s, W - 24));
            int h = 16 + 10 * lines.size();
            c.box(0, 55, W, h, KitData.Box.CASINO_DARK);
            for (int i = 0; i < lines.size(); i++) c.text(12, 63 + 10 * i, lines.get(i), LIGHT, false);
        }
        return c;
    }

    // =================================================================================================================
    // Meldungen
    // =================================================================================================================

    public record ReportRow(int id, String target, String reporter, String reason, String status, TextColor color, String ago) {}

    public record Reports(Frame f, boolean openOnly, List<ReportRow> rows, int page, int pages, int open, int inWork, int total) {}

    public static final int REPORTS_PER_PAGE = 8;

    public static Canvas reports(Reports v, Nav nav) {
        Canvas c = base("Team-Bereich");
        head(c, KitData.Icon.BELL, "MELDUNGEN", v.open() + " offen · " + v.inWork() + " in Arbeit · " + v.total() + " insgesamt", 200);
        chips(c, 380, 9, List.of("Offene", "Alle"), List.of("meldungen:offen:0", "meldungen:alle:0"), v.openOnly() ? 0 : 1, nav);
        tabs(c, v.f(), Tab.REPORTS, nav);
        int y = TOP;
        int n = v.rows().size();
        int h = 24 + 26 * Math.max(1, n);
        card(c, 0, y, W, h, v.openOnly() ? "Offene Meldungen" : "Alle Meldungen");
        c.textRight(380, y + 8, "Klick: öffnen", DIM, false);
        if (n == 0) c.text(12, y + 24, v.openOnly() ? "Keine offenen Meldungen. Alles ruhig!" : "Noch keine Meldungen.", GREEN, false);
        for (int i = 0; i < n; i++) {
            ReportRow r = v.rows().get(i);
            int ry = y + 20 + 26 * i;
            String id = "#" + r.id();
            c.text(12, ry + 3, id, DIM, true);
            int tx = 48;
            String status = Canvas.fit(r.status(), true, 110);
            int sw = Canvas.width(status, true);
            int tw = c.text(tx, ry + 3, Canvas.fit(r.target(), true, 120), WHITE, true);
            c.text(tx + tw + 5, ry + 3, Canvas.fit("gemeldet von " + r.reporter(), false, 380 - sw - 8 - tx - tw - 5), GRAY, false);
            c.textRight(380, ry + 3, status, r.color(), true);
            int agoW = Canvas.width(r.ago(), false);
            c.text(tx, ry + 14, Canvas.fit(r.reason(), false, 332 - agoW - tx), LIGHT, false);
            c.textRight(380, ry + 14, r.ago(), DIM, false);
            if (i + 1 < n) c.line(10, ry + 25, W - 20, SEP);
            c.action(4, ry, W - 8, 25, nav.go("meldung:" + r.id()), "Meldung " + id + " öffnen");
        }
        pager(c, y + h + 4, v.page(), v.pages(), nav, "meldungen:" + (v.openOnly() ? "offen" : "alle") + ":");
        return c;
    }

    public record ReportDetail(Frame f, int id, String target, boolean targetOnline, String targetKey, String reporter,
                               String when, String ago, String where, String reason, String status, TextColor color,
                               String handler, String note, int againstCount, boolean canClaim, boolean canDone,
                               boolean canReopen) {}

    public static Canvas report(ReportDetail v, Nav nav) {
        Canvas c = base("Team-Bereich");
        int bw = width("‹ Zurück");
        head(c, KitData.Icon.BELL, "MELDUNG #" + v.id(), "Gemeldet " + v.ago() + " · gegen " + v.target(), 378 - bw - 8 - 47 - 40);
        dark(c, 380 - bw, 9, "‹ Zurück", nav.go("meldungen"), "Zur Liste");
        c.textRight(380, 33, v.status(), v.color(), true);
        tabs(c, v.f(), Tab.REPORTS, nav);
        int y = TOP;
        List<String> reason = Canvas.wrap(v.reason(), W - 102);
        List<String> note = v.note() == null || v.note().isEmpty() ? List.of() : Canvas.wrap(v.note(), W - 102);
        int h = 24 + 11 * 5 + 11 * reason.size() + (note.isEmpty() ? 0 : 11 * note.size()) + 4;
        card(c, 0, y, W, h, "Details");
        int ry = y + 21;
        c.text(12, ry, "Gemeldet", GRAY, false);
        int tw = c.text(90, ry, v.target(), WHITE, true);
        c.text(96 + tw, ry, v.targetOnline() ? "online" : "offline", v.targetOnline() ? GREEN : DIM, false);
        c.textRight(380, ry, v.againstCount() + (v.againstCount() == 1 ? " Meldung" : " Meldungen") + " insgesamt",
                v.againstCount() > 2 ? RED : GRAY, false);
        ry += 11;
        c.text(12, ry, "Von", GRAY, false);
        c.text(90, ry, v.reporter(), LIGHT, false);
        ry += 11;
        c.text(12, ry, "Wann", GRAY, false);
        c.text(90, ry, v.when() + " (" + v.ago() + ")", LIGHT, false);
        ry += 11;
        c.text(12, ry, "Wo", GRAY, false);
        c.text(90, ry, v.where(), LIGHT, false);
        ry += 11;
        c.text(12, ry, "Bearbeiter", GRAY, false);
        c.text(90, ry, v.handler() == null ? "noch niemand" : v.handler(), v.handler() == null ? DIM : GOLD_SOFT, v.handler() != null);
        ry += 11;
        c.text(12, ry, "Grund", GRAY, false);
        for (String s : reason) {
            c.text(90, ry, s, WHITE, false);
            ry += 11;
        }
        if (!note.isEmpty()) {
            c.text(12, ry, "Notiz", GRAY, false);
            for (String s : note) {
                c.text(90, ry, s, GREEN, false);
                ry += 11;
            }
        }
        y += h + 3;
        // Knöpfe: Beschriftung, Stil, Farbe, Aktion (null = gesperrt), Hinweis; brechen um, wenn die Zeile voll ist
        List<Object[]> btn = new ArrayList<>();
        if (v.canClaim()) btn.add(new Object[]{"Übernehmen", KitData.Small.GOLD, GOLD_SOFT, nav.go("uebernehmen:" + v.id()),
                "Du kümmerst dich darum (Stand: in Arbeit)"});
        if (v.canDone()) btn.add(new Object[]{"Erledigt", KitData.Small.GREEN, ON_GREEN, nav.go("erledigt:" + v.id()),
                "Abschließen, mit kurzer Notiz (was du gemacht hast)"});
        if (v.canReopen()) btn.add(new Object[]{"Wieder öffnen", KitData.Small.DARK, LIGHT, nav.go("oeffnen:" + v.id()), "Meldung wieder öffnen"});
        btn.add(new Object[]{"Zum Ort", KitData.Small.DARK, LIGHT, nav.go("ort:" + v.id()), "Dorthin, wo gemeldet wurde: " + v.where()});
        btn.add(new Object[]{"Zum Spieler", KitData.Small.DARK, LIGHT, v.targetOnline() ? nav.go("hin:" + v.targetKey()) : null,
                v.targetOnline() ? "Zu " + v.target() + " teleportieren" : v.target() + " ist nicht online"});
        btn.add(new Object[]{"Akte", KitData.Small.DARK, LIGHT, nav.go("akte:" + v.targetKey()),
                "Akte von " + v.target() + ": Meldungen, Verwarnungen, Aktionen"});
        int rowsB = 1, lx = 10;
        for (Object[] o : btn) {
            int w = width((String) o[0]);
            if (lx + w > W - 10 && lx > 10) {
                rowsB++;
                lx = 10;
            }
            lx += w + 4;
        }
        card(c, 0, y, W, 26 + 19 * rowsB, "Aktionen");
        int bx = 10, by = y + 20;
        for (Object[] o : btn) {
            int w = width((String) o[0]);
            if (bx + w > W - 10 && bx > 10) {
                by += 19;
                bx = 10;
            }
            @SuppressWarnings("unchecked")
            Consumer<Player> act = (Consumer<Player>) o[3];
            bx += button(c, bx, by, w, (String) o[0], (KitData.Small) o[1], (TextColor) o[2], act, (String) o[4]) + 4;
        }
        return c;
    }

    // =================================================================================================================
    // Tickets
    // =================================================================================================================

    public record TicketRow(int id, String title, String status, TextColor color, String info, String ago) {}

    public record Tickets(Frame f, String tab, boolean team, int teamCount, int openCount, int workCount, List<TicketRow> rows,
                          int page, int pages, List<Member> helpers) {}

    public static final int TICKETS_PER_PAGE = 6;

    public static Canvas tickets(Tickets v, Nav nav) {
        Canvas c = base("Team-Bereich");
        head(c, KitData.Icon.WRITTEN_BOOK, "TICKETS", "Fragen der Spieler aus /support · Klick öffnet das Ticket", 330);
        tabs(c, v.f(), Tab.TICKETS, nav);
        int y = TOP;
        int n = v.rows().size();
        int h = 24 + 26 * Math.max(1, n);
        String title = switch (v.tab()) {
            case "team" -> "Beim Team";
            case "arbeit" -> "In Arbeit";
            default -> "Offen";
        };
        card(c, 0, y, W, h, title);
        List<String> labels = new ArrayList<>(), keys = new ArrayList<>();
        int active = 0;
        if (v.team()) {
            labels.add("Beim Team " + v.teamCount());
            keys.add("tickets:team:0");
        }
        labels.add("Offen " + v.openCount());
        keys.add("tickets:offen:0");
        labels.add("In Arbeit " + v.workCount());
        keys.add("tickets:arbeit:0");
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).startsWith("tickets:" + v.tab() + ":")) active = i;
        chips(c, 384, y + 3, labels, keys, active, nav);
        if (n == 0) c.text(12, y + 24, "Hier wartet gerade kein Ticket.", GREEN, false);
        for (int i = 0; i < n; i++) {
            TicketRow r = v.rows().get(i);
            int ry = y + 20 + 26 * i;
            c.text(12, ry + 3, "#" + r.id(), DIM, true);
            int sw = Canvas.width(r.status(), true);
            c.text(48, ry + 3, Canvas.fit(r.title(), true, 332 - sw - 48), WHITE, true);
            c.textRight(380, ry + 3, r.status(), r.color(), true);
            int agoW = Canvas.width(r.ago(), false);
            c.text(48, ry + 14, Canvas.fit(r.info(), false, 332 - agoW - 48), GRAY, false);
            c.textRight(380, ry + 14, r.ago(), DIM, false);
            if (i + 1 < n) c.line(10, ry + 25, W - 20, SEP);
            c.action(4, ry, W - 8, 25, nav.go("ticket:" + r.id()), "Ticket #" + r.id() + " öffnen");
        }
        y += h + 3;
        if (v.pages() > 1) {
            pager(c, y, v.page(), v.pages(), nav, "tickets:" + v.tab() + ":");
            y += 20;
        }
        int room = HEIGHT - y;
        if (room >= 40) {
            card(c, 0, y, W, Math.min(room, 37), "Helfer online");
            c.textRight(380, y + 8, "Punkte diese Woche", DIM, false);
            if (v.helpers().isEmpty()) c.text(12, y + 21, "Gerade ist kein Helfer online.", GRAY, false);
            int lx = 12;
            for (Member m : v.helpers()) {
                String s = m.name() + " " + m.info();
                int w = Canvas.width(m.name(), true) + 4 + Canvas.width(m.info(), false);
                if (lx + w > 378) break;
                int nw = c.text(lx, y + 21, m.name(), m.color(), true);
                c.text(lx + nw + 4, y + 21, m.info(), GOLD, false);
                c.action(lx - 2, y + 19, w + 4, 11, nav.go(m.key()), s);
                lx += w + 14;
            }
        }
        return c;
    }

    // =================================================================================================================
    // Spieler
    // =================================================================================================================

    public record PlayerRow(String name, TextColor color, String info, int reports, int warns, String key) {}

    public record Players(Frame f, List<PlayerRow> rows, int page, int pages, int online) {}

    public static final int PLAYERS_PER_PAGE = 22;

    public static Canvas players(Players v, Nav nav) {
        Canvas c = base("Team-Bereich");
        int sw = width("Suchen");
        head(c, KitData.Icon.NAME_TAG, "SPIELER", v.online() + " online · Klick öffnet die Akte", 378 - sw - 8 - 47);
        gold(c, 380 - sw, 9, "Suchen", nav.go("suchen"), "Spieler suchen (auch offline)");
        tabs(c, v.f(), Tab.PLAYERS, nav);
        int y = TOP;
        int per = Math.max(1, Math.min(PLAYERS_PER_PAGE / 2, (v.rows().size() + 1) / 2));
        int rows = per;
        int h = 24 + 18 * rows;
        card(c, 0, y, W, h, "Online");
        c.textRight(380, y + 8, "rot: offene Meldungen · orange: Verwarnungen", DIM, false);
        if (v.rows().isEmpty()) c.text(12, y + 22, "Niemand online.", GRAY, false);
        for (int i = 0; i < v.rows().size() && i < PLAYERS_PER_PAGE; i++) {
            PlayerRow r = v.rows().get(i);
            int col = i / per, row = i % per;
            int x = col == 0 ? 6 : 198, ry = y + 20 + 18 * row;
            int rw = 186;
            c.text(x + 6, ry + 4, Canvas.fit(r.name(), true, 100), r.color(), true);
            int rx = x + rw - 6;
            if (r.warns() > 0) {
                rx -= c.textRight(rx, ry + 4, "!" + r.warns(), ORANGE, true) + 6;
            }
            if (r.reports() > 0) {
                rx -= c.textRight(rx, ry + 4, "⚑" + r.reports(), RED, true) + 6;
            }
            c.textRight(rx, ry + 4, Canvas.fit(r.info(), false, rx - x - 110), DIM, false);
            if (row + 1 < per && i + 1 < v.rows().size()) c.line(x + 4, ry + 17, rw - 8, SEP);
            c.action(x, ry, rw, 17, nav.go(r.key()), r.name() + " · Akte öffnen");
        }
        pager(c, y + h + 4, v.page(), v.pages(), nav, "spieler:");
        return c;
    }

    /**
     * @param facts   [Bezeichnung, Wert]
     * @param reports [Text, vor …] neueste zuerst
     * @param warns   [Text, vor …] neueste zuerst
     */
    public record Akte(Frame f, String name, String key, boolean online, String status, List<String[]> facts,
                       List<String[]> reports, int reportCount, List<String[]> warns, int warnCount, boolean mod,
                       boolean admin, boolean self) {}

    public static Canvas akte(Akte v, Nav nav) {
        Canvas c = base("Team-Bereich");
        int bw = width("‹ Zurück");
        head(c, KitData.Icon.NAME_TAG, v.name(), v.status(), 378 - bw - 8 - 47);
        dark(c, 380 - bw, 9, "‹ Zurück", nav.go("spieler"), "Zur Spielerliste");
        c.textRight(380, 33, v.online() ? "online" : "offline", v.online() ? GREEN : DIM, true);
        tabs(c, v.f(), Tab.PLAYERS, nav);
        int y = TOP;
        int rightLines = 2 + Math.max(1, Math.min(3, v.reports().size())) + Math.max(1, Math.min(3, v.warns().size()));
        int h = Math.max(24 + 11 * v.facts().size(), 24 + 11 * rightLines + 6) + 2;
        card(c, 0, y, 193, h, "Akte");
        for (int i = 0; i < v.facts().size(); i++) {
            String[] fct = v.facts().get(i);
            int ry = y + 21 + 11 * i;
            c.text(12, ry, fct[0], GRAY, false);
            c.textRight(183, ry, Canvas.fit(fct[1], false, 100), WHITE, false);
        }
        card(c, 197, y, 193, h, null);
        int ry = y + 8;
        c.text(207, ry, Canvas.caps("Meldungen"), GOLD, true);
        c.textRight(380, ry, String.valueOf(v.reportCount()), v.reportCount() > 0 ? RED : GREEN, true);
        ry += 13;
        if (v.reports().isEmpty()) {
            c.text(209, ry, "keine", DIM, false);
            ry += 11;
        }
        for (int i = 0; i < v.reports().size() && i < 3; i++) {
            String[] r = v.reports().get(i);
            int aw = Canvas.width(r[1], false);
            c.text(209, ry, Canvas.fit(r[0], false, 165 - aw), LIGHT, false);
            c.textRight(380, ry, r[1], DIM, false);
            ry += 11;
        }
        ry += 4;
        c.text(207, ry, Canvas.caps("Verwarnungen"), GOLD, true);
        c.textRight(380, ry, String.valueOf(v.warnCount()), v.warnCount() > 0 ? ORANGE : GREEN, true);
        ry += 13;
        if (v.warns().isEmpty()) c.text(209, ry, "keine", DIM, false);
        for (int i = 0; i < v.warns().size() && i < 3; i++) {
            String[] r = v.warns().get(i);
            int aw = Canvas.width(r[1], false);
            c.text(209, ry, Canvas.fit(r[0], false, 165 - aw), LIGHT, false);
            c.textRight(380, ry, r[1], DIM, false);
            ry += 11;
        }
        y += h + 3;
        card(c, 0, y, W, 62, "Aktionen");
        String k = v.key();
        boolean on = v.online() && !v.self();
        String off = v.self() ? "Das bist du" : v.name() + " ist nicht online";
        String noMod = "Ab Moderator";
        int bw4 = (W - 20 - 3 * 4) / 4;
        int[] xs = {10, 10 + bw4 + 4, 10 + 2 * (bw4 + 4), 10 + 3 * (bw4 + 4)};
        int r1 = y + 20, r2 = y + 39;
        button(c, xs[0], r1, bw4, "Hin", KitData.Small.GOLD, GOLD_SOFT, on ? nav.go("hin:" + k) : null, on ? "Zu " + v.name() + " teleportieren" : off);
        button(c, xs[1], r1, bw4, "Herholen", KitData.Small.DARK, LIGHT, on && v.mod() ? nav.go("her:" + k) : null,
                !on ? off : v.mod() ? v.name() + " zu dir holen" : noMod);
        button(c, xs[2], r1, bw4, "Inventar", KitData.Small.DARK, LIGHT, v.online() && v.mod() ? nav.go("inv:" + k) : null,
                !v.online() ? v.name() + " ist nicht online" : v.mod() ? (v.admin() ? "Inventar öffnen (bearbeitbar)" : "Inventar ansehen") : noMod);
        button(c, xs[3], r1, bw4, "Enderkiste", KitData.Small.DARK, LIGHT, v.mod() ? nav.go("ec:" + k) : null,
                v.mod() ? "Enderkiste ansehen" : noMod);
        button(c, xs[0], r2, bw4, "Verwarnen", KitData.Small.DARK, ORANGE, v.mod() && !v.self() ? nav.go("warn:" + k) : null,
                v.self() ? "Das bist du" : v.mod() ? "Verwarnung mit Grund (Titel und Chat beim Spieler)" : noMod);
        button(c, xs[1], r2, bw4, "Kicken", KitData.Small.DARK, RED, on && v.mod() ? nav.go("kick:" + k) : null,
                !on ? off : v.mod() ? "Vom Server werfen, mit Grund" : noMod);
        button(c, xs[2], r2, bw4, "Meldungen", KitData.Small.DARK, LIGHT, v.reportCount() > 0 ? nav.go("gegen:" + k) : null,
                v.reportCount() > 0 ? "Alle Meldungen gegen " + v.name() : "Keine Meldungen");
        button(c, xs[3], r2, bw4, "Admin", KitData.Small.GOLD, GOLD_SOFT, v.admin() ? nav.go("adminp:" + k) : null,
                v.admin() ? "Coins, Juwelen, Stats, XP (Admin-Werkzeuge)" : "Nur für Admins");
        return c;
    }

    // =================================================================================================================
    // Kadmin
    // =================================================================================================================

    public record Tile(String section, Material icon, String name, String desc, String key) {}

    public record Kadmin(Frame f, String version, int online, List<Tile> tiles) {}

    public static Canvas kadmin(Kadmin v, Nav nav) {
        Canvas c = base("Kadmin");
        int bw = width("Truhe");
        head(c, KitData.Icon.COMPARATOR, "KADMIN", "Version " + v.version() + " · " + v.online() + " Spieler online",
                378 - bw - 8 - 47);
        dark(c, 380 - bw, 9, "Truhe", nav.go("k:truhe"), "Das alte Admin-Menü als Truhe");
        tabs(c, v.f(), Tab.KADMIN, nav);
        Map<String, List<Tile>> sections = new LinkedHashMap<>();
        for (Tile t : v.tiles()) sections.computeIfAbsent(t.section(), s -> new ArrayList<>()).add(t);
        int y = TOP;
        int tw = (W - 20 - 2 * 4) / 3;
        for (Map.Entry<String, List<Tile>> e : sections.entrySet()) {
            int rows = (e.getValue().size() + 2) / 3;
            int h = 20 + 30 * rows + 2;
            card(c, 0, y, W, h, e.getKey());
            for (int i = 0; i < e.getValue().size(); i++) {
                Tile t = e.getValue().get(i);
                int x = 10 + (i % 3) * (tw + 4), ty = y + 18 + 30 * (i / 3);
                c.box(x, ty, tw, 28, KitData.Box.FIELD_BLACK, Canvas.Z_BOX + 1);
                c.item(x + 6, ty + 6, t.icon(), false, KitData.Icon.PAPER);
                c.text(x + 27, ty + 6, Canvas.fit(t.name(), true, tw - 31), GOLD_SOFT, true);
                c.text(x + 27, ty + 17, Canvas.fit(t.desc(), false, tw - 31), GRAY, false);
                c.action(x, ty, tw, 28, nav.go("k:" + t.key()), t.name() + " · " + t.desc());
            }
            y += h + 3;
        }
        return c;
    }

    /** Standard-Kacheln des Kadmin (ohne Addons). */
    public static List<Tile> coreTiles() {
        List<Tile> l = new ArrayList<>();
        String s = "Server";
        l.add(new Tile(s, Material.WRITABLE_BOOK, "Speichern", "Alles sichern", "speichern"));
        l.add(new Tile(s, Material.REPEATER, "Neu laden", "Config laden", "reload"));
        l.add(new Tile(s, Material.OAK_SIGN, "Ankündigung", "An alle Spieler", "ankuendigung"));
        l.add(new Tile(s, Material.BOOK, "Quiz starten", "Frage jetzt", "quiz"));
        l.add(new Tile(s, Material.TRIAL_KEY, "Schlüssel", "Key an alle", "schluessel"));
        l.add(new Tile(s, Material.COMPARATOR, "Einstellungen", "Wichtige Werte", "einstellungen"));
        String i = "Spielinhalte";
        l.add(new Tile(i, Material.EMERALD, "Shop", "Shop-Sektionen", "shop"));
        l.add(new Tile(i, Material.GOLD_INGOT, "Preisliste", "Verkaufspreise", "preise"));
        l.add(new Tile(i, Material.IRON_CHESTPLATE, "Kits", "Items, Cooldowns", "kits"));
        l.add(new Tile(i, Material.ENCHANTED_BOOK, "Battlepass", "Free und Premium", "battlepass"));
        l.add(new Tile(i, Material.GOLD_BLOCK, "Casino", "Tische, Limits, Log", "casino"));
        l.add(new Tile(i, Material.CHEST, "Auktionshaus", "Angebote prüfen", "markt"));
        String sec = "Sicherheit";
        l.add(new Tile(sec, Material.GOLD_INGOT, "Wirtschaft", "Buchungen, Suche", "wirtschaft"));
        l.add(new Tile(sec, Material.BELL, "Warnungen", "Auffälliges", "warnungen"));
        l.add(new Tile(sec, Material.CHEST_MINECART, "Backups", "Sichern, zurück", "backups"));
        l.add(new Tile(sec, Material.NAME_TAG, "Zweitkonten", "Gleiche IP", "zweitkonten"));
        String v = "Verwaltung";
        l.add(new Tile(v, Material.PLAYER_HEAD, "Spieler", "Akten, Aktionen", "spieler"));
        l.add(new Tile(v, Material.WHITE_BANNER, "Teams", "Teams verwalten", "teams"));
        l.add(new Tile(v, Material.COMMAND_BLOCK, "Config-Editor", "Jeder Wert", "config"));
        return l;
    }
}
