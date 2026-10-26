package de.kuby.kubyplugins.gui;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.SmallCaps;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Basisklasse für alle Menüs. Jeder Slot kann eine eigene Klick-Aktion haben. */
public abstract class GuiMenu implements InventoryHolder {

    protected final KubyPlugins plugin;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected GuiMenu(KubyPlugins plugin, Component title, int rows) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, rows * 9, de.kuby.kubyplugins.util.I18n.ctx(title));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        inventory.clear();
        actions.clear();
        de.kuby.kubyplugins.util.I18n.as(player, () -> build(player));
        if (fillEmpty()) {
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) inventory.setItem(i, Items.filler());
            }
        }
        player.openInventory(inventory);
        Sfx.play(plugin, player, Sfx.Kind.OPEN);
    }

    protected abstract void build(Player player);

    /** Leere Plätze mit Glas füllen (Standard). Menüs im Clip-Stil lassen sie leer. */
    protected boolean fillEmpty() {
        return true;
    }

    /** Baut den Inhalt neu, ohne das Fenster zu schließen (Mauszeiger bleibt stehen). */
    public void refresh(Player player) {
        inventory.clear();
        actions.clear();
        de.kuby.kubyplugins.util.I18n.as(player, () -> build(player));
        if (fillEmpty()) {
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) inventory.setItem(i, Items.filler());
            }
        }
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    /** Zurück-Button mittig in der untersten Reihe. */
    protected void addBack(Player player) {
        set(inventory.getSize() - 5,
                Items.make(Material.ARROW, "<gray>" + SmallCaps.of("Zurück"), List.of()),
                e -> new MainMenu(plugin).open(player));
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;
        Consumer<InventoryClickEvent> action = actions.get(slot);
        if (action != null) {
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player clicker) {
                Sfx.play(plugin, clicker, Sfx.Kind.CLICK);
            }
            action.accept(event);
        }
    }
}
