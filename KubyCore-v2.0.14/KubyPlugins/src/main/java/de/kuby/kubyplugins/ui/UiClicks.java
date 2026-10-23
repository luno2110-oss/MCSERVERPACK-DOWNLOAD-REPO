package de.kuby.kubyplugins.ui;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Klicks auf Text in Canvas-Menüs: jeder klickbare Bereich bekommt ein Kennwort ("custom" Klick, kuby:ui).
 * Pro Spieler gilt nur das zuletzt geöffnete Menü; Klicks auf ein veraltetes Menü und schnelle Doppelklicks
 * (unter 150 ms) werden ignoriert.
 */
public final class UiClicks implements Listener {

    public static final Key KEY = Key.key("kuby", "ui");
    private static final long LIFETIME_MS = 30 * 60 * 1000L;
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicLong COUNTER = new AtomicLong();
    private static Plugin plugin;

    /** Klickbare Bereiche eines geöffneten Menüs. */
    public static final class Session {
        private final String id = Long.toString(COUNTER.incrementAndGet(), 36);
        private final Map<String, Consumer<Player>> actions = new ConcurrentHashMap<>();
        private final long created = System.currentTimeMillis();
        private long lastClick;
        private int next;

        public String register(Consumer<Player> action) {
            String token = id + "." + Integer.toString(next++, 36);
            actions.put(token, action);
            return token;
        }
    }

    public UiClicks(Plugin owner) {
        plugin = owner;
    }

    /** Neues Menü für den Spieler: die Klicks des vorigen gelten nicht mehr. */
    public static Session newSession(Player player) {
        Session s = new Session();
        SESSIONS.put(player.getUniqueId(), s);
        return s;
    }

    public static ClickEvent click(String token) {
        return ClickEvent.custom(KEY, BinaryTagHolder.binaryTagHolder("\"" + token + "\""));
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!KEY.equals(event.getIdentifier())) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection conn)) return;
        BinaryTagHolder tag = event.getTag();
        if (tag == null) return;
        String token = tag.string();
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            token = token.substring(1, token.length() - 1);
        }
        Player player = conn.getPlayer();
        Session s = SESSIONS.get(player.getUniqueId());
        if (s == null || System.currentTimeMillis() - s.created > LIFETIME_MS) return;
        Consumer<Player> action = s.actions.get(token);
        if (action == null) return;
        long now = System.currentTimeMillis();
        synchronized (s) {
            if (now - s.lastClick < 150) return;
            s.lastClick = now;
        }
        if (Bukkit.isPrimaryThread()) {
            run(player, action);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> run(player, action));
        }
    }

    private static void run(Player player, Consumer<Player> action) {
        if (!player.isOnline()) return;
        try {
            action.accept(player);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Fehler bei Menü-Klick von " + player.getName() + ": " + e);
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }
}
