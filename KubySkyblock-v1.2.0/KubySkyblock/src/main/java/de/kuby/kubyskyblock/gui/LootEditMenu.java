package de.kuby.kubyskyblock.gui;

import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Loot-Tabelle einer Stufe im Spiel bearbeiten (Admins): oben 45 Plätze für echte Items, die Menge im Platz ist die
 * Höchstmenge beim Würfeln. Unten: Speichern und Leeren (Shift-Klick, dann gilt wieder die Liste aus der config).
 * Schließen speichert.
 */
public final class LootEditMenu implements InventoryHolder {

    private static final int CONTENT = 45, CLEAR = 45, INFO = 49, SAVE = 53;

    private final String tier;
    private final Inventory inventory;
    private boolean cleared;

    public LootEditMenu(String tier) {
        this.tier = tier;
        this.inventory = Bukkit.createInventory(this, 54, Msg.parse("<dark_gray>Loot: " + Msg.escape(tier)));
        List<ItemStack> t = KubySkyblock.get().loot().table(tier);
        for (int i = 0; i < t.size() && i < CONTENT; i++) inventory.setItem(i, t.get(i).clone());
        ItemStack glass = Items.make(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = CONTENT; i < 54; i++) inventory.setItem(i, glass);
        inventory.setItem(CLEAR, Items.make(Material.BARRIER, "<red>Tabelle leeren", List.of("<gray>Shift + Klick: wieder die Liste aus der config")));
        inventory.setItem(INFO, Items.make(Material.BOOK, "<gold>Loot-Stufe " + tier, List.of(
                "<gray>Leg Items hinein. Die Menge im Platz", "<gray>ist die Höchstmenge beim Würfeln.", "<gray>Schließen speichert.")));
        inventory.setItem(SAVE, Items.make(Material.LIME_DYE, "<green>Speichern", List.of()));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    void save() {
        if (cleared) return;
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < CONTENT; i++) {
            ItemStack it = inventory.getItem(i);
            if (it != null && !it.getType().isAir()) items.add(it.clone());
        }
        KubySkyblock.get().loot().setTable(tier, items);
    }

    /** Klicks, Ziehen und Schließen für alle offenen Loot-Editoren. */
    public static final class Events implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof LootEditMenu m)) return;
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            int raw = e.getRawSlot();
            if (raw >= 54) {
                if (e.isShiftClick()) {
                    e.setCancelled(true);
                    ItemStack cur = e.getCurrentItem();
                    if (cur == null) return;
                    for (int i = 0; i < CONTENT; i++) {
                        if (m.inventory.getItem(i) == null) {
                            m.inventory.setItem(i, cur.clone());
                            e.setCurrentItem(null);
                            return;
                        }
                    }
                }
                return;
            }
            if (raw < CONTENT) return;
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            if (raw == SAVE) {
                m.save();
                Msg.send(p, "<green>Loot-Tabelle " + m.tier + " gespeichert.");
            } else if (raw == CLEAR && e.isShiftClick()) {
                m.cleared = true;
                KubySkyblock.get().loot().setTable(m.tier, List.of());
                p.closeInventory();
                Msg.send(p, "<gray>Loot-Tabelle " + m.tier + " geleert, es gilt wieder die Liste aus der config.");
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (!(e.getInventory().getHolder() instanceof LootEditMenu)) return;
            for (int raw : e.getRawSlots()) {
                if (raw >= CONTENT && raw < 54) {
                    e.setCancelled(true);
                    return;
                }
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (e.getInventory().getHolder() instanceof LootEditMenu m) m.save();
        }
    }
}
