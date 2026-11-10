package de.kuby.kubyplugins.ui;

import de.kuby.kubyplugins.service.InfoBar.Design;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;
import java.util.Locale;

/**
 * Baukasten der Info-Leiste oben (Bossbar-Titel aus Zeichen der Schrift kubyplugins:hud): Leisten im gewählten Design
 * nebeneinander, Inhalt aus Symbolen und Text. Genutzt von SurvivalHud und SkyblockHud. Muss zu
 * tools/build_sb_pack.py (build_infobar) passen: je Design Mittelstück 1, 2, 4, ..., 64 px ab {@link Design#base()},
 * Enden bei base + 8 und base + 9.
 */
public final class HudBar {

    public static final Key FONT = Key.key("kubyplugins", "hud");
    private static final int[] TILE_WIDTHS = {64, 32, 16, 8, 4, 2, 1};
    /** Münze, Juwel, Angel, Punkt der Leiste (build_infobar). */
    public static final char I_COIN = '', I_GEM = '', I_ROD = '', DOT = '';
    /** Skyblock: Insel, XP, Generator (build_infobar, 8 x 8). */
    public static final char I_ISLAND = '', I_XP = '', I_GEN = '';
    /** Kompass und Uhr aus dem Scoreboard (8 x 8, 1 px über der Schrift). */
    public static final char I_COMPASS = '', I_CLOCK = '';
    private static final int GAP = 4;                          // Abstand zwischen den Leisten
    private static final TextColor DOT_COLOR = TextColor.color(0x545454);

    private HudBar() {
    }

    /** Farbe der Schrift: helle Designs (Wald, Wolke) dunkel, die anderen hell. */
    public static TextColor color(Design d, int light, int dark) {
        return TextColor.color(d.dark ? dark : light);
    }

    public static String number(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    /** Die Leisten-Schrift kennt nur ASCII. */
    public static String ascii(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae")
                .replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss").toCharArray()) {
            b.append(ch >= 0x21 && ch <= 0x7E || ch == ' ' ? ch : '?');
        }
        return b.toString();
    }

    /** Ein Stück Inhalt: Platz im Layout und tatsächlicher Vorschub der Zeichen. */
    public record Part(Component c, int width, int advance) {
        /** Grafik width px breit, danach gap px Abstand. */
        public static Part icon(char ch, int width, int gap) {
            return new Part(glyph(String.valueOf(ch)), width + gap, width + 1);
        }

        /** Text: in hellen Designs dunkel ohne Schatten, sonst hell mit Schatten (wie Minecraft-Schrift). */
        public static Part text(String s, TextColor color, Design d) {
            int w = Ui.width(s, false);
            Component c = Component.text(s, color).font(FONT);
            return new Part(d.dark ? c : c.shadowColor(ShadowColor.none()), w, w);
        }

        public static Part gap(int px) {
            return new Part(Component.empty(), px, 0);
        }

        /** „ · “: Leerzeichen, Punkt (1 px, dunkelgrau), Leerzeichen. */
        public static List<Part> dot(Design d) {
            Component c = Component.text(String.valueOf(DOT), DOT_COLOR).font(FONT);
            return List.of(gap(4), new Part(d.dark ? c : c.shadowColor(ShadowColor.none()), 2, 2), gap(4));
        }
    }

    public static Component glyph(String s) {
        return Component.text(s).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /**
     * Setzt die Leisten nebeneinander: linkes Ende (d.cap breit), Mittelstück in Binärstücken, rechtes Ende; der
     * Inhalt beginnt 1 px nach dem linken Ende und endet am rechten. Der Cursor wird mit Leerzeichen der Schrift gesetzt,
     * die Gesamtbreite stimmt, damit Minecraft die Leiste richtig mittig setzt.
     */
    public static final class Strip {
        private final TextComponent.Builder out = Component.text();
        private final Design design;
        private final int base;
        private int cursor;
        private int x;
        private boolean any;

        public Strip(Design d) {
            this.design = d;
            this.base = d.base();
        }

        public void panel(List<Part> parts) {
            any = true;
            int content = 0;
            for (Part p : parts) content += p.width();
            int body = content + 1;
            int cap = design.cap;
            int x0 = x;
            int pos = x0 + cap;
            int rest = body;
            for (int i = 0; i < TILE_WIDTHS.length; i++) {
                while (rest >= TILE_WIDTHS[i]) {
                    put(pos, String.valueOf((char) (base + (TILE_WIDTHS.length - 1 - i))), TILE_WIDTHS[i] + 1);
                    pos += TILE_WIDTHS[i];
                    rest -= TILE_WIDTHS[i];
                }
            }
            put(x0, String.valueOf((char) (base + 8)), cap + 1);
            put(x0 + cap + body, String.valueOf((char) (base + 9)), cap + 1);
            int cx = x0 + cap + 1;
            for (Part p : parts) {
                moveTo(cx);
                out.append(p.c());
                cursor = cx + p.advance();
                cx += p.width();
            }
            x = x0 + 2 * cap + body + GAP;
        }

        public boolean empty() {
            return !any;
        }

        private void put(int at, String ch, int advance) {
            moveTo(at);
            out.append(glyph(ch));
            cursor = at + advance;
        }

        private void moveTo(int at) {
            if (at != cursor) out.append(glyph(Ui.spaceChars(at - cursor)));
            cursor = at;
        }

        public Component build() {
            if (any) moveTo(x - GAP);
            return out.build();
        }
    }
}
