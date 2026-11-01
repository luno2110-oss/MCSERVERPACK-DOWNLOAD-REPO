package de.kuby.kubyplugins.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Eigener Hintergrund für Truhen-Fenster: ein Zeichen der Schrift kuby:chest (Bild 176 px breit, so hoch wie das
 * Fenster) vor dem Titel, verschoben auf die linke obere Ecke des Fensters. Bilder: tools/build_chest_bg.py.
 * Nur für Spieler mit Pack verwenden, sonst stehen Kästchen im Titel.
 */
public final class ChestBg {

    /** Farbwelten in der Reihenfolge von build_chest_bg.THEMES. */
    public static final List<String> THEMES = List.of("market", "shop", "storage", "pickup", "heads", "sell", "steel", "sky");

    private static final Key FONT = Key.key("kuby", "chest");

    private ChestBg() {
    }

    /** Titel mit Hintergrund; unbekannte Farbwelt oder Reihen außerhalb 1..6: Titel unverändert. */
    public static Component title(String theme, int rows, Component title) {
        int t = THEMES.indexOf(theme);
        if (t < 0 || rows < 1 || rows > 6) return title;
        String bg = "" + (char) (0xE000 + 8 * t + rows) + "";
        return Component.text(bg).font(FONT).append(title);
    }
}
