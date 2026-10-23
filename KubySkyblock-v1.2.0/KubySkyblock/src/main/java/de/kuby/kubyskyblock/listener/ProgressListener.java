package de.kuby.kubyskyblock.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;

/** Sammelt Items für die Checkliste: aufgehoben und hergestellt. */
public final class ProgressListener implements Listener {

    private final KubySkyblock addon;
    private final KubyPlugins core;

    public ProgressListener(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    private boolean inSkyblock(org.bukkit.World w) {
        return w != null && w.getName().equals(core.modes().worldOf(ModeService.SKYBLOCK));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !inSkyblock(p.getWorld())) return;
        addon.progress().recordItem(p, e.getItem().getItemStack().getType());
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !inSkyblock(p.getWorld())) return;
        addon.progress().recordItem(p, e.getRecipe().getResult().getType());
    }
}
