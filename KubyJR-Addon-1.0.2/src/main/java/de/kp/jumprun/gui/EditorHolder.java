package de.kp.jumprun.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Kennzeichnet die Editor-Menüs, damit Klicks nur dort abgefangen werden.
 */
public final class EditorHolder implements InventoryHolder {

    public enum Screen { MAIN, POINTS, TEXTS, SOUNDS }

    private final Screen screen;
    private final String arena;
    private Inventory inventory;

    public EditorHolder(Screen screen, String arena) {
        this.screen = screen;
        this.arena = arena;
    }

    public Screen screen() {
        return screen;
    }

    public String arena() {
        return arena;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
