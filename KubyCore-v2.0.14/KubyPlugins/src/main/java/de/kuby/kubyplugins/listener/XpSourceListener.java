package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * XP für Erze, Mobs und Angeln. Beträge stehen unter xp-sources in der config. Booster gilt auch hier.
 * Gegen Farmen: Von Spielern gesetzte XP-Blöcke werden im Chunk markiert (übersteht Neustarts, wandert mit Kolben mit)
 * und geben beim Abbauen keine XP. Mobs aus Spawnern und Spawn-Eiern geben keine XP.
 */
public final class XpSourceListener implements Listener {

    private static final Set<CreatureSpawnEvent.SpawnReason> FARM_REASONS = Set.of(
            CreatureSpawnEvent.SpawnReason.SPAWNER, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,
            CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER);

    private final KubyPlugins plugin;
    private final NamespacedKey placedKey;

    public XpSourceListener(KubyPlugins plugin) {
        this.plugin = plugin;
        this.placedKey = new NamespacedKey(plugin, "placed_xp_blocks");
    }

    private long xpFor(Block b) {
        return plugin.getConfig().getLong("xp-sources.blocks." + b.getType().name(), 0);
    }

    // ---------- Markierung gesetzter Blöcke im Chunk ----------

    /** Position im Chunk als Zahl: x (4 Bit), z (4 Bit), y (Rest, verschoben um 4096). */
    private static long pack(Block b) {
        return (b.getX() & 15) | ((long) (b.getZ() & 15) << 4) | ((long) (b.getY() + 4096) << 8);
    }

    private boolean isPlaced(Block b) {
        long[] arr = b.getChunk().getPersistentDataContainer().get(placedKey, PersistentDataType.LONG_ARRAY);
        if (arr == null) return false;
        long key = pack(b);
        for (long v : arr) if (v == key) return true;
        return false;
    }

    private void mark(Block b, boolean placed) {
        Chunk c = b.getChunk();
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        long[] arr = pdc.get(placedKey, PersistentDataType.LONG_ARRAY);
        long key = pack(b);
        List<Long> list = new ArrayList<>();
        if (arr != null) for (long v : arr) if (v != key) list.add(v);
        if (placed) list.add(key);
        if (list.isEmpty()) {
            pdc.remove(placedKey);
        } else {
            long[] out = new long[list.size()];
            for (int i = 0; i < out.length; i++) out[i] = list.get(i);
            pdc.set(placedKey, PersistentDataType.LONG_ARRAY, out);
        }
    }

    private boolean antiFarm() {
        return plugin.getConfig().getBoolean("xp-sources.anti-farm", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (antiFarm() && xpFor(e.getBlock()) > 0) mark(e.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlock(BlockBreakEvent e) {
        Block b = e.getBlock();
        long v = xpFor(b);
        if (v <= 0) return;
        if (antiFarm() && isPlaced(b)) {
            mark(b, false);
            return;
        }
        add(e.getPlayer(), v);
    }

    /** Kolben verschieben markierte Blöcke: die Markierung wandert mit. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        move(e.getBlocks(), e.getDirection());
    }

    private void move(List<Block> blocks, BlockFace dir) {
        if (!antiFarm()) return;
        List<Block> marked = new ArrayList<>();
        for (Block b : blocks) if (isPlaced(b)) marked.add(b);
        for (Block b : marked) mark(b, false);
        for (Block b : marked) mark(b.getRelative(dir), true);
    }

    @EventHandler
    public void onMob(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        if (antiFarm() && FARM_REASONS.contains(e.getEntity().getEntitySpawnReason())) return;
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
