package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Schutz für Regionen: kein Abbauen, Platzieren, Benutzen oder Explodieren. Admins und Besitzer sind ausgenommen. */
public final class RegionListener implements Listener {

    private final KubyPlugins plugin;

    public RegionListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private void deny(Player p) {
        p.sendActionBar(de.kuby.kubyplugins.util.Msg.parse("<red>Dieser Bereich ist geschützt."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.regions().protects(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.regions().protects(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (plugin.regions().protects(e.getPlayer(), e.getClickedBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.regions().protectedAt(b.getLocation()));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.regions().protectedAt(b.getLocation()));
    }
}
