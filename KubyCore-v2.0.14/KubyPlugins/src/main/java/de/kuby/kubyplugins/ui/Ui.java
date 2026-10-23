package de.kuby.kubyplugins.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Baukasten für Menüs mit eigener Grafik aus dem Resource Pack "KubyUI" (Schriftart kuby:ui).
 * Die Grafiken sind Zeichen dieser Schrift, Leerzeichen-Zeichen verschieben den Cursor pixelgenau.
 * Kästen werden komplett aus der ersten (leeren) Zeile gezeichnet, deren Breite exakt bekannt ist.
 * Darum sitzen die Rahmen immer genau, nur der Text hängt von der geschätzten Zeichenbreite ab.
 * Die Zeichen und Breiten müssen zu tools/build_ui_pack.py passen.
 */
public final class Ui {

    public static final Key FONT = Key.key("kuby", "ui");
    public static final int PANEL_W = 300;
    private static final int STRIP_ADV = PANEL_W + 1;
    private static final int MAX_ROWS = 63;
    private static final char PANEL_TOP = '';
    private static final int MID_BASE = 0xE100;
    private static final int BOTTOM_BASE = 0xE140;
    private static final char TITLEBAR = '';
    private static final int TITLEBAR_W = 160;
    private static final char BUTTON_DARK = '';
    private static final char BUTTON_RED = '';
    /** Breite der Dialog-Buttons mit eigener Grafik: drei nebeneinander passen unter einen Kasten. */
    public static final int BUTTON_W = 98;

    /** Icons mit ihrer Vorschubbreite in Pixeln. */
    public enum Icon {
        BACK('', 14), DICE('', 16), CARDS('', 16), ROULETTE('', 17), SLOTS('', 16),
        CRASH('', 16), POKER('', 13), TROPHY('', 16), MUSIC('', 15),
        BULLET('', 4), CLUB('', 8), DIAMOND('', 8), HEART('', 8), STAR('', 8),
        SEVEN('', 8), COIN('', 10);

        final char c;
        final int advance;

        Icon(char c, int advance) {
            this.c = c;
            this.advance = advance;
        }
    }

    private Ui() {
    }

    // ---------- Grundbausteine ----------

