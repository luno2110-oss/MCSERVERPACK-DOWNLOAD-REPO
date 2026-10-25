package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Liest über DiscordSRV, ob ein Minecraft-Account mit Discord verknüpft ist.
 * Läuft per Reflection, damit das Plugin auch ohne DiscordSRV startet.
 * Verknüpfen macht der Spieler im Discord mit /linkaccount.
 */
public final class DiscordLink implements org.bukkit.event.Listener {

    private final KubyPlugins plugin;

    public DiscordLink(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** Beim Einloggen: frisch verlinkt? Dann Haken und Home sofort freischalten (Abfrage außerhalb des Haupt-Threads). */
    @org.bukkit.event.EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!available() || plugin.homes().verified(p.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) plugin.refreshTabName(p);
            }, 20L);
            return;
        }
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (discordIdOf(p.getUniqueId()) == null) return;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || plugin.homes().verified(p.getUniqueId())) return;
                plugin.homes().grantVerify(p.getUniqueId());
                plugin.refreshTabName(p);
                Msg.send(p, "<green>Dein Discord ist verlinkt! <#5865F2>✔</#5865F2> <green>steht jetzt hinter deinem Namen, dazu gibt es ein Home mehr.");
            });
        }, 40L);
    }

    public boolean available() {
        return plugin.getServer().getPluginManager().getPlugin("DiscordSRV") != null;
    }

    /** Discord-ID als Text oder null, wenn nicht verknüpft oder DiscordSRV fehlt. */
    public String discordIdOf(UUID id) {
        try {
            Plugin dsrv = plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
            if (dsrv == null) return null;
            Object manager = call(dsrv, "getAccountLinkManager");
            if (manager == null) return null;
            Object result = call(manager, "getDiscordId", id);
            return result == null ? null : String.valueOf(result);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("DiscordSRV-Abfrage fehlgeschlagen: " + ex.getMessage());
            return null;
        }
    }

    /** True, wenn die Regel in der config aus ist oder der Spieler verknüpft ist. */
    public boolean linkedOrDisabled(UUID id, String ruleKey) {
        if (!plugin.getConfig().getBoolean("discord." + ruleKey, false)) return true;
        return discordIdOf(id) != null;
    }

    /** Prüft die Regel für einen Spieler und meldet bei Nein. */
    public boolean allow(Player p, String ruleKey) {
        if (linkedOrDisabled(p.getUniqueId(), ruleKey)) return true;
        Msg.send(p, "<red>Verknüpfe zuerst deinen Discord-Account: <white>/linkaccount <red>im Discord.");
        Sfx.play(plugin, p, Sfx.Kind.ERROR);
        return false;
    }

    /** Ruft eine öffentliche Methode mit passendem Parameter per Name auf. */
    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            boolean fits = true;
            for (int i = 0; i < args.length; i++) {
                if (!m.getParameterTypes()[i].isInstance(args[i])) fits = false;
            }
            if (fits) return m.invoke(target, args);
        }
        return null;
    }
}
