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
 * <p>
 * ui.pack-url "auto" (und die alte Adresse von „latest“): das Pack genau dieses Builds aus dem Release „ui-packs“.
 * Die SHA-1 schreibt CI beim Bauen ins Jar (ui-pack.properties). So passen die Zeichen im Pack immer zu KitData,
 * auch wenn „latest“ schon neuer ist als das Plugin auf dem Server.
 */
public final class UiPackService implements Listener {

    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("kuby-ui-pack".getBytes(StandardCharsets.UTF_8));

    private static final String RELEASES = "https://github.com/luno2110-oss/MCSERVERPACK-DOWNLOAD-REPO/releases/download/";

    private final KubyPlugins plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private volatile byte[] hash;
    /** SHA-1 des Packs, mit dem dieses Jar gebaut wurde (null: lokal gebaut). */
    private final String buildSha;

    public UiPackService(KubyPlugins plugin) {
        this.plugin = plugin;
        String sha = null;
        try (java.io.InputStream in = plugin.getResource("ui-pack.properties")) {
            if (in != null) {
                java.util.Properties pr = new java.util.Properties();
                pr.load(in);
                String v = pr.getProperty("sha1", "").trim();
                if (v.matches("[0-9a-f]{40}")) sha = v;
            }
        } catch (java.io.IOException ignored) {
        }
        this.buildSha = sha;
        if (sha != null) hash = java.util.HexFormat.of().parseHex(sha);
    }

    /** Eigene Adresse oder die des passenden Packs („auto“, leer oder die alte Standard-Adresse von „latest“). */
    private boolean auto(String configured) {
        return configured == null || configured.isBlank() || configured.equalsIgnoreCase("auto")
                || configured.toLowerCase(java.util.Locale.ROOT).endsWith("/releases/download/latest/kubyui-pack.zip");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshHash, 20L, 20L * 60 * 30);
    }

    private String url() {
        String configured = plugin.getConfig().getString("ui.pack-url", "auto");
        if (buildSha != null && auto(configured)) return RELEASES + "ui-packs/KubyUI-" + buildSha.substring(0, 12) + ".zip";
        if (configured == null || configured.equalsIgnoreCase("auto")) return RELEASES + "latest/KubyUI-Pack.zip";
        return configured;
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
            byte[] got = MessageDigest.getInstance("SHA-1").digest(res.body());
            if (buildSha != null && auto(plugin.getConfig().getString("ui.pack-url", "auto"))
                    && !java.util.Arrays.equals(got, hash)) {
                plugin.getLogger().warning("UI-Pack unter " + url + " passt nicht zu diesem Build (SHA-1 abweichend).");
            }
            hash = got;
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

    /**
     * Eigene Menüs (Dialoge statt Truhen) für diesen Spieler? Java: wenn das Pack geladen ist (ui.custom-textures: auto,
     * on, off). Bedrock: ja, die Menüs kommen dort als native Formulare (bedrock.formulare).
     */
    public boolean active(Player p) {
        if (de.kuby.kubyplugins.bedrock.Bedrock.forms(p)) return true;
        return javaPack(p);
    }

    /** Java-Grafik-Pack wirklich da (für HUD, Scoreboard-Logo, Kino und alles, was die Java-Schriften direkt zeigt)? */
    public boolean javaPack(Player p) {
        if (de.kuby.kubyplugins.bedrock.Bedrock.is(p)) return false;
        String mode = plugin.getConfig().getString("ui.custom-textures", "auto");
        if ("on".equalsIgnoreCase(mode)) return true;
        if ("off".equalsIgnoreCase(mode)) return false;
        return loaded.contains(p.getUniqueId());
    }
}
