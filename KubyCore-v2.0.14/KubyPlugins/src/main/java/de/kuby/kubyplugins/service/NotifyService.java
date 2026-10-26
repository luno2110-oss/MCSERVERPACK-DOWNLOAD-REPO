package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Predicate;

/**
 * Mitteilungen an alle, die sie sehen wollen: statt Bukkit.broadcast geht jede Meldung nur an Spieler, deren Schalter
 * (z. B. "notify.jackpot" oder "chat.quiz") in /settings an ist. Die Konsole bekommt sie immer.
 *
 * <p>Dazu die Tipps: alle paar Minuten ein Hinweis aus tips.messages (Englisch: tips.messages-en) an alle mit
 * "Broadcasts & Tipps".
 */
public final class NotifyService {

    private final KubyPlugins plugin;
    private int tip;

    public NotifyService(KubyPlugins plugin) {
        this.plugin = plugin;
        long every = Math.max(1, plugin.getConfig().getLong("tips.interval-minutes", 6)) * 60L * 20L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tip, every, every);
    }

    /** An alle Spieler mit eingeschaltetem Schalter key. */
    public void send(String key, Component msg) {
        send(key, msg, p -> true);
    }

    /** Text (MiniMessage) an alle mit eingeschaltetem Schalter key, je Empfänger in seiner Sprache. */
    public void send(String key, String mini) {
        send(key, mini, p -> true);
    }

    public void send(String key, String mini, Predicate<Player> filter) {
        Bukkit.getConsoleSender().sendMessage(Msg.parse(mini));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (filter.test(p) && wants(p, key)) Msg.raw(p, mini);
        }
    }

    /** Nur an Spieler, die zusätzlich filter erfüllen (z. B. nur im Survival). */
    public void send(String key, Component msg, Predicate<Player> filter) {
        Bukkit.getConsoleSender().sendMessage(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (filter.test(p) && wants(p, key)) Msg.send(p, msg);
        }
    }

    public boolean wants(Player p, String key) {
        return plugin.playerSettings() == null || plugin.playerSettings().get(p.getUniqueId(), key);
    }

    private void tip() {
        if (!plugin.getConfig().getBoolean("tips.enabled", true)) return;
        List<String> de = plugin.getConfig().getStringList("tips.messages");
        List<String> en = plugin.getConfig().getStringList("tips.messages-en");
        if (de.isEmpty()) return;
        int i = tip++ % de.size();
        Component prefix = Msg.parse(plugin.getConfig().getString("tips.prefix", "<#FCD45D><b>TIPP</b> <dark_gray>▶ "));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!wants(p, "notify.broadcast")) continue;
            boolean english = plugin.playerSettings().english(p.getUniqueId()) && i < en.size();
            p.sendMessage(prefix.append(Msg.parse("<gray>" + (english ? en.get(i) : de.get(i)))));
        }
    }
}
