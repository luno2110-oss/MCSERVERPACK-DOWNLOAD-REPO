package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.block.Block;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/** XP für Erze, Mobs und Angeln. Beträge stehen unter xp-sources in der config. Booster gilt auch hier. */
public final class XpSourceListener implements Listener {

    private final KubyPlugins plugin;

    public XpSourceListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** Von Spielern gesetzte Blöcke. Wer sie selbst abbaut, bekommt keine XP (gegen Farmen). */
    private final java.util.Set<String> placed = new java.util.HashSet<>();
    private static final int PLACED_CAP = 200_000;

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (placed.size() < PLACED_CAP) placed.add(key(e.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlock(BlockBreakEvent e) {
        if (placed.remove(key(e.getBlock()))) return;
        long v = plugin.getConfig().getLong("xp-sources.blocks." + e.getBlock().getType().name(), 0);
        if (v > 0) add(e.getPlayer(), v);
    }

    @EventHandler
    public void onMob(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        long v = plugin.getConfig().getLong("xp-sources.mobs." + e.getEntityType().name(), 0);
        if (v > 0) add(e.getEntity().getKiller(), v);
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        long v = plugin.getConfig().getLong("xp-sources.fish", 0);
        if (v > 0) add(e.getPlayer(), v);
    }

    private void add(Player p, long base) {
        long boosted = Math.round(base * plugin.booster().multiplier());
        plugin.stats().get(p.getUniqueId()).xp += boosted;
    }
}