    /** Text in der UI-Schrift, weiß (damit die Grafik nicht eingefärbt wird) und ohne Schatten. */
    static Component glyph(String s) {
        return Component.text(s).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /** Verschiebt den Cursor um px Pixel (auch negativ). */
    public static String spaceChars(int px) {
        StringBuilder sb = new StringBuilder();
        int base = px < 0 ? 0xF801 : 0xF811;
        int n = Math.abs(px);
        for (int bit = 9; bit >= 0; bit--) {
            while (n >= (1 << bit)) {
                sb.append((char) (base + bit));
                n -= 1 << bit;
            }
        }
        return sb.toString();
    }

    public static Component space(int px) {
        return px == 0 ? Component.empty() : glyph(spaceChars(px));
    }

    public static Component icon(Icon icon) {
        return glyph(String.valueOf(icon.c));
    }

    /** Geschätzte Breite in der Standardschrift (Vorschub je Zeichen, fett +1). */
    public static int width(String text, boolean bold) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += charWidth(text.charAt(i)) + (bold ? 1 : 0);
        }
        return w;
    }

    private static int charWidth(char c) {
        return switch (c) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case '"', 'I', '[', ']', 't', ' ' -> 4;
            case '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }

    // ---------- Titel und Buttons ----------

    /** Titelleiste mit Text und Münze, mittig. Gesamtbreite exakt 160. */
    public static Component titleBar(String text) {
        int t = width(text, true);
        int content = t + 3 + Icon.COIN.advance;
        int a = (TITLEBAR_W - content) / 2;
        return Component.empty()
                .append(glyph(String.valueOf(TITLEBAR)))
                .append(space(a - (TITLEBAR_W + 1)))
                .append(Component.text(text, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(space(3))
                .append(icon(Icon.COIN))
                .append(space(TITLEBAR_W - a - content));
    }

    /**
     * Button-Beschriftung mit eigenem Hintergrund (Button-Breite muss BUTTON_W sein). Der Hintergrund hat in Summe
     * keinen Vorschub, die Beschriftung ist also genau so breit wie der Text. Minecraft zentriert sie mit der echten
     * Textbreite, der Text sitzt damit immer exakt mittig. Der Hintergrund wird vom Textanfang aus zum Button-Rand
     * zurückgesetzt (nach der geschätzten Breite).
     */
    public static Component button(String text, boolean red) {
        int t = width(text, false);
        int toLeft = -((BUTTON_W - t) / 2);
        return Component.empty()
                .append(space(toLeft))
                .append(glyph(String.valueOf(red ? BUTTON_RED : BUTTON_DARK)))
                .append(space(-(BUTTON_W + 1) - toLeft))
                .append(Component.text(text, NamedTextColor.WHITE));
    }

    // ---------- Kästen ----------

    /** Ein Stück einer Zeile: Text, Icon oder Abstand, mit bekannter oder geschätzter Breite. */
    private record Part(Component component, int width) {}

    /** Kasten mit roter Umrandung, Icons im Kopf und Textzeilen. Zeilen werden links ausgerichtet. */
    public static final class Panel {
        private final List<List<Part>> lines = new ArrayList<>();
        private final List<int[]> headIcons = new ArrayList<>();
        private final List<Icon> headIconTypes = new ArrayList<>();

        /** Icon im Kopf des Kastens bei x Pixel vom linken Rand (geht über die ersten zwei Textzeilen). */
        public Panel headIcon(Icon icon, int x) {
            headIcons.add(new int[]{x});
            headIconTypes.add(icon);
            return this;
        }

        /** Zeile mit Text ab x, beliebige Farbe. */
        public Panel text(int x, String text, TextColor color, boolean bold) {
            List<Part> line = new ArrayList<>();
            line.add(gap(x));
            line.add(textPart(text, color, bold));
            lines.add(line);
            return this;
        }

        /** Aufzählungspunkt mit Text, umbrochen auf die Kastenbreite. {club} {diamond} {heart} {star} {seven} werden Icons. */
        public Panel bullet(String text, TextColor color) {
            int indent = 15;
            int max = PANEL_W - indent - 8;
            List<List<Part>> wrapped = wrap(text, color, max);
            for (int i = 0; i < wrapped.size(); i++) {
                List<Part> line = new ArrayList<>();
                if (i == 0) {
                    line.add(gap(7));
                    line.add(new Part(icon(Icon.BULLET), Icon.BULLET.advance));
                    line.add(gap(indent - 7 - Icon.BULLET.advance));
                } else {
                    line.add(gap(indent));
                }
                line.addAll(wrapped.get(i));
                lines.add(line);
            }
            return this;
        }

        /** Zwei Spalten: links ab 8, rechts ab rightX. Passt beides nicht in eine Zeile, kommt rechts in die nächste. */
        public Panel columns(String left, TextColor leftColor, String right, TextColor rightColor, int rightX) {
            List<Part> line = new ArrayList<>();
            line.add(gap(8));
            Part l = textPart(left, leftColor, false);
            Part r = textPart(right, rightColor, false);
            line.add(l);
            int x = Math.max(rightX, 8 + l.width() + 6);
            if (x + r.width() <= PANEL_W - 8) {
                line.add(gap(x - 8 - l.width()));
                line.add(r);
                lines.add(line);
            } else {
                lines.add(line);
                List<Part> next = new ArrayList<>();
                next.add(gap(rightX));
                next.add(r);
                lines.add(next);
            }
            return this;
        }

        public Panel blank() {
            lines.add(new ArrayList<>());
            return this;
        }

        /** Baut den Kasten: Kopfzeile mit Rahmen und Icons, Textzeilen, untere Randzeile. */
        public List<Component> build() {
            int n = Math.min(lines.size(), MAX_ROWS - 1);
            StringBuilder strips = new StringBuilder();
            String back = spaceChars(-STRIP_ADV);
            strips.append(PANEL_TOP).append(back);
            for (int k = 1; k <= n; k++) strips.append((char) (MID_BASE + k)).append(back);
            strips.append((char) (BOTTOM_BASE + n + 1)).append(back);
            TextComponent.Builder head = Component.text().append(glyph(strips.toString()));
            int cursor = 0;
            for (int i = 0; i < headIcons.size(); i++) {
                Icon icon = headIconTypes.get(i);
                int x = headIcons.get(i)[0];
                head.append(space(x - cursor)).append(icon(icon));
                cursor = x + icon.advance;
            }
            head.append(space(PANEL_W - cursor));

            List<Component> out = new ArrayList<>();
            out.add(head.build());
            for (int i = 0; i < n; i++) {
                TextComponent.Builder line = Component.text();
                int used = 0;
                for (Part p : lines.get(i)) {
                    line.append(p.component());
                    used += p.width();
                }
                line.append(space(PANEL_W - used));
                out.add(line.build());
            }
            out.add(space(PANEL_W));
            return out;
        }
    }

    /** Fügt Kästen mit einer Leerzeile Abstand zu einer mehrzeiligen Komponente zusammen. */
    public static Component stack(List<Panel> panels) {
        TextComponent.Builder all = Component.text();
        boolean first = true;
        for (Panel panel : panels) {
            if (!first) all.append(Component.newline()).append(space(PANEL_W)).append(Component.newline());
            boolean firstLine = true;
            for (Component line : panel.build()) {
                if (!firstLine) all.append(Component.newline());
                all.append(line);
                firstLine = false;
            }
            first = false;
        }
        return all.build();
    }

    // ---------- Hilfen ----------

    private static Part gap(int px) {
        return new Part(space(px), px);
    }

    private static Part textPart(String text, TextColor color, boolean bold) {
        Component c = Component.text(text, color);
        if (bold) c = c.decorate(TextDecoration.BOLD);
        return new Part(c, width(text, bold));
    }

    /** Bricht Text in Zeilen von höchstens max Pixeln um; Platzhalter wie {heart} werden Icons. */
    private static List<List<Part>> wrap(String text, TextColor color, int max) {
        List<List<Part>> out = new ArrayList<>();
        List<Part> line = new ArrayList<>();
        int used = 0;
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            Part part = token(word, color);
            int need = part.width() + (line.isEmpty() ? 0 : charWidth(' '));
            if (!line.isEmpty() && used + need > max) {
                out.add(line);
                line = new ArrayList<>();
                used = 0;
                need = part.width();
            }
            if (!line.isEmpty()) line.add(textPart(" ", color, false));
            line.add(part);
            used += need;
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private static Part token(String word, TextColor color) {
        Icon icon = switch (word) {
            case "{club}" -> Icon.CLUB;
            case "{diamond}" -> Icon.DIAMOND;
            case "{heart}" -> Icon.HEART;
            case "{star}" -> Icon.STAR;
            case "{seven}" -> Icon.SEVEN;
            default -> null;
        };
        return icon == null ? textPart(word, color, false) : new Part(icon(icon), icon.advance);
    }
}
