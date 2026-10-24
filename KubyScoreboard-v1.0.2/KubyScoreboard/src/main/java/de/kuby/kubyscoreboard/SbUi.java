package de.kuby.kubyscoreboard;

import de.kuby.kubyplugins.ui.Ui;

/**
 * Sidebar mit Grafik aus dem Pack KubyUI (Schrift kubyplugins:sb), Maße nach der Referenz:
 * Sidebar 116 px breit, goldener Rahmen 125 px (6 px links vom Text bis zum Bildschirmrand), 6 px Rand,
 * Holzranken an den Seiten. Die Texturen haben links 5 px extra für Ranken und Blätter.
 * Der ganze Rahmen wird aus dem Titel gezeichnet (ein Stück je Zeile, nach unten versetzt), das Logo ragt über
 * den Rahmen hinaus. Der Titel ist genau W Pixel breit, alle Zeilen sind schmaler, darum sitzt der Rahmen
 * pixelgenau. Muss zu tools/build_sb_pack.py passen.
 */
final class SbUi {

    /** Breite der Sidebar in Pixeln (= Titelbreite). */
    static final int W = 116;
    /** Innenbreite des Rahmens; der Text beginnt am linken Innenrand. */
    static final int INNER = 113;
    static final String FONT = "kubyplugins:sb";
    /** Texturbeginn links vom Text: 6 px Rahmen + 5 px Platz für Ranken. */
    private static final int FRAME_LEFT = 6 + 5;
    private static final int FRAME_ADV = 125 + 5 + 1;
    /** Vanilla zeigt höchstens 15 Zeilen. */
    static final int MAX_LINES = 15;

    private SbUi() {
    }

    /** Titel mit Rahmen für lineCount Zeilen und dem Logo des Modus. */
    static String title(String mode, int lineCount) {
        int n = Math.max(2, Math.min(lineCount, MAX_LINES));
        String back = Ui.spaceChars(-FRAME_ADV);
        // Casino: eigener rot-oranger Rahmen mit Deko (0xE180 / 0xE1C0), sonst Gold mit Ranken (0xE100 / 0xE140)
        int top = "casino".equals(mode) ? 0xE180 : 0xE100;
        int bottom = "casino".equals(mode) ? 0xE1C0 : 0xE140;
        StringBuilder g = new StringBuilder(Ui.spaceChars(-FRAME_LEFT));
        g.append((char) top).append(back);
        for (int k = 1; k <= n - 2; k++) g.append((char) (top + k)).append(back);
        // Unterteil: die letzten zwei Zeilen und der untere Rand
        g.append((char) (bottom + n - 1)).append(back);
        g.append(glyph(mode, ''));
        g.append(Ui.spaceChars(W - (FRAME_ADV - FRAME_LEFT)));
        return "<font:" + FONT + ">" + g + "</font>";
    }

    /** Normale Zeile: 1 px Abstand zum Rahmen. */
    static String indent(String mini) {
        return indent(mini, 1);
    }

    /** Zeile mit px Pixel Abstand zum Rahmen. */
    static String indent(String mini, int px) {
        return mini.isEmpty() ? mini : "<font:" + FONT + ">" + Ui.spaceChars(px) + "</font>" + mini;
    }

    /** Zentriert eine Zeile pixelgenau im Rahmen. */
    static String center(String mini) {
        int free = INNER - ScoreboardText.width(ScoreboardText.plain(mini));
        int shift = Math.max(0, free / 2);
        return shift == 0 ? mini : "<font:" + FONT + ">" + Ui.spaceChars(shift) + "</font>" + mini;
    }

    private static char glyph(String mode, char base) {
        return switch (mode) {
            case "smp" -> base;
            case "survival" -> (char) (base + 1);
            case "casino" -> (char) (base + 3);
            default -> (char) (base + 2);
        };
    }
}
