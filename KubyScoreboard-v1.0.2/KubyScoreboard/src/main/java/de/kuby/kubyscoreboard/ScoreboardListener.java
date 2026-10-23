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

    /** Schickt das Scoreboard-Pack, wenn in der config eine URL steht. */
    private void sendPack(Player p) {
        String url = addon.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        String sha1 = addon.getConfig().getString("resource-pack.sha1", "");
        if (sha1 == null || sha1.isBlank()) p.setResourcePack(url);
        else p.setResourcePack(url, sha1);
    }
}
