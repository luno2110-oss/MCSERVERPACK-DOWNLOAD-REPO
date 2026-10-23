package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TradeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Sperrt alles außer dem eigenen Angebotsbereich im Handels-GUI. */
public final class TradeListener implements Listener {

    private final KubyPlugins plugin;

    public TradeListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeService.TradeHolder h)) return;
        Player p = (Player) e.getWhoClicked();

        // Doppelklick würde Items aus der Spiegelung aufsammeln, also immer sperren
        if (e.getClick() == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            return;
        }
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) {
            e.setCancelled(true);
            return;
        }
        if (!clicked.equals(top)) {
            if (e.isShiftClick()) e.setCancelled(true);
            return;
        }

        int slot = e.getRawSlot();
        boolean ownOffer = slot / 9 < 5 && slot % 9 < 4;
        if (ownOffer) {
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.trades().changed(h.session));
            return;
        }

        e.setCancelled(true);
        if (slot == TradeService.CONFIRM_SLOT) plugin.trades().confirm(p);
        else if (slot == TradeService.CANCEL_SLOT) plugin.trades().cancel(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof TradeService.TradeHolder h)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                boolean ownOffer = raw / 9 < 5 && raw % 9 < 4;
                if (!ownOffer) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.trades().changed(h.session));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof TradeService.TradeHolder h) {
            plugin.trades().abort(h.session, "<gray>Handel beendet, Angebote zurückgegeben.");
        }
    }
}
