package de.kuby.kubyscoreboard;

import de.kuby.kubyplugins.util.SmallCaps;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hilfsfunktionen fürs Scoreboard: Pixelbreite messen, zu breite Zeilen kürzen, Geld formatieren,
 * Icons und Rahmen aus dem Resource Pack (KubyPlugins-ResourcePack.zip).
 */
public final class ScoreboardText {

    /** Schrift mit Rahmen und Icons aus dem Resource Pack. */
    public static final String FONT = "kubyplugins:sb";

    /** Rahmen-Glyphe, 152 Pixel breit. */
    private static final String PANEL = "\uE000";
    /** Zieht den Cursor um die Rahmenbreite zurück, damit der Text über dem Rahmen steht. */
    private static final String BACK = "\uE001";

    public static final String I_COIN = "\uE010";
    public static final String I_GEM = "\uE011";
    public static final String I_XP = "\uE012";
    public static final String I_TROPHY = "\uE013";
    public static final String I_SWORD = "\uE014";
    public static final String I_SKULL = "\uE015";
    public static final String I_CLOCK = "\uE016";
    public static final String I_PEOPLE = "\uE017";
    public static final String I_RANK = "\uE018";
    public static final String I_TEAM = "\uE019";
    public static final String I_CLAIM = "\uE01A";
    public static final String I_TOKEN = "\uE01B";
    public static final String I_LEVEL = "\uE01C";
    public static final String I_JOB = "\uE01D";
    public static final String I_ARROW = "\uE01E";

    private static final Pattern TEXT = Pattern.compile(">([^<]+)");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Map<Character, Integer> WIDTH = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('.', 2), Map.entry(',', 2), Map.entry(':', 2), Map.entry(';', 2),
            Map.entry('!', 2), Map.entry('i', 2), Map.entry('l', 3), Map.entry('|', 2), Map.entry('\'', 2),
            Map.entry('I', 4), Map.entry('t', 4), Map.entry('f', 5), Map.entry('k', 5), Map.entry('(', 5), Map.entry(')', 5),
            Map.entry('[', 4), Map.entry(']', 4)
    );

    private ScoreboardText() {}

    /** Zeile mit festem Rahmen: Rahmen wird gezeichnet, Cursor zurück, Text darüber. */
    public static String line(String mini) {
        return "<font:" + FONT + ">" + PANEL + BACK + "</font> " + mini;
    }

    /** Zentriert eine Zeile innerhalb der Breite (Leerzeichen davor). */
    public static String center(String mini, int widthPx) {
        int free = widthPx - width(plain(mini));
        int spaces = Math.max(0, free / 2 / 4);
        return " ".repeat(spaces) + mini;
    }

    /** Ein Icon aus dem Resource Pack, mit Abstand dahinter. */
    public static String icon(String glyph) {
        return "<font:" + FONT + ">" + glyph + "</font> ";
    }

    /** Wandelt den sichtbaren Text in Small Caps um. Tags und Icons bleiben unverändert. */
    public static String smallText(String mini) {
        Matcher m = TEXT.matcher(mini);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(mini, last, m.start(1));
            sb.append(SmallCaps.of(m.group(1)));
            last = m.end(1);
        }
        sb.append(mini.substring(last));
        return sb.toString();
    }

    /** Entfernt MiniMessage-Tags und gibt den reinen Text zurück. */
    public static String plain(String mini) {
        return TAGS.matcher(mini).replaceAll("");
    }

    public static int width(String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\uE010' && c <= '\uE01F') w += 9;   // Icons sind 9 Pixel breit
            else w += WIDTH.getOrDefault(c, 6);
        }
        return w;
    }

    /** Kürzt eine Zeile, wenn sie breiter als maxPx ist. Gekürzte Zeilen verlieren ihre Farben. */
    public static String fit(String mini, int maxPx) {
        if (width(plain(mini)) <= maxPx) return mini;
        String p = plain(mini);
        int ell = width("…");
        while (!p.isEmpty() && width(p) + ell > maxPx) p = p.substring(0, p.length() - 1);
        return p + "…";
    }

    /** Geld mit Punkt als Tausendertrenner, ab einer Million verkürzt, damit die Breite gleich bleibt. */
    public static String money(long v) {
        long a = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (a >= 1_000_000_000L) return sign + String.format(Locale.GERMANY, "%.2f Mrd$", a / 1_000_000_000.0);
        if (a >= 1_000_000L) return sign + String.format(Locale.GERMANY, "%.2f Mio$", a / 1_000_000.0);
        return sign + String.format(Locale.GERMANY, "%,d$", a);
    }

    /** Zahl mit Tausendertrenner, ohne Währung. */
    public static String number(long v) {
        return String.format(Locale.GERMANY, "%,d", v);
    }

    /** mm:ss */
    public static String clock(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
