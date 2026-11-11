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

    /** Inventar in der Lobby: für alle gesperrt (auch Admins und Ops), außer im Bearbeitungsmodus oder bei Aktivitäten. */
    private boolean locked(Player p) {
        return plugin.modes().isHub(p.getWorld()) && !plugin.edit().isEditing(p.getUniqueId()) && !plugin.modes().hasActivity(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.modes().onJoin(e.getPlayer());
        plugin.markers().applyVisibility(e.getPlayer());
    }

    /** MONITOR: erst speichern, wenn alle anderen Plugins fertig sind (z. B. Kampf-Logout leert das Inventar,
     *  ein abgebrochenes Duell gibt das eigene Inventar zurück). Sonst wären die Items doppelt. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
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

        // Kompass: Modus-Wahl (Dialog mit Karten je Modus, ohne Pack das Truhen-Menü); Farbstoff: Sichtbarkeit
        if ((left || right) && e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) useLobbyItem(p, hand);
    }

    /** Kurze Sperre gegen doppeltes Auslösen (z. B. Klick auf Block und Luft im selben Moment). */
    private final java.util.Map<java.util.UUID, Long> lastUse = new java.util.HashMap<>();

    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastUse.put(p.getUniqueId(), now);
        return last != null && now - last < 250;
    }

    private static boolean compass(ItemStack it) {
        return it != null && it.getType() == Material.COMPASS;
    }

    private static boolean dye(ItemStack it) {
        return it != null && it.getType().name().endsWith("_DYE");
    }

    /** Kompass: Modus-Wahl öffnen. Farbstoff: Sichtbarkeit wechseln. True, wenn eins davon benutzt wurde. */
    private boolean useLobbyItem(Player p, ItemStack it) {
        if (!plugin.modes().isHub(p.getWorld()) || plugin.edit().isEditing(p.getUniqueId())) return false;
        if (compass(it)) {
            if (!debounce(p)) de.kuby.kubyplugins.gui.ModeSelectScreen.open(plugin, p);
            return true;
        }
        if (dye(it)) {
            if (!debounce(p)) plugin.visibility().cycle(p);
            return true;
        }
        return false;
    }

    /** Kompass oder Farbstoff im Inventar anklicken wirkt wie in der Hand. */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (locked(p)) e.setCancelled(true);
        if (!plugin.modes().isHub(p.getWorld()) || plugin.edit().isEditing(p.getUniqueId())) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != p.getInventory()) return;
        ItemStack it = e.getCurrentItem();
        if (!compass(it) && !dye(it)) return;
        e.setCancelled(true);
        // erst das Inventar schließen, dann im nächsten Tick öffnen (sonst bleibt der Dialog hinter dem Inventar)
        boolean isCompass = compass(it);
        p.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (isCompass) de.kuby.kubyplugins.gui.ModeSelectScreen.open(plugin, p);
            else plugin.visibility().cycle(p);
        });
    }

    /** Links- oder Rechtsklick auf einen Spieler oder eine Figur mit Kompass/Farbstoff in der Hand. */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onHitEntity(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        if (useLobbyItem(e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onUseOnPlayer(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player)) return;
        if (useLobbyItem(e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && locked(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (locked(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) e.setCancelled(true);
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
