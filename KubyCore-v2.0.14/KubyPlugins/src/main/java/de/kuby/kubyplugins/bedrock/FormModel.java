package de.kuby.kubyplugins.bedrock;

import de.kuby.kubyplugins.ui.Canvas;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Übersetzt ein Canvas-Menü (gezeichnet für Java mit eigenen Schriften) in ein Bedrock-Formular: Titel, Inhalt (Texte
 * in Lesereihenfolge mit Farben und kleinen Icons als Glyphen aus dem Bedrock-Pack) und ein Knopf je klickbarem
 * Bereich (Beschriftung aus dem Text im Bereich, bei kurzen Knöpfen wie „Kaufen“ mit dem Zeilentitel davor, Bild aus
 * dem Icon im Bereich oder in seiner Zeile). So bekommen alle Menüs ohne eigene Bedrock-Fassung ein sauberes Formular.
 */
public final class FormModel {

    public record Button(String text, String image, Consumer<Player> action, String url, String hover) {}

    public record Model(String title, String content, List<Button> buttons) {}

    private FormModel() {
    }

    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    /** Kit-Schrift zurück in normale Buchstaben (Small Caps, eigene Umlaute), unsichtbare Zeichen raus. */
    public static String plain(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            int i = SMALL.indexOf(c);
            if (i >= 0) b.append(Character.toUpperCase("abcdefghijklmnopqrstuvwxyz".charAt(i)));
            else if (c == '') b.append('Ä');
            else if (c == '') b.append('Ö');
            else if (c == '') b.append('Ü');
            else if (c >= '' && c <= '') continue;    // Leerzeichen-/Grafik-Zeichen der Java-Schriften
            else b.append(c);
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    /** Small Caps und Kit-Umlaute in normale Großbuchstaben (Bedrock-Schrift kennt sie nicht sicher); Tags bleiben. */
    public static String unsmall(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            int i = SMALL.indexOf(c);
            if (i >= 0) b.append(Character.toUpperCase("abcdefghijklmnopqrstuvwxyz".charAt(i)));
            else if (c == '\ue0f0') b.append('Ä');
            else if (c == '\ue0f1') b.append('Ö');
            else if (c == '\ue0f2') b.append('Ü');
            else b.append(c);
        }
        return b.toString();
    }

    private static final int[][] LEGACY = {
            {0x000000, '0'}, {0x0000AA, '1'}, {0x00AA00, '2'}, {0x00AAAA, '3'}, {0xAA0000, '4'}, {0xAA00AA, '5'},
            {0xFFAA00, '6'}, {0xAAAAAA, '7'}, {0x555555, '8'}, {0x5555FF, '9'}, {0x55FF55, 'a'}, {0x55FFFF, 'b'},
            {0xFF5555, 'c'}, {0xFF55FF, 'd'}, {0xFFFF55, 'e'}, {0xFFFFFF, 'f'}, {0xDDD605, 'g'}};

    /** Nächste Bedrock-Farbe (§0..§g) zu einer RGB-Farbe; sehr dunkle Texte (für helle Java-Kästen) werden hell. */
    public static String code(TextColor c) {
        if (c == null) return "§f";
        int v = c.value();
        int r = v >> 16 & 255, g = v >> 8 & 255, bl = v & 255;
        if (r + g + bl < 160) return "§7";
        String best = "§f";
        double bd = Double.MAX_VALUE;
        for (int[] e : LEGACY) {
            if (e[0] == 0) continue;
            int er = e[0] >> 16 & 255, eg = e[0] >> 8 & 255, eb = e[0] & 255;
            double d = 0.30 * (r - er) * (r - er) + 0.59 * (g - eg) * (g - eg) + 0.11 * (bl - eb) * (bl - eb);
            if (d < bd) {
                bd = d;
                best = "§" + (char) e[1];
            }
        }
        return best;
    }

