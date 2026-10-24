package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubysurvival.KubySurvival;
import de.kuby.kubysurvival.jobs.Job;
import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Angeln in Survival: Wartezeit (Upgrades, Köder, Zone), Biss-Hinweis, Fang durch eigene Fische ersetzen.
 * Fisch-Items lassen sich nicht braten, verbauen oder verfüttern. Rechtsklick auf den Fischhändler öffnet sein Menü.
 */
public final class AngelListener implements Listener {

    private static final String SURVIVAL = "survival";

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final AngelService service;
    private final AngelFx fx;
    private final AngelMenus menus;
    private final AngelDealer dealer;

    public AngelListener(KubySurvival addon, KubyPlugins core, AngelService service, AngelFx fx, AngelMenus menus,
                         AngelDealer dealer) {
        this.addon = addon;
        this.core = core;
        this.service = service;
        this.fx = fx;
        this.menus = menus;
        this.dealer = dealer;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (!SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return;
        FishHook hook = e.getHook();
        switch (e.getState()) {
            case FISHING -> {
                boolean zone = service.zoneAt(hook.getLocation()) != null || service.zoneAt(p.getLocation()) != null;
                double f = Math.max(0.2, 1.0 - service.faster(p.getUniqueId(), zone) / 100.0);
                int min = Math.max(1, (int) (hook.getMinWaitTime() * f));
                int max = Math.max(min, (int) (hook.getMaxWaitTime() * f));
                hook.setMinWaitTime(min);
                hook.setMaxWaitTime(max);
                fx.cast(p, hook);
            }
            case BITE -> fx.bite(p);
            case CAUGHT_FISH -> {
                if (!(e.getCaught() instanceof Item item)) return;
                Location at = hook.getLocation();
                AngelService.Catch c = service.catchFish(p, at);
                ItemStack fish = AngelItems.fish(c.species(), c.stars(), c.amount());
                if (c.autoSold() > 0) {
                    try {
                        item.setItemStack(ItemStack.empty());
                    } catch (IllegalArgumentException ex) {
                        item.remove();
                    }
                } else {
                    item.setItemStack(fish);
                    item.setPickupDelay(0);
                    item.setVelocity(item.getVelocity().multiply(0.2));
                    item.teleport(p.getLocation().add(0, 0.3, 0));
                }
                fx.caught(p, at, c, fish);
                Rarity r = c.species().rarity();
                int xp = 4 + 4 * r.ordinal() * r.ordinal();
                addon.jobs().reward(p, Job.ANGLER, xp, xp);
                addon.quests().progress(p, "fish", null, c.amount());
            }
            default -> fx.stop(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        fx.stop(e.getPlayer());
    }

    // ---------- Händler ----------

    @EventHandler(ignoreCancelled = true)
    public void onDealer(PlayerInteractEntityEvent e) {
        if (dealer.is(e.getRightClicked())) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) menus.openDealer(e.getPlayer(), null);
            return;
        }
        // Fische nicht verfüttern (Katzen, Delfine, Axolotl ...)
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (AngelItems.isFish(hand) && (e.getRightClicked() instanceof Animals || e.getRightClicked() instanceof WaterMob
                || e.getRightClicked() instanceof Dolphin || e.getRightClicked() instanceof Axolotl
                || e.getRightClicked() instanceof Villager)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealerDamage(EntityDamageEvent e) {
        if (dealer.is(e.getEntity())) e.setCancelled(true);
    }

    // ---------- Fisch-Items schützen ----------

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceStartSmeltEvent e) {
        if (AngelItems.isFish(e.getSource())) e.setTotalCookTime(Integer.MAX_VALUE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCook(BlockCookEvent e) {
        if (AngelItems.isFish(e.getSource())) e.setCancelled(true);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (AngelItems.isFish(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
