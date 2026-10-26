package de.kuby.kubysurvival.marry;

import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.Canvas.Span;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.util.I18n;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Menüs für Heiraten und Bank auf dem Dialog-Kit (Breite 390, Kopf wie die Hilfe-Seiten der Referenz).
 * Nur Darstellung; Daten kommen als View-Records, Klicks über die Actions-Schnittstellen (MarryMenus).
 */
public final class MarryScreens {

    public static final int W = 390;
    /** Alle Menüs gleich hoch, dann steht der Kopf beim Wechseln immer an derselben Stelle. */
    public static final int HEIGHT = 330;

    static final TextColor PINK = TextColor.color(0xFF7AC0), GOLD = TextColor.color(0xF8D35A),
            GRAY = TextColor.color(0xA0A0A8), LIGHT = TextColor.color(0xE4E4EA), WHITE = TextColor.color(0xF4F4F4),
            GREEN = TextColor.color(0x6CE86E), RED = TextColor.color(0xFF6A6A), ON_GOLD = TextColor.color(0x2C1C06),
            CMD = TextColor.color(0x5FE3C4);

    private MarryScreens() {
    }

    /**
     * Kopf wie die Hilfe-Seiten der Referenz: 51 hoch, Zurück-Knopf (8, 17), Icon 18 x 18 (43, 18), Titel (74, 15),
     * Untertitel (74, 28). Ohne Zurück rücken Icon und Text nach links.
     */
    private static void head(Canvas c, KitData.Box box, KitData.SmallIcon icon, String title, TextColor accent, String sub,
                             Consumer<Player> back, List<Span> right) {
        c.box(0, 0, W, 51, box);
        int ix = 13, tx = 41;
        if (back != null) {
            c.backHelp(8, 17);
            c.action(8, 17, 22, 22, back, "Zurück");
            ix = 43;
            tx = 74;
        }
        c.smallIcon(ix, back != null ? 18 : 16, icon);
        c.text(tx, 15, title, accent, true);
        int room = W - tx - 12 - (right == null ? 0 : Canvas.width(right) + 8);
        if (sub != null) c.text(tx, 28, Canvas.fit(sub, false, room), GRAY, false);
        if (right != null) c.textRight(378, 21, right);
    }

    /** Kasten mit Überschrift (Small Caps in der Akzentfarbe). */
    private static void box(Canvas c, int y, int h, KitData.Box style, String title, TextColor accent) {
        c.box(0, y, W, h, style);
        if (title != null) c.text(9, y + 9, title.toUpperCase(java.util.Locale.ROOT), accent, true);
    }

