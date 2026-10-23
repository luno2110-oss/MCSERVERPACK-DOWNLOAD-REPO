package de.kuby.kubyscoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScoreboardListener implements Listener {

    private final JavaPlugin addon;
    private final ScoreboardService service;

    public ScoreboardListener(JavaPlugin addon, ScoreboardService service) {
        this.addon = addon;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        service.join(e.getPlayer());
        sendPack(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        service.quit(e.getPlayer().getUniqueId());
    }

    private static final java.util.UUID PACK_ID = java.util.UUID.nameUUIDFromBytes(
            "kuby-scoreboard-pack".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /** Schickt das Scoreboard-Pack, wenn in der config eine URL steht. */
    private void sendPack(Player p) {
        String url = addon.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        String sha1 = addon.getConfig().getString("resource-pack.sha1", "");
        byte[] hash = null;
        if (sha1 != null && sha1.length() == 40) {
            try {
                hash = java.util.HexFormat.of().parseHex(sha1);
            } catch (IllegalArgumentException ignored) {
                hash = null;
            }
        }
        // Zusätzlich statt ersetzend, damit andere Packs (z. B. KubyUI) erhalten bleiben.
        p.addResourcePack(PACK_ID, url, hash, null, false);
    }
}
