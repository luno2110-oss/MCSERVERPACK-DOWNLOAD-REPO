package de.kuby.kubyplugins.util;

import java.util.Locale;

/**
 * Wandelt Buchstaben in Small Caps um (wie piliapp.com/cool-text/small-caps).
 * Nur auf reinen Text anwenden, niemals auf MiniMessage-Tags wie <gold>.
 */
public final class SmallCaps {

    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {}

    public static String of(String text) {
        text = I18n.ctx(text);
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            int idx = FROM.indexOf(c);
            sb.append(idx >= 0 ? TO.charAt(idx) : c);
        }
        return sb.toString();
    }
}