    /** Aufzählung mit • wie in der Hilfe (Zeilen 11, Absätze +3), gibt die y-Position unter der letzten Zeile zurück. */
    private static int bullets(Canvas c, int x, int y, List<String> lines, TextColor bullet) {
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) y += 3;
            List<String> w = Canvas.wrap(lines.get(i), W - x - 19);
            for (int k = 0; k < w.size(); k++) {
                if (k == 0) c.text(x, y, "•", bullet, false);
                c.text(x + 11, y, w.get(k), LIGHT, false);
                y += 11;
            }
        }
        return y;
    }

    /** Höhe eines "So geht's"-Kastens (Titel, Aufzählung ab y + 23, unten 12 Rand). */
    private static int howHeight(List<String> paras) {
        int n = 0;
        for (String p : paras) n += Canvas.wrap(p, W - 10 - 19).size();
        return 23 + 11 * n + 3 * (paras.size() - 1) - 4 + 12;
    }

    /** Reihe gleich breiter Knöpfe über die ganze Breite. */
    private static void buttonRow(Canvas c, int y, List<Btn> buttons) {
        int n = buttons.size(), gap = 4;
        int w = (W - gap * (n - 1)) / n, x = 0;
        for (int i = 0; i < n; i++) {
            Btn b = buttons.get(i);
            int bw = i == n - 1 ? W - x : w;
            c.button(x, y, bw, b.style, Canvas.fit(b.label, true, bw - 10), b.color, true);
            if (b.click != null) c.action(x, y, bw, KitData.BTN_H, b.click, b.hover);
            x += bw + gap;
        }
    }

    record Btn(String label, KitData.Button style, TextColor color, Consumer<Player> click, String hover) {
        static Btn of(String label, KitData.Button style, Consumer<Player> click, String hover) {
            TextColor color = switch (style) {
                case GOLD -> ON_GOLD;
                case GOLD_LINE -> GOLD;
                case RED_LINE -> RED;
                case DARK -> LIGHT;
                default -> WHITE;
            };
            return new Btn(label, style, color, click, hover);
        }
    }

    // =================================================================================================================
    // Heiraten
    // =================================================================================================================

    /** Ein offener Antrag: Name des anderen und Restzeit in Minuten. */
    public record Pending(String name, long minutes) {}

    public record MarryView(String partner, String since, String partnerStatus, boolean partnerOnline, Long marriageBalance,
                            Pending incoming, Pending outgoing, List<String> candidates, long cost, String blocked,
                            String armed) {
        boolean married() {
            return partner != null;
        }
    }

    public interface MarryActions {
        Consumer<Player> tp();

        Consumer<Player> church();

        Consumer<Player> bank();

        Consumer<Player> divorce();

        Consumer<Player> deny();

        Consumer<Player> cancel();

        Consumer<Player> propose(String name);
    }

    public static Canvas marry(MarryView v, MarryActions a) {
        Canvas c = new Canvas(W).heading("Heiraten", net.kyori.adventure.text.format.TextColor.color(0xFF7AC0));
        head(c, KitData.Box.PURPLE, KitData.SmallIcon.H_RINGS, "HEIRATEN", PINK, "Antrag, Hochzeit und Ehe-Konto", null,
                v.married() ? List.of(Span.of("❤ ", PINK), Span.of("verheiratet", LIGHT)) : null);
        int y = 53;
        if (v.married()) {
            box(c, y, 70, KitData.Box.PURPLE, "Deine Ehe", PINK);
            c.text(12, y + 22, List.of(Span.bold("❤ ", PINK), Span.bold(v.partner(), WHITE)));
            c.text(12, y + 35, "Verheiratet seit " + v.since(), GRAY, false);
            c.text(12, y + 48, v.partnerStatus(), v.partnerOnline() ? GREEN : GRAY, false);
            c.textRight(378, y + 9, "EHE-KONTO", PINK, true);
            if (v.marriageBalance() != null) {
                c.textRight(378, y + 22, de.kuby.kubyplugins.util.Msg.coins(v.marriageBalance()) + " Coins", GOLD, true);
                c.textRight(378, y + 35, "/bank", CMD, false);
            } else {
                c.textRight(378, y + 22, "noch nicht eröffnet", GRAY, false);
            }
            y += 73;
            boolean sure = "divorce".equals(v.armed());
            buttonRow(c, y, List.of(
                    Btn.of("Zum Partner", KitData.Button.PURPLE, v.partnerOnline() ? a.tp() : null,
                            v.partnerOnline() ? "Zu " + v.partner() + " teleportieren" : null),
                    Btn.of("Zur Kirche", KitData.Button.DARK, a.church(), "Zur Kirche teleportieren"),
                    // englisch kürzer: „Marriage account“ passt nicht auf den Knopf
                    Btn.of(I18n.context() ? "Account" : "Ehe-Konto", KitData.Button.GOLD, a.bank(), "Bank öffnen"),
                    Btn.of(sure ? "Wirklich?" : "Scheidung", sure ? KitData.Button.RED : KitData.Button.RED_LINE, a.divorce(),
                            sure ? "Nochmal klicken: Scheidung, das Ehe-Konto wird geteilt" : "Scheiden lassen (zweimal klicken)")));
            y += 25;
            List<String> how = List.of(
                    "Mit /bank zahlt ihr überall bis zu einem Limit ein und aus, große Beträge an der Bank am Spawn.",
                    "Verheiratet steht ein Herz vor eurem Namen im Chat und in der Tab-Liste.",
                    "Bei einer Scheidung wird das Ehe-Konto halbiert, jeder bekommt seinen Teil. Danach dürft ihr eine Zeit lang nicht neu heiraten.");
            box(c, y, howHeight(how), KitData.Box.PURPLE, "So geht's", PINK);
            bullets(c, 10, y + 23, how, PINK);
            return c;
        }
        // Anträge
        int rows = (v.incoming() != null ? 1 : 0) + (v.outgoing() != null ? 1 : 0);
        int h = rows == 0 ? 34 : 20 + 34 * rows;
        box(c, y, h, KitData.Box.PURPLE, "Anträge", PINK);
        int ry = y + 21;
        if (rows == 0) c.text(12, ry, "Keine offenen Anträge.", GRAY, false);
        if (v.incoming() != null) {
            int room = proposalButtons(c, ry, "Ablehnen", a.deny(), "Antrag ablehnen", a.church());
            c.text(12, ry, List.of(Span.bold(Canvas.fit(v.incoming().name(), true, room / 2), WHITE),
                    Span.of(" hat dir einen Antrag gemacht", LIGHT)));
            c.text(12, ry + 12, Canvas.fit("noch " + v.incoming().minutes() + " Min. · beim Priester sagst du Ja", false, room), GRAY, false);
            ry += 34;
        }
        if (v.outgoing() != null) {
            int room = proposalButtons(c, ry, "Zurückziehen", a.cancel(), "Antrag zurückziehen", a.church());
            c.text(12, ry, List.of(Span.of("Dein Antrag an ", LIGHT), Span.bold(Canvas.fit(v.outgoing().name(), true, room / 2), WHITE)));
            c.text(12, ry + 12, Canvas.fit("noch " + v.outgoing().minutes() + " Min. · beim Priester sagt "
                    + v.outgoing().name() + " Ja", false, room), GRAY, false);
        }
        y += h + 3;
        // Antrag an Freunde
        if (v.outgoing() == null && v.blocked() == null) {
            int n = Math.min(6, v.candidates().size());
            int ch = n == 0 ? 34 : 22 + 20 * ((n + 1) / 2);
            box(c, y, ch, KitData.Box.PURPLE, "Antrag machen", PINK);
            if (n == 0) {
                c.text(12, y + 21, "Kein Freund ist gerade in Survival. Sonst: /marry propose <Spieler>", GRAY, false);
            }
            for (int i = 0; i < n; i++) {
                String name = v.candidates().get(i);
                int tx = 8 + (i % 2) * 190, ty = y + 19 + (i / 2) * 20;
                c.text(tx + 4, ty + 4, Canvas.fit(name, false, 110), WHITE, false);
                boolean sure = ("propose:" + name).equals(v.armed());
                String label = sure ? "Sicher?" : "❤ Antrag";
                int bw = Canvas.width(label, true) + 14;
                c.smallButton(tx + 178 - bw, ty, bw, sure ? KitData.Small.GREEN : KitData.Small.GOLD, label, sure ? WHITE : GOLD, true);
                c.action(tx + 178 - bw, ty, bw, 16, a.propose(name), sure ? "Nochmal klicken: Antrag an " + name
                        : "Antrag an " + name + " (zweimal klicken)");
            }
            y += ch + 3;
        } else if (v.blocked() != null) {
            box(c, y, 34, KitData.Box.PURPLE, "Antrag machen", PINK);
            c.text(12, y + 21, v.blocked(), RED, false);
            y += 37;
        }
        List<String> how = List.of(
                "Mach einem Spieler mit /marry propose einen Antrag. Er gilt 10 Minuten.",
                "Das Ja-Wort gebt ihr euch beim Priester in der Kirche, beide müssen dort stehen. Rechtsklick auf den Priester.",
                "Die Hochzeit kostet " + de.kuby.kubyplugins.util.Msg.coins(v.cost()) + " Coins. Bezahlt wird sie von dem, der den Antrag macht.");
        box(c, y, howHeight(how), KitData.Box.PURPLE, "So geht's", PINK);
        bullets(c, 10, y + 23, how, PINK);
        y += howHeight(how) + 4;
        buttonRow(c, y, List.of(
                Btn.of("Zur Kirche", KitData.Button.PURPLE, a.church(), "Zur Kirche teleportieren"),
                Btn.of("Bank", KitData.Button.GOLD, a.bank(), "Ehe-Konto und Konten mit Freunden")));
        return c;
    }

    /** Knöpfe "Zur Kirche" und Ablehnen/Zurückziehen rechts neben einem Antrag; gibt die Breite für den Text zurück. */
    private static int proposalButtons(Canvas c, int ry, String label, Consumer<Player> click, String hover,
                                       Consumer<Player> church) {
        int bw = Canvas.width(label, true) + 14, cw = Canvas.width("Zur Kirche", true) + 14;
        c.smallButton(378 - bw, ry + 2, bw, KitData.Small.DARK, label, LIGHT, true);
        c.action(378 - bw, ry + 2, bw, 16, click, hover);
        c.smallButton(374 - bw - cw, ry + 2, cw, KitData.Small.GOLD, "Zur Kirche", GOLD, true);
        c.action(374 - bw - cw, ry + 2, cw, 16, church, "Zur Kirche teleportieren");
        return 374 - bw - cw - 12 - 8;
    }

    // =================================================================================================================
    // Bank: Übersicht
    // =================================================================================================================

    public record AccountView(String id, String name, boolean marriage, long balance, List<String> members, String owner,
                              boolean isOwner) {}

    public record BankView(long wallet, boolean atBank, long limit, List<AccountView> accounts, boolean canOpenMarriage,
                           boolean canCreate, String createHint, boolean hasBankPoint) {}

    public interface BankActions {
        Consumer<Player> open(String id);

        Consumer<Player> openMarriage();

        Consumer<Player> create();

        Consumer<Player> toBank();

        Consumer<Player> marry();
    }

    private static List<Span> wallet(long wallet) {
        return List.of(Span.of("Dein Geld ", GRAY), Span.bold(de.kuby.kubyplugins.util.Msg.coins(wallet), GOLD));
    }

    public static Canvas bank(BankView v, BankActions a) {
        Canvas c = new Canvas(W).heading("Bank", net.kyori.adventure.text.format.TextColor.color(0xFF7AC0));
        head(c, KitData.Box.GOLD, KitData.SmallIcon.H_COINS, "BANK", GOLD, "Ehe-Konto und Konten mit Freunden", null, wallet(v.wallet()));
        int y = 53;
        String info = v.atBank() ? "Du bist an der Bank: Ein- und Auszahlen ohne Grenze."
                : "Unterwegs: bis " + de.kuby.kubyplugins.util.Msg.coins(v.limit()) + " Coins je Buchung. Mehr an der Bank am Spawn.";
        c.text(4, y + 2, info, v.atBank() ? GREEN : GRAY, false);
        y += 15;
        for (AccountView acc : v.accounts()) {
            KitData.Box style = acc.marriage() ? KitData.Box.PURPLE : KitData.Box.GOLD;
            c.box(0, y, W, 36, style);
            c.smallIcon(8, y + 9, acc.marriage() ? KitData.SmallIcon.H_RINGS : KitData.SmallIcon.H_PEOPLE);
            c.text(32, y + 8, Canvas.fit(acc.name(), true, 200), acc.marriage() ? PINK : GOLD, true);
            c.text(32, y + 21, Canvas.fit(String.join(", ", acc.members()), false, 220), GRAY, false);
            c.textRight(380, y + 8, de.kuby.kubyplugins.util.Msg.coins(acc.balance()) + " Coins", WHITE, true);
            c.textRight(380, y + 21, "Öffnen ›", LIGHT, false);
            c.action(0, y, W, 36, a.open(acc.id()), "Konto öffnen: ein- und auszahlen, Verlauf");
            y += 39;
        }
        if (v.canOpenMarriage()) {
            c.box(0, y, W, 36, KitData.Box.PURPLE);
            c.smallIcon(8, y + 9, KitData.SmallIcon.H_RINGS);
            c.text(32, y + 8, "Ehe-Konto eröffnen", PINK, true);
            c.text(32, y + 21, "Gemeinsames Konto für euch beide, kostenlos", GRAY, false);
            c.textRight(380, y + 14, "Eröffnen ›", LIGHT, false);
            c.action(0, y, W, 36, a.openMarriage(), "Ehe-Konto eröffnen");
            y += 39;
        }
        c.box(0, y, W, 36, KitData.Box.GRAY);
        c.smallIcon(8, y + 9, KitData.SmallIcon.H_PEOPLE);
        c.text(32, y + 8, "Neues Konto mit Freunden", v.canCreate() ? GOLD : GRAY, true);
        c.text(32, y + 21, Canvas.fit(v.createHint(), false, 300), GRAY, false);
        if (v.canCreate()) {
            c.textRight(380, y + 14, "Eröffnen ›", LIGHT, false);
            c.action(0, y, W, 36, a.create(), "Konto mit Freunden eröffnen");
        }
        y += 39;
        if (v.accounts().isEmpty() && !v.canOpenMarriage()) {
            c.text(4, y + 2, "Noch kein Konto. Verheiratet: Ehe-Konto. Mit Freunden: an der Bank am Spawn.", GRAY, false);
            y += 15;
        }
        y += 3;
        List<Btn> row = new ArrayList<>();
        if (!v.atBank() && v.hasBankPoint()) row.add(Btn.of("Zur Bank", KitData.Button.GOLD, a.toBank(), "Zur Bank am Spawn teleportieren"));
        row.add(Btn.of("Heiraten", KitData.Button.PURPLE, a.marry(), "Ehe-Menü"));
        buttonRow(c, y, row);
        return c;
    }

    // =================================================================================================================
    // Bank: ein Konto
    // =================================================================================================================

    /** Eine Zeile im Verlauf: Zeitpunkt, Name, Betrag (mit Vorzeichen). */
    public record LogLine(String when, String who, long amount) {}

    public record AccountDetail(AccountView account, long wallet, boolean atBank, long limit, List<LogLine> log,
                                boolean canManage, List<String> addable, String armed) {}

    public interface AccountActions {
        /** amount &gt; 0: fester Betrag, -1: alles, 0: Betrag eingeben. */
        Consumer<Player> deposit(long amount);

        Consumer<Player> withdraw(long amount);

        Consumer<Player> add(String name);

        Consumer<Player> remove(String name);

        Consumer<Player> leave();

        Consumer<Player> close();

        Consumer<Player> back();
    }

    private static final long[] AMOUNTS = {100, 1_000, 10_000};

    public static Canvas account(AccountDetail d, AccountActions a) {
        AccountView acc = d.account();
        Canvas c = new Canvas(W).heading("Konto", net.kyori.adventure.text.format.TextColor.color(0xFF7AC0));
        KitData.Box style = acc.marriage() ? KitData.Box.PURPLE : KitData.Box.GOLD;
        TextColor accent = acc.marriage() ? PINK : GOLD;
        head(c, style, acc.marriage() ? KitData.SmallIcon.H_RINGS : KitData.SmallIcon.H_PEOPLE, acc.name().toUpperCase(), accent,
                acc.marriage() ? "Ehe-Konto" : "Konto mit Freunden · eröffnet von " + acc.owner(), a.back(),
                List.of(Span.bold(de.kuby.kubyplugins.util.Msg.coins(acc.balance()), WHITE), Span.of(" Coins", GRAY)));
        int y = 53;
        box(c, y, 78, style, "Buchen", accent);
        c.textRight(378, y + 9, List.of(Span.of("Dein Geld ", GRAY), Span.bold(de.kuby.kubyplugins.util.Msg.coins(d.wallet()), GOLD)));
        amountRow(c, y + 20, "Einzahlen", true, d, a);
        amountRow(c, y + 38, "Abheben", false, d, a);
        c.text(12, y + 64, d.atBank() ? "An der Bank: Beträge ohne Grenze."
                : "Unterwegs bis " + de.kuby.kubyplugins.util.Msg.coins(d.limit()) + " Coins je Buchung, mehr an der Bank am Spawn.",
                d.atBank() ? GREEN : GRAY, false);
        y += 81;
        // Mitglieder
        List<String> members = acc.members();
        int mh = 22 + 12 * ((members.size() + 1) / 2) + (d.canManage() && !d.addable().isEmpty() ? 20 : 0);
        box(c, y, mh, style, "Mitglieder", accent);
        if (!acc.marriage()) c.textRight(378, y + 9, "alle dürfen ein- und auszahlen", GRAY, false);
        for (int i = 0; i < members.size(); i++) {
            String m = members.get(i);
            int mx = 12 + (i % 2) * 190, my = y + 21 + (i / 2) * 12;
            boolean owner = m.equals(acc.owner()) && !acc.marriage();
            c.text(mx, my, List.of(Span.of(owner ? "★ " : "• ", owner ? GOLD : GRAY), Span.of(m, WHITE)));
            if (d.canManage() && !owner) {
                boolean sure = ("remove:" + m).equals(d.armed());
                String label = sure ? "Sicher?" : "Entfernen";
                c.textRight(mx + 176, my, label, sure ? RED : GRAY, false);
                c.action(mx + 176 - Canvas.width(label, false), my - 2, Canvas.width(label, false), 11, a.remove(m),
                        sure ? "Nochmal klicken: " + m + " entfernen" : m + " entfernen (zweimal klicken)");
            }
        }
        if (d.canManage() && !d.addable().isEmpty()) {
            int ax = 12, ay = y + mh - 20;
            c.text(ax, ay + 4, "Aufnehmen:", GRAY, false);
            ax += Canvas.width("Aufnehmen:", false) + 6;
            for (String f : d.addable()) {
                int bw = Canvas.width("+ " + f, true) + 12;
                if (ax + bw > 380) break;
                c.smallButton(ax, ay, bw, KitData.Small.DARK, "+ " + f, LIGHT, true);
                c.action(ax, ay, bw, 16, a.add(f), f + " ins Konto aufnehmen");
                ax += bw + 3;
            }
        }
        y += mh + 3;
        // Verlauf
        int n = Math.min(6, d.log().size());
        int lh = 22 + 11 * Math.max(1, n);
        box(c, y, lh, style, "Verlauf", accent);
        if (n == 0) c.text(12, y + 21, "Noch keine Buchungen.", GRAY, false);
        for (int i = 0; i < n; i++) {
            LogLine l = d.log().get(i);
            int ly = y + 21 + 11 * i;
            c.text(12, ly, l.when(), GRAY, false);
            c.text(110, ly, Canvas.fit(l.who(), false, 150), LIGHT, false);
            c.textRight(378, ly, (l.amount() >= 0 ? "+" : "-") + de.kuby.kubyplugins.util.Msg.coins(Math.abs(l.amount())),
                    l.amount() >= 0 ? GREEN : RED, false);
        }
        y += lh + 3;
        List<Btn> row = new ArrayList<>();
        row.add(Btn.of("‹ Zurück", KitData.Button.DARK, a.back(), "Zur Bank-Übersicht"));
        if (!acc.marriage()) {
            if (acc.isOwner()) {
                boolean sure = "close".equals(d.armed());
                if (d.canManage()) {
                    row.add(Btn.of(sure ? "Wirklich auflösen?" : "Konto auflösen", sure ? KitData.Button.RED : KitData.Button.RED_LINE,
                            a.close(), sure ? "Nochmal klicken: Guthaben wird gleichmäßig verteilt" : "Konto auflösen (zweimal klicken)"));
                }
            } else {
                boolean sure = "leave".equals(d.armed());
                row.add(Btn.of(sure ? "Wirklich verlassen?" : "Konto verlassen", sure ? KitData.Button.RED : KitData.Button.RED_LINE,
                        a.leave(), sure ? "Nochmal klicken: das Geld bleibt im Konto" : "Konto verlassen (zweimal klicken)"));
            }
        }
        buttonRow(c, y, row);
        return c;
    }

    private static void amountRow(Canvas c, int y, String label, boolean in, AccountDetail d, AccountActions a) {
        c.text(12, y + 7, label, LIGHT, true);
        int x = 84, gap = 3, bw = (378 - x - 4 * gap) / 5;
        long max = in ? d.wallet() : d.account().balance();
        for (int i = 0; i < 5; i++) {
            String text;
            long amount;
            if (i < 3) {
                amount = AMOUNTS[i];
                text = (in ? "+" : "-") + de.kuby.kubyplugins.util.Msg.coins(amount);
            } else if (i == 3) {
                amount = -1;
                text = "Alles";
            } else {
                amount = 0;
                text = "Betrag";
            }
            boolean ok = amount == 0 || (amount == -1 ? max > 0 : max >= amount && (d.atBank() || amount <= d.limit()));
            KitData.Button style = ok ? (in ? KitData.Button.GREEN_LINE : KitData.Button.RED_LINE) : KitData.Button.DARK;
            TextColor color = ok ? (in ? GREEN : RED) : GRAY;
            int bx = x + i * (bw + gap);
            c.button(bx, y, bw, style, text, color, true);
            c.action(bx, y, bw, KitData.BTN_H, in ? a.deposit(amount) : a.withdraw(amount),
                    (in ? "Einzahlen: " : "Abheben: ") + (amount == -1 ? "alles" : amount == 0 ? "Betrag eingeben" : text.substring(1) + " Coins"));
        }
    }
}