    private static String hover(Component c) {
        return c == null ? "" : plain(PlainTextComponentSerializer.plainText().serialize(c));
    }

    private static boolean inside(Canvas.Piece p, Canvas.Click r) {
        int cx = p.x() + Math.max(1, p.w()) / 2, cy = p.y() + Math.max(1, p.h()) / 2;
        return cx >= r.x() && cx < r.x() + r.w() && cy >= r.y() - 2 && cy < r.y() + r.h() + 2;
    }

    private static int centerY(Canvas.Piece p) {
        return p.y() + Math.max(1, p.h()) / 2;
    }

    public static Model of(Canvas c) {
        String title = plain(PlainTextComponentSerializer.plainText().serialize(c.heading()));
        List<Canvas.Piece> pieces = new ArrayList<>();
        for (Canvas.Piece p : c.pieces()) {
            if (p.kind() == 0 && plain(p.text()).isEmpty()) continue;
            pieces.add(p);
        }
        List<Canvas.Click> clicks = new ArrayList<>();
        for (Canvas.Click k : c.clicks()) if (k.action() != null || k.url() != null) clicks.add(k);
        clicks.sort(Comparator.comparingInt(Canvas.Click::y).thenComparingInt(Canvas.Click::x));
        Set<Canvas.Piece> used = new HashSet<>();
        List<Button> buttons = new ArrayList<>();
        for (Canvas.Click k : clicks) {
            List<Canvas.Piece> in = new ArrayList<>();
            Canvas.Piece img = null;
            for (Canvas.Piece p : pieces) {
                if (!inside(p, k)) continue;
                if (p.kind() == 0) in.add(p);
                else if (img == null || p.w() * p.h() > img.w() * img.h()) img = p;
                used.add(p);
            }
            in.sort(Comparator.comparingInt(Canvas.Piece::y).thenComparingInt(Canvas.Piece::x));
            String label = join(in);
            String hov = hover(k.hover());
            // Kurze Knöpfe („Kaufen“, „Holen“, „›“) bekommen den Titel ihrer Zeile davor
            String context = "";
            if (plain(label).length() <= 14) {
                int cy = k.y() + k.h() / 2;
                Canvas.Piece best = null;
                for (Canvas.Piece p : pieces) {
                    if (used.contains(p) || p.x() >= k.x()) continue;
                    if (Math.abs(centerY(p) - cy) > Math.max(9, k.h() / 2 + 6)) continue;
                    if (insideAny(p, clicks)) continue;
                    if (p.kind() == 0) {
                        if (best == null || best.kind() != 0 || (p.bold() && !best.bold()) || (p.bold() == best.bold() && p.x() < best.x())) best = p;
                    } else if (img == null) {
                        img = p;
                    }
                }
                if (best != null && best.kind() == 0) {
                    context = plain(best.text());
                    used.add(best);
                }
            }
            String text;
            String l = plain(label).isEmpty() ? (hov.isEmpty() ? "›" : hov) : label;
            if (!context.isEmpty() && !plain(l).equalsIgnoreCase(context)) {
                text = "§l" + cut(context, 34) + "§r\n§8" + cut(plain(l), 40);
            } else {
                text = cut(l, 60);
            }
            String image = img == null ? null : BedrockAssets.image(img.kind(), img.text().isEmpty() ? 0 : img.text().charAt(0));
            buttons.add(new Button(text, image, k.action(), k.url(), hov));
        }
        // Inhalt: übrige Texte und kleine Icons zeilenweise
        List<Canvas.Piece> rest = new ArrayList<>();
        for (Canvas.Piece p : pieces) {
            if (used.contains(p)) continue;
            if (p.kind() != 0 && p.h() > 20) continue;
            rest.add(p);
        }
        rest.sort(Comparator.comparingInt(FormModel::centerY).thenComparingInt(Canvas.Piece::x));
        StringBuilder content = new StringBuilder();
        List<Canvas.Piece> line = new ArrayList<>();
        int ly = Integer.MIN_VALUE;
        for (Canvas.Piece p : rest) {
            if (!line.isEmpty() && Math.abs(centerY(p) - ly) > 5) {
                appendLine(content, line);
                line.clear();
            }
            if (line.isEmpty()) ly = centerY(p);
            line.add(p);
        }
        appendLine(content, line);
        return new Model(title.isEmpty() ? "Menü" : title, content.toString().trim(), buttons);
    }

