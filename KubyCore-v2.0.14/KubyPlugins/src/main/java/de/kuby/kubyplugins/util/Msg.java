package de.kuby.kubyplugins.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

    /** Item-Texte ohne den Standard-Kursivstil von Minecraft (im Sprach-Kontext übersetzt, siehe I18n). */
    public static Component item(String mini) {
        return MM.deserialize("<!i>" + I18n.ctx(mini));
    }

    /** Meldung mit Präfix; englisch, wenn der Empfänger Englisch eingestellt hat. */
    public static void send(CommandSender to, String mini) {
        to.sendMessage(MM.deserialize(prefix + (to instanceof Player p ? I18n.forPlayer(p, mini) : mini)));
    }

    /** Meldung ohne Präfix, übersetzt für den Empfänger. */
    public static void raw(CommandSender to, String mini) {
        to.sendMessage(MM.deserialize(to instanceof Player p ? I18n.forPlayer(p, mini) : mini));
    }

    /** Fertige Komponente, übersetzt für den Empfänger. */
    public static void send(CommandSender to, Component c) {
        to.sendMessage(to instanceof Player p && I18n.english(p) ? I18n.tr(c) : c);
    }

    /** Aktionsleiste, übersetzt für den Empfänger. */
    public static void actionBar(Player p, String mini) {
        p.sendActionBar(MM.deserialize(I18n.forPlayer(p, mini)));
    }

    /** Entschärft Spielereingaben, damit niemand MiniMessage-Tags einschleusen kann. */
    public static String escape(String text) {
        return MM.escapeTags(text);
    }

    public static String coins(long amount) {
        return NF.format(amount);
    }
}
