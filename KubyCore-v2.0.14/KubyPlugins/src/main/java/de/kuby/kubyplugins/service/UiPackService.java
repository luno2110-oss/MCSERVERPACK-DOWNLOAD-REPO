package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schickt das Grafik-Pack "KubyUI" (Schriftart kuby:ui) beim Join zusätzlich zu anderen Packs und merkt sich,
 * bei wem es geladen ist. Menüs nutzen die eigenen Grafiken nur dann, sonst die normale Ansicht.
 * Die SHA-1-Prüfsumme wird beim Start und alle 30 Minuten aus der URL berechnet, damit Spieler das Pack
 * zwischenspeichern und Updates erkennen.
 */
public final class UiPackService implements Listener {

    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("kuby-ui-pack".getBytes(StandardCharsets.UTF_8));

    private final KubyPlugins plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private volatile byte[] hash;

    public UiPackService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshHash, 20L, 20L * 60 * 30);
    }

    private String url() {
        return plugin.getConfig().getString("ui.pack-url", "");
    }

    /** Lädt das Pack herunter und berechnet die SHA-1 (läuft im Hintergrund). */
    private void refreshHash() {
        String url = url();
        if (url == null || url.isBlank()) return;
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<byte[]> res = client.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                plugin.getLogger().warning("UI-Pack nicht erreichbar (HTTP " + res.statusCode() + "): " + url);
                return;
            }
            hash = MessageDigest.getInstance("SHA-1").digest(res.body());
        } catch (Exception e) {
            plugin.getLogger().warning("UI-Pack konnte nicht geprüft werden: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Etwas später als andere Packs, damit es zusätzlich geladen wird.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, 60L);
    }

    public void send(Player p) {
        String url = url();
        if (url == null || url.isBlank()) return;
        p.addResourcePack(PACK_ID, url, hash, plugin.getConfig().getString("ui.pack-prompt", null),
                plugin.getConfig().getBoolean("ui.pack-force", false));
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (!PACK_ID.equals(e.getID())) return;
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(e.getPlayer().getUniqueId());
            case ACCEPTED, DOWNLOADED -> {
            }
            default -> loaded.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }

    /** Eigene Grafiken für diesen Spieler? ui.custom-textures: auto (wenn Pack geladen), on, off. */
    public boolean active(Player p) {
        String mode = plugin.getConfig().getString("ui.custom-textures", "auto");
        if ("on".equalsIgnoreCase(mode)) return true;
        if ("off".equalsIgnoreCase(mode)) return false;
        return loaded.contains(p.getUniqueId());
    }
}
