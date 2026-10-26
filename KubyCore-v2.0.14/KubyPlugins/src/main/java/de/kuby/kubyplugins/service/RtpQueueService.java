package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Duo-RTP: Zwei Wartende werden zusammen an einen Zufallsort gesetzt und kämpfen gegeneinander. */
public final class RtpQueueService {

    private static final long QUEUE_TIMEOUT_MS = 120_000L;
    private static final long PAIR_TIMEOUT_TICKS = 20L * 60 * 10;

    private final KubyPlugins plugin;
    private final Deque<UUID> queue = new ArrayDeque<>();
    private final Map<UUID, Long> queuedAt = new HashMap<>();
    private final Map<UUID, UUID> pairs = new HashMap<>();

    public RtpQueueService(KubyPlugins plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanup, 600L, 600L);
    }

    public synchronized boolean inQueue(UUID id) {
        return queuedAt.containsKey(id);
    }

    public synchronized boolean inPair(UUID a, UUID b) {
        return b.equals(pairs.get(a));
    }

    /** Gibt null zurück, wenn der Spieler in der Warteschlange ist. Sonst eine Fehlermeldung. */
    public synchronized String join(Player p) {
        UUID id = p.getUniqueId();
        if (pairs.containsKey(id)) return "Du bist schon in einem Duo-Kampf.";
        if (queuedAt.containsKey(id)) return "Du bist schon in der Warteschlange.";
        if (plugin.pvp().isDueling(id) || plugin.trades().inTrade(id)) return "Du bist gerade beschäftigt.";
        if (plugin.pvp().inCombat(id)) return "Du bist gerade im Kampf.";

        enqueue(id);
        Msg.send(p, "<gray>In der Warteschlange für ein Duo <white>(" + queue.size() + " wartend)");
        if (queue.size() == 1) {
            String mode = plugin.modes().modeOf(id);
            plugin.notifications().send("notify.rtpqueue", "<#7BC4FC><b>RTP-QUEUE</b> <dark_gray>▶ <white>" + p.getName()
                    + " <gray>wartet auf einen Gegner. <click:run_command:'/rtpqueue'><green>[Beitreten]</green></click>",
                    o -> !o.equals(p) && mode.equals(plugin.modes().modeOf(o.getUniqueId())));
        }
        if (queue.size() >= 2) {
            UUID a = queue.poll();
            UUID b = queue.poll();
            queuedAt.remove(a);
            queuedAt.remove(b);
            start(a, b);
        }
        return null;
    }

    public synchronized String leave(UUID id) {
        if (queuedAt.remove(id) == null) return "Du bist nicht in der Warteschlange.";
        queue.remove(id);
        return null;
    }

    /** Beendet das Duo, wenn einer der beiden stirbt oder ausloggt. */
    public synchronized void onDeath(UUID id) {
        endPairOf(id);
    }

    public synchronized void onQuit(UUID id) {
        leave(id);
        endPairOf(id);
    }

    private void enqueue(UUID id) {
        queue.add(id);
        queuedAt.put(id, System.currentTimeMillis());
    }

    private void start(UUID a, UUID b) {
        Player pa = Bukkit.getPlayer(a);
        Player pb = Bukkit.getPlayer(b);
        if (pa == null || pb == null) {
            if (pa != null) enqueue(a);
            if (pb != null) enqueue(b);
            return;
        }
        Location spot = Teleports.randomSafeLocation(plugin);
        if (spot == null) {
            enqueue(a);
            enqueue(b);
            Msg.send(pa, "<red>Kein sicherer Ort gefunden, du bleibst in der Warteschlange.");
            Msg.send(pb, "<red>Kein sicherer Ort gefunden, du bleibst in der Warteschlange.");
            return;
        }

        pairs.put(a, b);
        pairs.put(b, a);
        plugin.pvp().tagCombat(a);
        plugin.pvp().tagCombat(b);
        pa.teleportAsync(spot);
        pb.teleportAsync(spot.clone().add(4, 0, 0));
        Msg.send(pa, "<red>Duo-Kampf gegen <white>" + pb.getName() + "<red>! Viel Glück.");
        Msg.send(pb, "<red>Duo-Kampf gegen <white>" + pa.getName() + "<red>! Viel Glück.");
        Sfx.play(plugin, pa, Sfx.Kind.DUEL_START);
        Sfx.play(plugin, pb, Sfx.Kind.DUEL_START);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                if (b.equals(pairs.get(a))) endPairOf(a);
            }
        }, PAIR_TIMEOUT_TICKS);
    }

    private void endPairOf(UUID id) {
        UUID other = pairs.remove(id);
        if (other == null) return;
        pairs.remove(other);
        Player po = Bukkit.getPlayer(other);
        if (po != null) Msg.send(po, "<gray>Duo-Kampf beendet.");
    }

    private void cleanup() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            List<UUID> expired = new ArrayList<>();
            for (Map.Entry<UUID, Long> e : queuedAt.entrySet()) {
                if (now - e.getValue() > QUEUE_TIMEOUT_MS) expired.add(e.getKey());
            }
            for (UUID id : expired) {
                queuedAt.remove(id);
                queue.remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) Msg.send(p, "<gray>Warteschlange abgelaufen, niemand hat sich gefunden.");
            }
        }
    }
}
