package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Gibt jede volle Stunde jedem Online-Spieler einen Universal-Schlüssel (keyall_key).
 * Die Vergabe läuft über einen Phoenix-Befehl aus der config.yml (keyall.command).
 */
public final class KeyAllTimer {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final KubyPlugins plugin;

    public KeyAllTimer(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime next = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        long ticks = Math.max(20L, Duration.between(now, next).toSeconds() * 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::giveAll, ticks, 72_000L);
    }

    /** Sekunden bis zur nächsten vollen Stunde (Europe/Berlin), genau wie start() plant. */
    public static int secondsUntilNext() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime next = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        return (int) Duration.between(now, next).toSeconds();
    }

    public void giveAll() {
        String template = plugin.getConfig().getString("keyall.command", "");
        if (template.isBlank()) {
            plugin.getLogger().warning("keyall.command ist nicht gesetzt, Stunden-Keyall übersprungen.");
            return;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                    template.replace("{player}", p.getName()).replace("%player%", p.getName()));
            // Eigene Schlüssel melden sich selbst (+1 Keyall-Schlüssel), fremde Kisten-Plugins nicht
            if (!template.trim().startsWith("keys ")) {
                Msg.send(p, "<aqua>Stunden-Keyall: <white>1 Schlüssel erhalten!");
                Sfx.play(plugin, p, Sfx.Kind.PING);
            }
        }
    }
}
