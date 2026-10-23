package de.kuby.kubyplugins.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.text.NumberFormat;
import java.util.Locale;

public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final NumberFormat NF = NumberFormat.getIntegerInstance(Locale.GERMANY);
    private static String prefix = "";

    private Msg() {}

    public static void setPrefix(String value) { prefix = value; }

    public static Component parse(String mini) {
        return MM.deserialize(mini);
    }

    /** Item-Texte ohne den Standard-Kursivstil von Minecraft. */
    public static Component item(String mini) {
        return MM.deserialize("<!i>" + mini);
    }

    public static void send(CommandSender to, String mini) {
        to.sendMessage(MM.deserialize(prefix + mini));
    }

    /** Entschärft Spielereingaben, damit niemand MiniMessage-Tags einschleusen kann. */
    public static String escape(String text) {
        return MM.escapeTags(text);
    }

    public static String coins(long amount) {
        return NF.format(amount);
    }
}
