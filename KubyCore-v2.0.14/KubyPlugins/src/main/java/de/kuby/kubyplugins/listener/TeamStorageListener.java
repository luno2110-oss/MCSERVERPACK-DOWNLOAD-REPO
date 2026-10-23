package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TeamService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Speichert das Team-Lager, wenn jemand es schließt. */
public final class TeamStorageListener implements Listener {

    private final KubyPlugins plugin;

    public TeamStorageListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof TeamService.StorageHolder h) plugin.teams().storageClosed(h.team);
    }
}
