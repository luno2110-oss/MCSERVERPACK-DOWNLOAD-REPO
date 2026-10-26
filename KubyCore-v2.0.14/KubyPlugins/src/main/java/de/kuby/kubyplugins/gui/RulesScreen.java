package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.RulesBook.Book;
import de.kuby.kubyplugins.service.RulesBook.Paragraph;
import de.kuby.kubyplugins.service.RulesBook.Point;
import de.kuby.kubyplugins.ui.Canvas;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.I18n;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * /regeln nach der Referenz „Neue-referenzen“: Übersicht mit Regelbuch, „So spielen wir“ und einem Knopf je Paragraph
 * (zwei Spalten, jede Farbe für sich); je Paragraph eine Seite mit Zurück-Knopf, nummerierten Punkten in Kästen und
 * unten Vorheriger / Alle Regeln / Nächster. Kästen sind wie im Original 9·k+7 px hoch. Ohne Pack: Text im Chat.
 */
public final class RulesScreen {

    private static final int W = 390, TEXT_RIGHT = 380, LINE = 11, ITEM = 14;
    private static final TextColor BODY = TextColor.color(0xD1D2D5), GRAY = TextColor.color(0x9FA1A7),
            NAV = TextColor.color(0xC0C4CB), ON = TextColor.color(0x201400), GREEN = TextColor.color(0x6DE288),
            WHITE = TextColor.color(0xFCFCFC);

    public interface Actions {
        Consumer<Player> paragraph(int index);

        Consumer<Player> overview();
    }

    /** Kasten, Knopf „Alle Regeln“ und Schriftfarbe je Farbe aus regeln.yml. */
    private record Look(KitData.Box box, KitData.Box all, TextColor color) {}

    private static Look look(String color) {
        return switch (color.toLowerCase(Locale.ROOT)) {
            case "rot" -> new Look(KitData.Box.RG_RED, KitData.Box.RG_ALL_RED, TextColor.color(0xFC7986));
            case "gold" -> new Look(KitData.Box.RG_GOLD, KitData.Box.RG_ALL_GOLD, TextColor.color(0xFCD45D));
            case "braun" -> new Look(KitData.Box.RG_BROWN, KitData.Box.RG_ALL_BROWN, TextColor.color(0xEFB16A));
            case "grau" -> new Look(KitData.Box.RG_GRAY, KitData.Box.RG_ALL_GRAY, NAV);
            case "lila" -> new Look(KitData.Box.RG_PURPLE, KitData.Box.RG_ALL_PURPLE, TextColor.color(0xCD89FC));
            case "blau" -> new Look(KitData.Box.RG_BLUE, KitData.Box.RG_ALL_BLUE, TextColor.color(0x62CAFC));
            case "tuerkis", "türkis" -> new Look(KitData.Box.RG_TEAL, KitData.Box.RG_ALL_TEAL, TextColor.color(0x6DE5D7));
            default -> new Look(KitData.Box.RG_GREEN, KitData.Box.RG_ALL_GREEN, GREEN);
        };
    }

    private static KitData.Sprite icon(int n) {
        return switch (n) {
            case 2 -> KitData.Sprite.RG_P2;
            case 3 -> KitData.Sprite.RG_P3;
            case 4 -> KitData.Sprite.RG_P4;
            case 5 -> KitData.Sprite.RG_P5;
            case 6 -> KitData.Sprite.RG_P6;
            case 7 -> KitData.Sprite.RG_P7;
            case 8 -> KitData.Sprite.RG_P8;
            case 9 -> KitData.Sprite.RG_P9;
            case 10 -> KitData.Sprite.RG_P10;
            case 11 -> KitData.Sprite.RG_P11;
            case 12 -> KitData.Sprite.RG_P12;
            default -> KitData.Sprite.RG_P1;
        };
    }

    private final KubyPlugins plugin;

