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
    /** Vorige Sitzung je Spieler: bleibt 3 Sekunden gültig, damit ein Klick im Moment einer Aktualisierung nicht verloren geht. */
    private static final Map<UUID, Session> PREVIOUS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PREVIOUS_UNTIL = new ConcurrentHashMap<>();
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
        Session old = SESSIONS.put(player.getUniqueId(), s);
        if (old != null) {
            PREVIOUS.put(player.getUniqueId(), old);
            PREVIOUS_UNTIL.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
        }
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
        if (action == null) {
            // Klick auf das Menü kurz vor einer Aktualisierung (z. B. Börse): noch gültig
            Session prev = PREVIOUS.get(player.getUniqueId());
            Long until = PREVIOUS_UNTIL.get(player.getUniqueId());
            if (prev == null || until == null || System.currentTimeMillis() > until) return;
            action = prev.actions.get(token);
            if (action == null) return;
        }
        long now = System.currentTimeMillis();
        synchronized (s) {
            if (now - s.lastClick < 150) return;
            s.lastClick = now;
        }
        Consumer<Player> act = action;
        if (Bukkit.isPrimaryThread()) {
            run(player, act);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> run(player, act));
        }
    }

    /**
     * Nur für den Belastungstest (/kubytest klicks): einen zufälligen klickbaren Bereich des zuletzt geöffneten Menüs
     * auslösen, auf demselben Weg wie ein echter Klick. false = kein Menü offen.
     */
    public static boolean randomClick(Player player) {
        Session s = SESSIONS.get(player.getUniqueId());
        if (s == null || s.actions.isEmpty() || System.currentTimeMillis() - s.created > LIFETIME_MS) return false;
        java.util.List<Consumer<Player>> l = new java.util.ArrayList<>(s.actions.values());
        run(player, l.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(l.size())));
        return true;
    }

    private static void run(Player player, Consumer<Player> action) {
        if (!player.isOnline()) return;
        boolean sounded = false;
        UiDialog.beginClick(player);
        try {
            de.kuby.kubyplugins.util.I18n.as(player, () -> action.accept(player));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Fehler bei Menü-Klick von " + player.getName() + ": " + e);
            e.printStackTrace();
        } finally {
            sounded = UiDialog.endClick(player);
        }
        // Jedes Menü klingt beim Klicken: hat die Aktion keinen eigenen Ton gespielt, ein leiser Klick
        if (!sounded && plugin instanceof de.kuby.kubyplugins.KubyPlugins core) {
            de.kuby.kubyplugins.util.Sfx.play(core, player, de.kuby.kubyplugins.util.Sfx.Kind.CLICK);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        SESSIONS.remove(id);
        PREVIOUS.remove(id);
        PREVIOUS_UNTIL.remove(id);
        UiDialog.forget(id);
    }
}
