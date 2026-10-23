package de.kp.jumprun.util;

import de.kp.jumprun.KPJumpRun;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/**
 * Hilfsmethoden für Texte: Small Caps für Item-Titel und Nachrichten mit Prefix.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final String FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String TO =
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍ"
          + "ɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private Text() {
    }

    /** Wandelt Kleinbuchstaben in Small Caps um, alle anderen Zeichen bleiben unverändert. */
    public static String smallCaps(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            int index = FROM.indexOf(Character.toLowerCase(c));
            sb.append(index >= 0 ? TO.charAt(index) : c);
        }
        return sb.toString();
    }

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /** Item-Titel: immer in Small Caps, ohne kursive Standardschrift. */
    public static Component item(String title) {
        return MM.deserialize("<!italic><white>" + smallCaps(title) + "</white>");
    }

    /** Sendet eine Nachricht mit dem konfigurierten Prefix. */
    public static void send(CommandSender to, String miniMessage) {
        String prefix = KPJumpRun.get().getConfig().getString("messages-prefix", "");
        to.sendMessage(MM.deserialize(prefix + miniMessage));
    }
}
