package de.kuby.kubyplugins.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof GuiMenu menu) {
            menu.handleClick(event);
        } else if (event.getInventory().getHolder() instanceof KitEditMenu editor) {
            editor.click(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiMenu) {
            event.setCancelled(true);
        } else if (event.getInventory().getHolder() instanceof KitEditMenu editor) {
            editor.drag(event);
        }
    }

    /** Kit-Editor: beim Schließen speichern. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof KitEditMenu editor) editor.save();
    }
}
