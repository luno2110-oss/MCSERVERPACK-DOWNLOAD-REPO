package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TeamService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Team-Lager (v2.07: zwei Seiten zu 3 Reihen, 4. Reihe Knöpfe): Knöpfe auswerten, Knopfreihe und gesperrte Plätze
 * schützen, beim Schließen speichern.
 */
public final class TeamStorageListener implements Listener {

    private final KubyPlugins plugin;

    public TeamStorageListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof TeamService.StorageHolder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        boolean top = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        // Einsammeln per Doppelklick könnte Knöpfe greifen: im Lager nur über freie Plätze
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        if (!top) return;
        if (raw >= TeamService.PAGE_SLOTS) {
            e.setCancelled(true);
            if (e.getClick().isLeftClick() || e.getClick().isRightClick()) plugin.teams().storageButton(p, h.team, h.page, raw);
            return;
        }
        if (!plugin.teams().unlocked(h.team, h.page, raw)) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof TeamService.StorageHolder h)) return;
        int size = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < size && (raw >= TeamService.PAGE_SLOTS || !plugin.teams().unlocked(h.team, h.page, raw))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof TeamService.StorageHolder h) plugin.teams().storageClosed(h.team);
    }
}
