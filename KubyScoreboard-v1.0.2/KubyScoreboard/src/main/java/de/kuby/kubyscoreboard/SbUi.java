package de.kuby.kubyscoreboard;

import de.kuby.kubyplugins.ui.Ui;

/**
 * Sidebar mit Grafik aus dem Pack KubyUI (Schrift kubyplugins:sb): goldener Rahmen, Logo je Modus, Deko unten.
 * Der ganze Rahmen wird aus dem Titel gezeichnet (Streifen je Zeile, nach unten versetzt). Der Titel ist genau
 * W Pixel breit, alle Zeilen sind schmaler. Damit ist die Sidebar immer genau W breit und der Rahmen sitzt
 * pixelgenau. Er ragt links und rechts 2 px über und deckt so den Standard-Hintergrund ab.
 * Muss zu tools/build_ui_pack.py passen.
 */
final class SbUi {

    /** Breite der Sidebar in Pixeln. */
    static final int W = 140;
    static final String FONT = "kubyplugins:sb";
    private static final int FRAME_ADV = W + 4 + 1;
    private static final int LOGO_ADV = 90;
    private static final int MAX_LINES = 39;

    private SbUi() {
    }

    /** Titel mit Rahmen für lineCount Zeilen und dem Logo des Modus. */
    static String title(String mode, int lineCount) {
        int n = Math.min(lineCount, MAX_LINES);
        String back = Ui.spaceChars(-FRAME_ADV);
        StringBuilder g = new StringBuilder(Ui.spaceChars(-2));
        g.append('').append(back);
        for (int k = 1; k <= n; k++) g.append((char) (0xE100 + k)).append(back);
        g.append((char) (0xE140 + n + 1)).append(back);
        g.append(Ui.spaceChars(2));
        char logo = switch (mode) {
            case "smp" -> '';
            case "survival" -> '';
            default -> '';
        };
        int cx = (W - LOGO_ADV) / 2;
        g.append(Ui.spaceChars(cx)).append(logo).append(Ui.spaceChars(W - cx - LOGO_ADV));
        return "<font:" + FONT + ">" + g + "</font>";
    }

    /** Zentriert eine Zeile pixelgenau in der Sidebar (die Zeile beginnt später mit einem Leerzeichen). */
    static String center(String mini) {
        int free = W - ScoreboardText.width(ScoreboardText.plain(mini));
        int shift = Math.max(0, free / 2 - 4);
        return shift == 0 ? mini : "<font:" + FONT + ">" + Ui.spaceChars(shift) + "</font>" + mini;
    }
}
