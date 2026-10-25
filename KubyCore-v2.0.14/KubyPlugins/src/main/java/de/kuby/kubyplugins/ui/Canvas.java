package de.kuby.kubyplugins.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Zeichenfläche für Dialog-Menüs mit eigener Grafik (Schriften kuby:k/..., erzeugt von tools/build_kit.py).
 *
 * <p>Alles wird in Pixeln gesetzt: x nach rechts, y nach unten; bei Text ist y die Oberkante der Großbuchstaben.
 * Ein Dialog-Körper ist mehrzeiliger Text mit 9 px Zeilenhöhe. Jedes Zeichen kommt in eine Zeile und wird über die
 * Schrift (kuby:k/t3 = 3 px tiefer usw.) genau auf seine Höhe verschoben. Spätere Zeilen liegen über früheren; in
 * einer Zeile zeichnet Minecraft (ab 1.21.6) die Zeichen in Textreihenfolge. {@link #build} ordnet das automatisch:
 * Ebenen weiter unten (Kasten unter Knopf unter Text) kommen in frühere Zeilen, und wo das nicht geht (ganz oben),
 * in derselben Zeile weiter vorn.
 *
 * <p>Klickbare Bereiche ({@link #action}) bekommen Klick und Tooltip auf alle Zeichen darin, auch auf den Hintergrund.
 */
public final class Canvas {

    /** Ebenen: höher liegt oben. */
    public static final int Z_BOX = 0, Z_BUTTON = 2, Z_FILL = 3, Z_ICON = 4, Z_TEXT = 5;

    private static final int LINE = 9;

    private enum Kind { TEXT, BG, ICON }

    private static final class Glyph {
        final Kind kind;
        final int x, y, w, h, advance, z, minShift, maxShift;
        final String chars;
        final TextColor color;
        final boolean bold;
        /** Sichtbares Rechteck (für Überdeckung). */
        final int top, bottom;
        int line;
        Region region;

        Glyph(Kind kind, int x, int y, int w, int h, int advance, int z, String chars, TextColor color, boolean bold,
              int minShift, int maxShift, int top, int bottom) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.advance = advance;
            this.z = z;
            this.chars = chars;
            this.color = color;
            this.bold = bold;
            this.minShift = minShift;
            this.maxShift = maxShift;
            this.top = top;
            this.bottom = bottom;
        }

        boolean overlaps(Glyph o) {
            return x < o.x + o.w && o.x < x + w && top < o.bottom && o.top < bottom;
        }
    }

    private record Region(int x, int y, int w, int h, Consumer<Player> onClick, Component hover) {
        boolean contains(Glyph g) {
            int cx = g.x + g.w / 2;
            int cy = (g.top + g.bottom) / 2;
            return cx >= x && cx < x + w && cy >= y && cy < y + h;
        }
    }

    /** Ein Stück Text mit eigener Farbe (für mehrfarbige Zeilen). */
    public record Span(String text, TextColor color, boolean bold) {
        public static Span of(String text, TextColor color) {
            return new Span(text, color, false);
        }

        public static Span bold(String text, TextColor color) {
            return new Span(text, color, true);
        }
    }

    private final int width;
    private final List<Glyph> glyphs = new ArrayList<>();
    private final List<Region> regions = new ArrayList<>();
    private int topMargin;

    public Canvas(int width) {
        this.width = width;
    }

    public int width() {
        return width;
    }

    // =================================================================================================================
    // Text
    // =================================================================================================================

    /** Vorschub eines Textes in der Vanilla-Schrift (fett: +1 je Zeichen, auch Leerzeichen). */
    public static int width(String text, boolean bold) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += advance(text.charAt(i)) + (bold ? 1 : 0);
        }
        return w;
    }

    public static int width(List<Span> spans) {
        int w = 0;
        for (Span s : spans) w += width(s.text(), s.bold());
        return w;
    }

    private static int advance(char c) {
        int i = indexOf(c);
        return i < 0 ? advance('?') : KitData.TEXT_ADV.charAt(i) - '0';
    }

    private static int indexOf(char c) {
        int lo = 0, hi = KitData.TEXT_CHARS.length() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            char m = KitData.TEXT_CHARS.charAt(mid);
            if (m < c) lo = mid + 1;
            else if (m > c) hi = mid - 1;
            else return mid;
        }
        return -1;
    }

    /** Ersetzt Zeichen, die die Kit-Schrift nicht kennt, durch '?'. */
    public static String clean(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(indexOf(c) < 0 ? '?' : c);
        }
        return sb.toString();
    }

    /** Kürzt Text mit "..." auf höchstens max Pixel. */
    public static String fit(String text, boolean bold, int max) {
        if (width(text, bold) <= max) return text;
        String dots = "...";
        int budget = max - width(dots, bold);
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int a = advance(text.charAt(i)) + (bold ? 1 : 0);
            if (w + a > budget) break;
            sb.append(text.charAt(i));
            w += a;
        }
        return sb.toString().stripTrailing() + dots;
    }

    /** Bricht Text an Leerzeichen um, sodass jede Zeile höchstens max Pixel breit ist. */
    public static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String probe = line.isEmpty() ? word : line + " " + word;
            if (width(probe, false) > max && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    /** Text ab x (links). Gibt die Breite zurück. */
    public int text(int x, int y, String text, TextColor color, boolean bold) {
        String t = clean(text);
        int w = width(t, bold);
        if (t.isEmpty()) return 0;
        glyphs.add(new Glyph(Kind.TEXT, x, y, w, LINE, w, Z_TEXT, t, color, bold, 0, KitData.T_MAX, y - 3, y + 9));
        return w;
    }

    public int text(int x, int y, String text, TextColor color) {
        return text(x, y, text, color, false);
    }

    /** Text, der bei right endet. */
    public int textRight(int right, int y, String text, TextColor color, boolean bold) {
        int w = width(clean(text), bold);
        text(right - w, y, text, color, bold);
        return w;
    }

    /** Text mittig um cx. */
    public int textCenter(int cx, int y, String text, TextColor color, boolean bold) {
        int w = width(clean(text), bold);
        text(cx - w / 2, y, text, color, bold);
        return w;
    }

    /** Mehrfarbiger Text ab x. Gibt die Breite zurück. */
    public int text(int x, int y, List<Span> spans) {
        int cx = x;
        for (Span s : spans) cx += text(cx, y, s.text(), s.color(), s.bold());
        return cx - x;
    }

    public int textRight(int right, int y, List<Span> spans) {
        int w = width(cleanAll(spans));
        return text(right - w, y, spans);
    }

    private static List<Span> cleanAll(List<Span> spans) {
        List<Span> out = new ArrayList<>(spans.size());
        for (Span s : spans) out.add(new Span(clean(s.text()), s.color(), s.bold()));
        return out;
    }

    // =================================================================================================================
    // Hintergründe
    // =================================================================================================================

    private void piece(int x, int y, KitData.Piece p, int z) {
        glyphs.add(new Glyph(Kind.BG, x, y, p.width(), p.height(), p.advance(), z, String.valueOf(p.c()),
                NamedTextColor.WHITE, false, 0, KitData.B_MAX, y, y + p.height()));
    }

    /** Kasten mit Rahmen (mindestens 48 x 14). */
    public Canvas box(int x, int y, int w, int h, KitData.Box style) {
        return box(x, y, w, h, style, Z_BOX);
    }

    public Canvas box(int x, int y, int w, int h, KitData.Box style, int z) {
        w = Math.max(w, style.narrow != null ? 2 * KitData.CAP : 2 * KitData.CAP + KitData.SEG);
        h = Math.max(h, 14);
        row(x, y, w, style.p[0], style.narrow == null ? null : style.narrow[0], z);
        // Mittelzeilen dürfen in die geraden Teile der oberen und unteren Zeile ragen (ab Zeile style.straight)
        int lo = y + style.straight;
        int hi = y + h - LINE - style.straight;
        for (int p = y + LINE; p < y + h - LINE; p += LINE) {
            row(x, Math.max(Math.min(lo, hi), Math.min(p, hi)), w, style.p[1], style.narrow == null ? null : style.narrow[1], z);
        }
        row(x, y + h - LINE, w, style.p[2], style.narrow == null ? null : style.narrow[2], z);
        return this;
    }

    private void row(int x, int y, int w, KitData.Piece[] lmr, KitData.Piece[] narrow, int z) {
        KitData.Piece l = lmr[0], m = lmr[1], r = lmr[2];
        piece(x, y, l, z);
        span(x + l.width(), x + w - r.width(), y, m, narrow, z);
        piece(x + w - r.width(), y, r, z);
    }

    /** Füllt [a, b) mit Mittelstücken (überlappend) oder, wenn zu schmal, mit schmalen Stücken. */
    private void span(int a, int b, int y, KitData.Piece seg, KitData.Piece[] narrow, int z) {
        int len = b - a;
        if (len <= 0) return;
        if (len >= seg.width() || narrow == null) {
            for (int p = a; p < b; p += seg.width()) piece(Math.min(p, b - seg.width()), y, seg, z);
            return;
        }
        int p = a;
        for (KitData.Piece n : narrow) {
            while (b - p >= n.width()) {
                piece(p, y, n, z);
                p += n.width();
            }
        }
    }

    /** Knopf (22 px hoch, mindestens 12 breit). */
    public Canvas button(int x, int y, int w, KitData.Button style) {
        w = Math.max(w, 12);
        int cap = Math.min(style.l.width(), w / 2);
        piece(x, y, style.l, Z_BUTTON);
        span(x + cap, x + w - cap, y, style.m, style.narrow, Z_BUTTON);
        piece(x + w - style.r.width(), y, style.r, Z_BUTTON);
        return this;
    }

    /** Knopf mit Beschriftung in der Mitte. */
    public Canvas button(int x, int y, int w, KitData.Button style, String label, TextColor color, boolean bold) {
        button(x, y, w, style);
        textCenter(x + w / 2, y + 7, label, color, bold);
        return this;
    }

    /** Knopf, so breit wie die Beschriftung plus pad links und rechts. Gibt die Breite zurück. */
    public int labelButton(int x, int y, int pad, KitData.Button style, String label, TextColor color, boolean bold) {
        int w = width(clean(label), bold) - 1 + 2 * pad;
        button(x, y, w, style, label, color, bold);
        return w;
    }

    /** Kleiner Knopf (16 px hoch, mindestens 10 breit). */
    public Canvas smallButton(int x, int y, int w, KitData.Small style) {
        w = Math.max(w, 10);
        int cap = Math.min(style.l.width(), w / 2);
        piece(x, y, style.l, Z_BUTTON);
        span(x + cap, x + w - cap, y, style.m, style.narrow, Z_BUTTON);
        piece(x + w - style.r.width(), y, style.r, Z_BUTTON);
        return this;
    }

    /** Kleiner Knopf mit Beschriftung in der Mitte. */
    public Canvas smallButton(int x, int y, int w, KitData.Small style, String label, TextColor color, boolean bold) {
        smallButton(x, y, w, style);
        textCenter(x + w / 2, y + 4, label, color, bold);
        return this;
    }

    /** Zurück-Knopf 20 x 20 mit Pfeil. */
    public Canvas back(int x, int y) {
        piece(x, y, KitData.BACK, Z_BUTTON);
        return this;
    }

    /** Zurück-Knopf der Survival-Hilfe (22 x 22, Stil der Blätter-Knöpfe). */
    public Canvas backHelp(int x, int y) {
        piece(x, y, KitData.BACK_HELP, Z_BUTTON);
        return this;
    }

    /** Fortschrittsbalken (7 px hoch), frac 0..1. */
    public Canvas bar(int x, int y, int w, double frac, KitData.Fill fill) {
        w = Math.max(w, 2 * KitData.CAP);
        piece(x, y, KitData.BAR_L, Z_BUTTON);
        span(x + KitData.CAP, x + w - KitData.CAP, y, KitData.BAR_M, KitData.BAR_NARROW, Z_BUTTON);
        piece(x + w - KitData.CAP, y, KitData.BAR_R, Z_BUTTON);
        int inner = w - 2;
        int fw = (int) Math.round(Math.max(0, Math.min(1, frac)) * inner);
        if (frac > 0 && fw == 0) fw = 1;
        int p = x + 1;
        int end = x + 1 + fw;
        KitData.Piece big = fill.p[5];
        while (end - p >= big.width()) {
            piece(p, y + 1, big, Z_FILL);
            p += big.width();
        }
        for (int i = 4; i >= 0; i--) {
            KitData.Piece n = fill.p[i];
            while (end - p >= n.width()) {
                piece(p, y + 1, n, Z_FILL);
                p += n.width();
            }
        }
        return this;
    }

    // =================================================================================================================
    // Icons
    // =================================================================================================================

    /** Icon 16 x 16 (big: 32 x 32) mit linker oberer Ecke bei (x, y). */
    public Canvas icon(int x, int y, KitData.Icon icon, boolean big) {
        int size = big ? 32 : 16;
        char c = big ? icon.c32 : icon.c16;
        glyphs.add(new Glyph(Kind.ICON, x, y, size, size, big ? icon.adv32 : icon.adv16, Z_ICON, String.valueOf(c),
                NamedTextColor.WHITE, false, Math.max(KitData.I_MIN, 7 - size), KitData.I_MAX, y, y + size));
        return this;
    }

    /** Icon in Originalgröße (Chips, Sterne, Noten) mit linker oberer Ecke bei (x, y). Gibt den Vorschub zurück. */
    public int smallIcon(int x, int y, KitData.SmallIcon icon) {
        glyphs.add(new Glyph(Kind.ICON, x, y, icon.advance - 1, icon.height, icon.advance, Z_ICON,
                String.valueOf(icon.c), NamedTextColor.WHITE, false, Math.max(KitData.I_MIN, 7 - KitData.SMALL_CELL),
                KitData.I_MAX, y, y + icon.height));
        return icon.advance;
    }

    // =================================================================================================================
    // Titelschrift
    // =================================================================================================================

    /** Breite eines Titels in der großen Titelschrift. */
    public static int titleWidth(String text) {
        int w = 0;
        for (char c : titleText(text).toCharArray()) {
            int i = KitData.TITLE_CHARS.indexOf(c);
            w += KitData.TITLE_ADV.charAt(i) - '0';
        }
        return w;
    }

    private static String titleText(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            sb.append(KitData.TITLE_CHARS.indexOf(c) < 0 ? '?' : c);
        }
        return sb.toString();
    }

    /**
     * Titel in der großen Titelschrift (Versalhöhe 17 px), y = Oberkante der Großbuchstaben. gold = fertig gefärbt
     * (Gold-Verlauf), sonst hell und mit color eingefärbt. Gibt die Breite zurück.
     */
    /** Zeichen der Titelschrift für einen Text (Schriften kuby:title und kuby:k/i...). */
    public static String titleChars(String text, boolean gold) {
        String t = titleText(text);
        char base = gold ? KitData.TITLE_GOLD : KitData.TITLE_WHITE;
        StringBuilder chars = new StringBuilder(t.length());
        for (char c : t.toCharArray()) chars.append((char) (base + KitData.TITLE_CHARS.indexOf(c)));
        return chars.toString();
    }

    public int title(int x, int y, String text, boolean gold, TextColor color) {
        String t = titleText(text);
        String chars = titleChars(t, gold);
        int w = titleWidth(t);
        int top = y - KitData.TITLE_TOP;
        glyphs.add(new Glyph(Kind.ICON, x, top, w, KitData.TITLE_H, w, Z_TEXT, chars,
                gold || color == null ? NamedTextColor.WHITE : color, false,
                Math.max(KitData.I_MIN, 7 - KitData.TITLE_H), KitData.I_MAX, top, top + KitData.TITLE_H));
        return w;
    }

    public int title(int x, int y, String text) {
        return title(x, y, text, true, null);
    }

    /** Small Caps für die Kit-Schriften (ᴀʙᴄ ..., mit eigenen Umlauten ᴀ̈ ᴏ̈ ᴜ̈). */
    public static String caps(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            switch (c) {
                case 'ä' -> sb.append('\ue0f0');
                case 'ö' -> sb.append('\ue0f1');
                case 'ü' -> sb.append('\ue0f2');
                default -> {
                    int i = "abcdefghijklmnopqrstuvwxyz".indexOf(c);
                    sb.append(i >= 0 ? "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ".charAt(i) : c);
                }
            }
        }
        return sb.toString();
    }

    // =================================================================================================================
    // Klicks
    // =================================================================================================================

    /** Macht ein Rechteck klickbar; alle Zeichen, deren Mitte darin liegt, bekommen Klick und Tooltip. */
    public Canvas action(int x, int y, int w, int h, Consumer<Player> onClick, Component hover) {
        regions.add(new Region(x, y, w, h, onClick, hover));
        return this;
    }

    public Canvas action(int x, int y, int w, int h, Consumer<Player> onClick, String hover) {
        return action(x, y, w, h, onClick, hover == null ? null
                : Component.text(hover, NamedTextColor.GRAY));
    }

    // =================================================================================================================
    // Bauen
    // =================================================================================================================

    /** Zusätzlicher Rand oben in Pixeln (wird auf ganze Zeilen aufgerundet), nach dem Bauen bekannt. */
    public int topMargin() {
        return topMargin;
    }

    /** Ordnet die Zeichen den Zeilen zu und baut den Text. Klicks gelten für diesen Spieler bis zum nächsten Menü. */
    public Component build(Player viewer) {
        List<String> tokens = new ArrayList<>();
        UiClicks.Session session = viewer == null ? null : UiClicks.newSession(viewer);
        for (Region r : regions) tokens.add(session == null || r.onClick() == null ? null : session.register(r.onClick()));
        for (Glyph g : glyphs) {
            for (int i = regions.size() - 1; i >= 0; i--) {
                if (regions.get(i).contains(g)) {
                    g.region = regions.get(i);
                    break;
                }
            }
        }
        assignLines();
        int maxLine = 0;
        for (Glyph g : glyphs) maxLine = Math.max(maxLine, g.line);
        List<List<Glyph>> lines = new ArrayList<>();
        for (int i = 0; i <= maxLine; i++) lines.add(new ArrayList<>());
        for (Glyph g : glyphs) lines.get(g.line).add(g);

        TextComponent.Builder all = Component.text();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) all.append(Component.newline());
            all.append(buildLine(lines.get(i), tokens));
        }
        return all.build();
    }

    /** Zeilen so spät wie möglich, aber vor allem, was darüber liegt. */
    private void assignLines() {
        List<Glyph> order = new ArrayList<>(glyphs);
        order.sort(Comparator.comparingInt((Glyph g) -> -g.z));
        List<Glyph> placed = new ArrayList<>();
        int minLine = Integer.MAX_VALUE;
        for (Glyph g : order) {
            int latest = Math.floorDiv(g.y - g.minShift, LINE);
            int earliest = -Math.floorDiv(-(g.y - g.maxShift), LINE);
            int line = latest;
            for (Glyph o : placed) {
                if (o.z > g.z && o.line <= line && g.overlaps(o)) line = o.line - 1;
            }
            if (line < 0) line = 0;                  // ganz oben: in derselben Zeile darunter einordnen (buildLine)
            if (line < earliest) line = earliest;    // zu weit verschoben: Schrift fehlt
            g.line = line;
            placed.add(g);
            minLine = Math.min(minLine, line);
        }
        topMargin = 0;
    }

    private record Style(Key font, TextColor color, boolean bold, boolean noShadow, Region region) {}

    private Component buildLine(List<Glyph> line, List<String> tokens) {
        // erst nach Ebene, dann von links nach rechts: Überlappendes in einer Zeile liegt so richtig übereinander
        line.sort(Comparator.comparingInt((Glyph g) -> g.z).thenComparingInt(g -> g.x));
        List<Style> styles = new ArrayList<>();
        List<StringBuilder> texts = new ArrayList<>();
        int cursor = 0;
        for (Glyph g : line) {
            int lineIndex = g.line;
            int shift = g.y + topMargin - lineIndex * LINE;
            Key font = Key.key("kuby", switch (g.kind) {
                case TEXT -> "k/t" + shift;
                case BG -> "k/b" + shift;
                case ICON -> "k/i" + shift;
            });
            int gap = g.x - cursor;
            if (gap != 0) {
                Style last = styles.isEmpty() ? null : styles.get(styles.size() - 1);
                if (last != null && !last.bold() && last.region() == null) {
                    texts.get(texts.size() - 1).append(Ui.spaceChars(gap));
                } else {
                    styles.add(new Style(font, null, false, false, null));
                    texts.add(new StringBuilder(Ui.spaceChars(gap)));
                }
            }
            Style st = new Style(font, g.color, g.bold, g.kind != Kind.TEXT || dark(g.color), g.region);
            Style last = styles.isEmpty() ? null : styles.get(styles.size() - 1);
            if (last != null && last.equals(st)) {
                texts.get(texts.size() - 1).append(g.chars);
            } else if (last != null && last.color() == null && last.font().equals(font) && !g.bold && g.region == null
                    && g.kind != Kind.TEXT) {
                // reine Leerzeichen davor: in diesen Lauf übernehmen
                styles.set(styles.size() - 1, st);
                texts.get(texts.size() - 1).append(g.chars);
            } else {
                styles.add(st);
                texts.add(new StringBuilder(g.chars));
            }
            cursor = g.x + g.advance;
        }
        if (width != cursor) {
            Style last = styles.isEmpty() ? null : styles.get(styles.size() - 1);
            if (last != null && !last.bold() && last.region() == null) {
                texts.get(texts.size() - 1).append(Ui.spaceChars(width - cursor));
            } else {
                styles.add(new Style(Key.key("kuby", "k/t0"), null, false, false, null));
                texts.add(new StringBuilder(Ui.spaceChars(width - cursor)));
            }
        }
        TextComponent.Builder b = Component.text();
        for (int i = 0; i < styles.size(); i++) {
            Style s = styles.get(i);
            TextComponent.Builder c = Component.text().content(texts.get(i).toString()).font(s.font());
            if (s.color() != null) c.color(s.color());
            if (s.bold()) c.decoration(TextDecoration.BOLD, true);
            if (s.noShadow()) c.shadowColor(ShadowColor.none());
            if (s.region() != null) {
                int idx = regions.indexOf(s.region());
                String token = idx >= 0 ? tokens.get(idx) : null;
                if (token != null) c.clickEvent(UiClicks.click(token));
                if (s.region().hover() != null) c.hoverEvent(HoverEvent.showText(s.region().hover()));
            }
            b.append(c.build());
        }
        return b.build();
    }

    /** Dunkle Schrift (z. B. auf Gold- oder Grün-Knöpfen) ohne Schatten, wie in den Referenz-Menüs. */
    private static boolean dark(TextColor c) {
        if (c == null) return false;
        return 0.299 * c.red() + 0.587 * c.green() + 0.114 * c.blue() < 64;
    }

    @Override
    public String toString() {
        return "Canvas[" + width + ", " + glyphs.size() + " Zeichen]";
    }

    @SuppressWarnings("unused")
    private static boolean same(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