    private static boolean insideAny(Canvas.Piece p, List<Canvas.Click> clicks) {
        for (Canvas.Click k : clicks) if (inside(p, k)) return true;
        return false;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Texte eines Knopfs: farbig, mehrere Zeilen des Knopfs mit Zeilenumbruch (Bedrock zeigt zwei Zeilen). */
    private static String join(List<Canvas.Piece> in) {
        StringBuilder b = new StringBuilder();
        int y = Integer.MIN_VALUE, lines = 0;
        for (Canvas.Piece p : in) {
            String t = plain(p.text());
            if (t.isEmpty()) continue;
            if (b.length() > 0) {
                if (Math.abs(centerY(p) - y) > 5) {
                    if (++lines >= 2) break;
                    b.append("§r\n");
                } else {
                    b.append(' ');
                }
            }
            y = centerY(p);
            b.append(code(p.color())).append(p.bold() ? "§l" : "").append(t).append("§r");
        }
        return b.toString();
    }

    private static void appendLine(StringBuilder out, List<Canvas.Piece> line) {
        if (line.isEmpty()) return;
        line.sort(Comparator.comparingInt(Canvas.Piece::x));
        // In Abschnitte teilen (große Lücke oder ein Icon nach Text = neue Zelle): zwei Abschnitte in einer Zeile mit ·,
        // mehr = Spalten (je eine Zeile)
        List<List<Canvas.Piece>> segs = new ArrayList<>();
        int right = Integer.MIN_VALUE;
        for (Canvas.Piece p : line) {
            boolean cellStart = p.kind() != 0 && !segs.isEmpty() && segs.get(segs.size() - 1).stream().anyMatch(q -> q.kind() == 0);
            if (segs.isEmpty() || p.x() - right > 40 || cellStart) segs.add(new ArrayList<>());
            segs.get(segs.size() - 1).add(p);
            right = Math.max(right, p.x() + p.w());
        }
        List<String> parts = new ArrayList<>();
        for (List<Canvas.Piece> seg : segs) {
            String t = segment(seg);
            if (!t.isEmpty()) parts.add(t);
        }
        if (parts.isEmpty()) return;
        if (parts.size() <= 2) out.append(String.join("  §8·§r  ", parts)).append('\n');
        else for (String t : parts) out.append(t).append('\n');
    }

    private static String segment(List<Canvas.Piece> seg) {
        StringBuilder b = new StringBuilder();
        int right = Integer.MIN_VALUE;
        boolean any = false;
        for (Canvas.Piece p : seg) {
            String part;
            if (p.kind() == 0) {
                String t = plain(p.text());
                if (t.isEmpty()) continue;
                part = code(p.color()) + (p.bold() ? "§l" : "") + t + "§r";
                any = true;
            } else {
                String g = p.text().isEmpty() ? null : BedrockAssets.glyph(p.kind(), p.text().charAt(0));
                if (g == null) continue;
                part = "§f" + g;
            }
            if (b.length() > 0) b.append(p.x() - right > 12 ? "  " : " ");
            b.append(part);
            right = p.x() + p.w();
        }
        return any ? b.toString() : "";
    }

    /** Für Tests ohne Spieler: Farbe aus dem Namen. */
    static TextColor named(String n) {
        NamedTextColor c = NamedTextColor.NAMES.value(n);
        return c == null ? NamedTextColor.WHITE : c;
    }
}
