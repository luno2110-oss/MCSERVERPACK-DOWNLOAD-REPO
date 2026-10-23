package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/** Erweitert die Worldborder, wenn am Rand gebaut wird – auch bei Schematics und WorldEdit. */
public final class WorldBorderListener implements Listener {

    private final KubyPlugins plugin;

    public WorldBorderListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        plugin.worlds().checkPlacement(e.getBlock().getWorld(), e.getBlock().getX(), e.getBlock().getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase(Locale.ROOT);
        boolean buildCmd = m.startsWith("//") || m.startsWith("/schem") || m.startsWith("/schematic")
                || m.startsWith("/we ") || m.startsWith("/worldedit");
        if (!buildCmd) return;
        World w = e.getPlayer().getWorld();
        // Zwei Prüfungen: direkt nach dem Befehl und nach Verzögerung (asynchrone Bearbeitung von FAWE)
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.worlds().scanAfterBuild(w), 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.worlds().scanAfterBuild(w), 60L);
    }
}