    public RulesScreen(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** index -1: Übersicht, sonst Paragraph index (ab 0). */
    public void show(Player p, int index) {
        Book b = plugin.rules().book();
        Actions a = new Actions() {
            public Consumer<Player> paragraph(int i) {
                return pl -> show(pl, i);
            }

            public Consumer<Player> overview() {
                return pl -> show(pl, -1);
            }
        };
        if (index < 0 || index >= b.paragraphs().size()) UiDialog.show(p, overview(b, a));
        else UiDialog.show(p, paragraph(b, index, a));
    }

    /** Höhe eines Kastens wie im Original: mindestens Inhalt + Rand, gerundet auf 9·k+7. */
    private static int boxHeight(int lastTop, int boxY) {
        int need = lastTop - boxY + 17;
        return 7 + 9 * Math.max(1, (need - 7 + 8) / 9);
    }

    public static Canvas overview(Book b, Actions a) {
        Canvas c = new Canvas(W).heading("Serverregeln", GREEN);
        c.box(0, 0, W, 52, KitData.Box.RG_GREEN);
        c.sprite(9, 12, KitData.Sprite.RG_BOOK);
        c.text(47, 16, upper("Serverregeln"), GREEN, true);
        c.text(47, 29, Canvas.fit(b.subtitle(), false, 330), GRAY, false);

        // So spielen wir
        int y = 54;
        int ty = y + 10;
        c.text(10, ty, upper(b.introTitle()), GREEN, true);
        ty += 14;
        List<String> lines = Canvas.wrap(b.introText(), TEXT_RIGHT - 12);
        for (int i = 0; i < lines.size(); i++) {
            c.text(12, ty, lines.get(i), BODY, false);
            if (i + 1 < lines.size()) ty += LINE;
        }
        if (!b.hint().isEmpty()) {
            List<String> hint = Canvas.wrap(b.hint(), TEXT_RIGHT - 12);
            ty += ITEM;
            for (int i = 0; i < hint.size(); i++) {
                c.text(12, ty, hint.get(i), GRAY, false);
                if (i + 1 < hint.size()) ty += LINE;
            }
        }
        int h = boxHeight(ty, y);
        c.box(0, y, W, h, KitData.Box.RG_GREEN);
        y += h + 11;

        // Paragraphen in zwei Spalten
        List<Paragraph> ps = b.paragraphs();
        for (int i = 0; i < ps.size(); i++) {
            Paragraph p = ps.get(i);
            Look lk = look(p.color());
            int bx = (i % 2) * 198, by = y + (i / 2) * 27;
            c.box(bx, by, 192, 25, lk.box());
            c.sprite(bx + 10, by + 8, icon(p.icon()));
            String num = (i + 1) + ".";
            // Nummer rechtsbündig (jede weitere Ziffer 7 px weiter links), Titel immer ab x + 42
            c.text(bx + 28 - 7 * (String.valueOf(i + 1).length() - 1), by + 8, num, lk.color(), true);
            c.text(bx + 42, by + 8, Canvas.fit(p.title(), true, 135), lk.color(), true);
            c.text(bx + 178, by + 8, "›", lk.color(), true);
            c.action(bx, by, 192, 25, a.paragraph(i), "§" + (i + 1) + " " + p.title() + " lesen");
        }
        return c;
    }

    public static Canvas paragraph(Book b, int index, Actions a) {
        List<Paragraph> ps = b.paragraphs();
        Paragraph p = ps.get(index);
        Look lk = look(p.color());
        int n = index + 1;
        Canvas c = new Canvas(W).heading("§" + n + " " + p.title(), lk.color());

        // Kopf
        c.box(0, 0, W, 52, lk.box());
        c.sprite(9, 18, KitData.Sprite.RG_BACK);
        c.action(9, 18, 22, 22, a.overview(), "Alle Regeln");
        c.sprite(37, 17, icon(p.icon()));
        c.text(49, 17, Canvas.fit(upper(n + ". " + p.title()), true, 330), lk.color(), true);
        c.text(37, 29, "Paragraph " + n + " von " + ps.size(), GRAY, false);

        // Punkte in Kästen
        int y = 54, k = 0;
        for (List<Point> box : p.boxes()) {
            int ty = y + 10;
            for (int j = 0; j < box.size(); j++) {
                Point pt = box.get(j);
                int tx;
                if (p.bullets()) {
                    bullet(c, 11, ty + 3, lk.color());
                    tx = 22;
                } else {
                    String num = n + "." + (++k);
                    c.text(12, ty, num, lk.color(), true);
                    tx = 12 + Canvas.width(num, true) + 8;
                }
                List<String> lines = Canvas.wrap(pt.text(), TEXT_RIGHT - tx);
                for (int i = 0; i < lines.size(); i++) {
                    c.text(tx, ty, lines.get(i), BODY, false);
                    if (i + 1 < lines.size()) ty += LINE;
                }
                for (String sub : pt.subs()) {
                    ty += LINE;
                    dash(c, tx, ty + 3, lk.color());
                    int sx = tx + 10;
                    List<String> sl = Canvas.wrap(sub, TEXT_RIGHT - sx);
                    for (int i = 0; i < sl.size(); i++) {
                        c.text(sx, ty, sl.get(i), WHITE, false);
                        if (i + 1 < sl.size()) ty += LINE;
                    }
                }
                if (j + 1 < box.size()) ty += ITEM;
            }
            int h = boxHeight(ty, y);
            c.box(0, y, W, h, lk.box());
            y += h + 11;
        }

        // Vorheriger / Alle Regeln / Nächster
        int ny = y - 1;
        String prev = "‹ " + ps.get(Math.floorMod(index - 1, ps.size())).title();
        String next = ps.get(Math.floorMod(index + 1, ps.size())).title() + " ›";
        // Knöpfe so breit wie der Text + 16; „‹“ hat in der Schrift eine leere erste Spalte, darum 7 statt 8
        int pw = Math.min(140, Canvas.width(prev, false) + 16);
        c.box(0, ny, pw, 20, KitData.Box.RG_NAV);
        c.text(7, ny + 6, Canvas.fit(prev, false, pw - 16), NAV, false);
        c.action(0, ny, pw, 20, a.paragraph(Math.floorMod(index - 1, ps.size())), "Voriger Paragraph");
        c.box(154, ny, 82, 20, lk.all());
        c.textCenter(195, ny + 6, "Alle Regeln", ON, true);
        c.action(154, ny, 82, 20, a.overview(), "Zur Übersicht");
        int nw = Math.min(140, Canvas.width(next, false) + 16);
        c.box(W - nw, ny, nw, 20, KitData.Box.RG_NAV);
        c.text(W - nw + 8, ny + 6, Canvas.fit(next, false, nw - 16), NAV, false);
        c.action(W - nw, ny, nw, 20, a.paragraph(Math.floorMod(index + 1, ps.size())), "Nächster Paragraph");
        return c;
    }

    /** Aufzählungspunkt 3 x 2 px in der Farbe des Paragraphen, mit Schatten wie Schrift. */
    private static void bullet(Canvas c, int x, int y, TextColor color) {
        TextColor shadow = TextColor.color(color.red() / 4, color.green() / 4, color.blue() / 4);
        c.line(x + 1, y + 2, 3, shadow);
        c.line(x + 3, y + 1, 1, shadow);
        c.line(x, y, 3, color);
        c.line(x, y + 1, 3, color);
    }

    /** Strich 7 px vor einem Unterpunkt (kein Schriftzeichen hat diese Breite), mit Schatten wie Schrift. */
    private static void dash(Canvas c, int x, int y, TextColor color) {
        c.line(x + 1, y + 1, 7, TextColor.color(color.red() / 4, color.green() / 4, color.blue() / 4));
        c.line(x, y, 7, color);
    }

    private static String upper(String text) {
        return I18n.ctx(text).toUpperCase(Locale.GERMAN);
    }
}
