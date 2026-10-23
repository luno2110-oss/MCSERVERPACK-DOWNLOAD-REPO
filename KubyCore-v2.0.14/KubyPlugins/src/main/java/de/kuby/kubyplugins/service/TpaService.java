package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Teleport-Anfragen: /tpa Spieler, dann /tpaccept oder /tpadeny. Der Anfragende springt zum Ziel. */
public final class TpaService {

    private static final long TIMEOUT_MS = 60_000L;

    private record Req(UUID from, long at) {}

    private final KubyPlugins plugin;
    private final Map<UUID, Req> requests = new HashMap<>();

    public TpaService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String request(Player from, Player to) {
        if (from.equals(to)) return "Du kannst dich nicht zu dir selbst teleportieren.";
        if (busy(from) || busy(to)) return "Einer der beiden ist gerade beschäftigt.";
        requests.put(to.getUniqueId(), new Req(from.getUniqueId(), System.currentTimeMillis()));
        Msg.send(to, "<gold>" + from.getName() + " <gray>möchte zu dir teleportieren. <white>/tpaccept <gray>oder <white>/tpadeny");
        Sfx.play(plugin, to, Sfx.Kind.PING);
        Msg.send(from, "<gray>Anfrage an <white>" + to.getName() + " <gray>gesendet.");
        return null;
    }

    public synchronized String accept(Player to) {
        Req r = requests.remove(to.getUniqueId());
        if (r == null) return "Keine offene Anfrage.";
        if (System.currentTimeMillis() - r.at() > TIMEOUT_MS) return "Die Anfrage ist abgelaufen.";
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) return "Der Anfragende ist offline.";
        if (busy(from) || busy(to)) return "Einer der beiden ist gerade beschäftigt.";
        Msg.send(to, "<green>Anfrage angenommen.");
        Teleports.delayed(plugin, from, to.getLocation(), to.getName());
        return null;
    }

    public synchronized String deny(Player to) {
        Req r = requests.remove(to.getUniqueId());
        if (r == null) return "Keine offene Anfrage.";
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) Msg.send(from, "<red>" + to.getName() + " hat deine Anfrage abgelehnt.");
        Msg.send(to, "<gray>Anfrage abgelehnt.");
        return null;
    }

    private boolean busy(Player p) {
        return plugin.trades().inTrade(p.getUniqueId()) || plugin.pvp().isDueling(p.getUniqueId())
                || plugin.pvp().inCombat(p.getUniqueId());
    }
}
