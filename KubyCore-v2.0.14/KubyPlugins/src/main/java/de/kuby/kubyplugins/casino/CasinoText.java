package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.SmallCaps;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Texte im Casino-Stil der Referenz: "CASINO ▶ ...", Zahlen mit Punkt, Tooltip-Zeilen mit "▶". */
public final class CasinoText {

    public static final String PREFIX = "<#F2B829><b>CASINO</b></#F2B829> <dark_gray>▶</dark_gray> ";

    private CasinoText() {
    }

    public static String num(long v) {
        return CasinoScreens.num(v);
    }

    /** Chatzeile mit CASINO-Präfix (MiniMessage). */
    public static void send(Player p, String mini) {
        p.sendMessage(Msg.parse(PREFIX + "<gray>" + mini));
    }

    public static void error(Player p, String text) {
        p.sendMessage(Msg.parse(PREFIX + "<#FF5A5A>" + Msg.escape(text)));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
    }

    /** Tooltip-Titel: Small Caps, fett, Ziffern bleiben normal ("+100 CHIPS"). */
    public static String title(String color, String text) {
        return "<" + color + "><b>" + SmallCaps.of(text) + "</b></" + color + ">";
    }

    /** Tooltip-Zeile mit grauem Dreieck davor. */
    public static String line(String mini) {
        return "<dark_gray>▶</dark_gray> <gray>" + mini;
    }

    /** "▶ Klicke — kaufen" */
    public static String click(String what) {
        return line("<yellow>Klicke</yellow> <dark_gray>—</dark_gray> " + what);
    }

    public static String chips(long v) {
        return num(v) + " Chips";
    }

    public static String coins(long v) {
        return num(v) + " Coins";
    }
}
