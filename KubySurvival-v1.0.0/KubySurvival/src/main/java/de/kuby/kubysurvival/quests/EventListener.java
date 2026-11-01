package de.kuby.kubysurvival.quests;

import io.papermc.paper.event.block.PlayerShearBlockEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ziele der Event-Kapitel, die QuestService nicht kennt: Candy Corn aufheben, Kürbisse schnitzen, Blöcke setzen,
 * Items aufheben, Tränke brauen, Zombie-Dorfbewohner heilen und Überfälle gewinnen.
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
}
