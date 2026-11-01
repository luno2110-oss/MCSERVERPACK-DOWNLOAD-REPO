package de.kuby.kubysurvival.quests;

import io.papermc.paper.event.block.PlayerShearBlockEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ziele der Event-Kapitel, die QuestService nicht kennt: Sammel-Items aufheben, Kürbisse schnitzen, Blöcke setzen,
 * Items aufheben, Tränke brauen, Zombie-Dorfbewohner heilen, Überfälle gewinnen, mit Schneebällen treffen, Schneemänner
 * und Golems bauen, werfen und Feuerwerk zünden, Tiere zähmen. Dazu: Feuerwerk des Events schadet niemandem.
 */
public final class EventListener implements Listener {

    private final EventService events;
    /** Braustand → wer zuletzt daran gearbeitet hat (BrewEvent kennt keinen Spieler). */
    private final Map<Location, UUID> brewers = new HashMap<>();

    public EventListener(EventService events) {
        this.events = events;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent e) {
        Item item = e.getItem();
        if (events.isCandy(item)) {
            e.setCancelled(true);
            if (e.getEntity() instanceof Player p) events.pickCandy(p, item);
            return;
        }
        if (e.isCancelled() || !(e.getEntity() instanceof Player p) || item.getThrower() != null) return;
        ItemStack st = item.getItemStack();
        events.progress(p, "pickup", st.getType(), st.getAmount(), item.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent e) {
        if (events.isCandy(e.getItem())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent e) {
        if (events.isCandy(e.getEntity()) || events.isCandy(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearBlockEvent e) {
        if (e.getBlock().getType() == Material.PUMPKIN) events.progress(e.getPlayer(), "carve", null, 1, e.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        events.progress(e.getPlayer(), "place", e.getBlockPlaced().getType(), 1, e.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType() != InventoryType.BREWING || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory().getHolder() instanceof BrewingStand bs) brewers.put(bs.getLocation(), p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        UUID id = brewers.get(e.getBlock().getLocation());
        Player p = id == null ? null : org.bukkit.Bukkit.getPlayer(id);
        if (p == null) return;
        int n = 0;
        for (ItemStack r : e.getResults()) if (r != null && !r.getType().isAir()) n++;
        events.progress(p, "brew", null, n, e.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCure(EntityTransformEvent e) {
        if (e.getTransformReason() != EntityTransformEvent.TransformReason.CURED || !(e.getEntity() instanceof ZombieVillager zv)) return;
        OfflinePlayer op = zv.getConversionPlayer();
        Player p = op == null ? null : op.getPlayer();
        if (p != null) events.progress(p, "cure", null, 1, zv.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRaid(RaidFinishEvent e) {
        for (Player p : e.getWinners()) events.progress(p, "raid", null, 1, p.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSnowball(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball sb) || !(sb.getShooter() instanceof Player p)) return;
        Entity hit = e.getHitEntity();
        if (hit instanceof LivingEntity && hit != p) events.progress(p, "snowball", hit, 1, hit.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuild(CreatureSpawnEvent e) {
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r != CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN && r != CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM) return;
        Player best = null;
        double bd = 8 * 8;
        for (Player p : e.getLocation().getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best != null) events.progress(best, "build", e.getEntity(), 1, e.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrow(ProjectileLaunchEvent e) {
        Projectile pr = e.getEntity();
        if (!(pr.getShooter() instanceof Player p)) return;
        Material m;
        if (pr instanceof Firework) {
            if (p.isGliding()) return;
            m = Material.FIREWORK_ROCKET;
        } else if (pr instanceof ThrowableProjectile tp) {
            m = tp.getItem().getType();
        } else if (pr instanceof Trident) {
            m = Material.TRIDENT;
        } else {
            return;
        }
        events.progress(p, "throw", m, 1, p.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p) events.progress(p, "tame", e.getEntity(), 1, e.getEntity().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework fw && fw.getPersistentDataContainer().has(events.fireworkKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }
}
