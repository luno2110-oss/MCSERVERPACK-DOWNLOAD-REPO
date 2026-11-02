package de.kuby.kubyskyblock.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService;
import de.kuby.kubyskyblock.service.IslandService.Island;
import de.kuby.kubyskyblock.service.LimitService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lag-Grenzen und die Upgrades Wachstum und Spawnrate. Alles nur in der Skyblock-Welt und nur mit einem schnellen
 * Nachschlagen der Insel (Map), ohne Suchen in der Welt.
 */
public final class LimitListener implements Listener {

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public LimitListener(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    private boolean inSkyblock(World w) {
        return w != null && w.getName().equals(core.modes().worldOf(ModeService.SKYBLOCK));
    }

    // ---------- Blöcke ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!inSkyblock(e.getBlock().getWorld())) return;
        LimitService ls = addon.limits();
        if (ls.groupOf(e.getBlockPlaced().getType()) == null) return;
        Island i = addon.islands().islandAt(e.getBlock().getLocation());
        String err = ls.checkPlace(i, e.getBlockPlaced().getType());
        if (err == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last == null || now - last > 2000) {
            lastWarn.put(p.getUniqueId(), now);
            Msg.send(p, "<red>" + err);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent e) {
        if (!inSkyblock(e.getBlock().getWorld()) || addon.limits().groupOf(e.getBlockPlaced().getType()) == null) return;
        addon.limits().changed(addon.islands().islandAt(e.getBlock().getLocation()), e.getBlockPlaced().getType(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!inSkyblock(e.getBlock().getWorld()) || addon.limits().groupOf(e.getBlock().getType()) == null) return;
        addon.limits().changed(addon.islands().islandAt(e.getBlock().getLocation()), e.getBlock().getType(), -1);
    }

    private void exploded(List<Block> blocks) {
        for (Block b : blocks) {
            if (addon.limits().groupOf(b.getType()) != null) addon.limits().changed(addon.islands().islandAt(b.getLocation()), b.getType(), -1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (inSkyblock(e.getLocation().getWorld())) exploded(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        if (inSkyblock(e.getBlock().getWorld())) exploded(e.blockList());
    }

    // ---------- Wesen und Spawnrate ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        SpawnReason r = e.getSpawnReason();
        if (r == SpawnReason.CUSTOM || r == SpawnReason.COMMAND || !inSkyblock(e.getLocation().getWorld())) return;
        LimitService.Kind k = LimitService.kindOf(e.getEntity());
        if (k == null) return;
        IslandService is = addon.islands();
        Island i = is.islandAt(e.getLocation());
        if (i == null) return;
        if (!addon.limits().allowSpawn(i, k)) {
            e.setCancelled(true);
            return;
        }
        // Spawnrate: beim natürlichen Spawnen (und aus echten Spawnern) mit etwas Glück ein zweites Wesen
        if ((r == SpawnReason.NATURAL || r == SpawnReason.SPAWNER) && ThreadLocalRandom.current().nextDouble() < is.spawnChance(i)) {
            EntityType type = e.getEntityType();
            Location l = e.getLocation().clone();
            org.bukkit.Bukkit.getScheduler().runTask(addon, () -> {
                if (!addon.limits().allowSpawn(i, k) || l.getWorld() == null) return;
                Entity extra = l.getWorld().spawnEntity(l, type, SpawnReason.CUSTOM);
                if (extra instanceof org.bukkit.entity.Ageable a && e.getEntity() instanceof org.bukkit.entity.Ageable src && !src.isAdult()) a.setBaby();
            });
        }
    }

    // ---------- Wachstum ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent e) {
        if (!(e.getNewState().getBlockData() instanceof Ageable a) || a.getAge() >= a.getMaximumAge()) return;
        if (!inSkyblock(e.getBlock().getWorld())) return;
        Island i = addon.islands().islandAt(e.getBlock().getLocation());
        double chance = addon.islands().growthChance(i);
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() >= chance) return;
        a.setAge(a.getAge() + 1);
        e.getNewState().setBlockData(a);
    }
}
