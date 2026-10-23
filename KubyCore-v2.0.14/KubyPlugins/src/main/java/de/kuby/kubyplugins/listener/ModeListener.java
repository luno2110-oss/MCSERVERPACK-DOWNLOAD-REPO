package de.kuby.kubyplugins.listener;

import org.bukkit.event.player.PlayerChangedWorldEvent;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/** Lobby-Regeln: Kompass und Farbstoff bedienen, sonst nichts verändern. Admins sind ausgenommen. */
public final class ModeListener implements Listener {

    private final KubyPlugins plugin;

    public ModeListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    private boolean lobby(Player p) {
        return plugin.modes().isHub(p.getWorld()) && !p.hasPermission("kuby.admin") && !plugin.modes().hasActivity(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.modes().onJoin(e.getPlayer());
        plugin.markers().applyVisibility(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.visibility().forget(e.getPlayer());
        plugin.edit().forget(e.getPlayer());
        plugin.modes().onQuit(e.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!plugin.modes().isHub(p.getWorld())) return;
        ItemStack hand = e.getItem();
        boolean lobbyItem = hand != null && (hand.getType() == Material.COMPASS || hand.getType().name().endsWith("_DYE"));
        if (!lobbyItem && !lobby(p)) return;
        e.setCancelled(true);
        if (hand == null) return;
        Action a = e.getAction();
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;

        if (hand.getType() == Material.COMPASS && (left || right)) {
            new de.kuby.kubyplugins.gui.ModeSelectMenu(plugin).open(p);
        } else if (hand.getType().name().endsWith("_DYE") && (left || right)) {
            plugin.visibility().cycle(p);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && lobby(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (lobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && lobby(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (lobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (lobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.modes().isHub(p.getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && plugin.modes().isHub(p.getWorld())) e.setCancelled(true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.markers().applyVisibility(e.getPlayer());
        if (!plugin.modes().isHub(e.getPlayer().getWorld())) plugin.edit().forget(e.getPlayer());
    }
}
