package de.kuby.kubyplugins.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Kleine Bausteine für Menüs mit dem Resource Pack "KubyUI": pixelgenaue Abstände (Leerzeichen-Zeichen der Schrift
 * kuby:ui), die Breite der normalen Dialog-Buttons und die Farbthemen der Survival-Hilfe. Die eigentliche Grafik
 * zeichnet {@link Canvas} (Dialog-Kit).
 */
public final class Ui {

    private static final Key FONT = Key.key("kuby", "ui");
    /** Breite der Dialog-Buttons: drei nebeneinander passen unter einen Kasten. */
    public static final int BUTTON_W = 98;

    /** Farbthemen der Hilfe-Seiten. */
    public enum Theme {
        CASINO(0xFF5D73, 0xFF4D67),
        GREEN(0x5FE07A, 0x5FE07A),
        GOLD(0xF2C84A, 0xF2C84A),
        GRAY(0xD2D2DA, 0xD2D2DA),
        ORANGE(0xF0A050, 0xF0A050),
        BLUE(0x64C0FF, 0x64C0FF),
        RED(0xFF6478, 0xFF6478),
        PINK(0xFF7AC0, 0xFF7AC0);

        /** Farbe für Überschriften wie "SO GEHT'S". */
        public final TextColor accent;
        /** Farbe für den Seitentitel. */
        public final TextColor title;

        Theme(int accent, int title) {
            this.accent = TextColor.color(accent);
            this.title = TextColor.color(title);
        }
    }

    private Ui() {
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
        return px == 0 ? Component.empty()
                : Component.text(spaceChars(px)).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /** Geschätzte Breite in der Standardschrift (Vorschub je Zeichen, fett +1), z. B. für die HUD-Leiste. */
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
}
