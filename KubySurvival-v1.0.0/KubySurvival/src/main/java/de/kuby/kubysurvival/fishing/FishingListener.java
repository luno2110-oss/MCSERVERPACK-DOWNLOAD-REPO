package de.kuby.kubysurvival.fishing;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Fänge in Survival werden durch Fische aus dem Angel-System ersetzt. Die Rute verkürzt die Wartezeit. */
public final class FishingListener implements Listener {

    private static final String SURVIVAL = "survival";

    private final KubySurvival addon;
    private final KubyPlugins core;

    public FishingListener(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (!SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) return;
        FishingService fishing = addon.fishing();
        FishHook hook = e.getHook();
        if (fishing.types().isEmpty() || !fishing.activeAt(hook.getLocation())) return;
        switch (e.getState()) {
            case FISHING -> {
                double f = fishing.waitFactor(p.getUniqueId());
                int min = Math.max(1, (int) (hook.getMinWaitTime() * f));
                int max = Math.max(min, (int) (hook.getMaxWaitTime() * f));
                hook.setMinWaitTime(min);
                hook.setMaxWaitTime(max);
            }
            case CAUGHT_FISH -> {
                if (!(e.getCaught() instanceof Item item)) return;
                FishingService.FishType type = fishing.roll(p.getUniqueId(), hook.getWorld());
                item.setItemStack(fishing.catchFish(p, type, hook.getLocation()));
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealer(PlayerInteractEntityEvent e) {
        if (!addon.dealer().is(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        String err = addon.fishing().sellAll(e.getPlayer());
        if (err != null) Msg.send(e.getPlayer(), "<aqua>Fischhändler: <white>" + err);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDealerDamage(EntityDamageEvent e) {
        if (addon.dealer().is(e.getEntity())) e.setCancelled(true);
    }
}
