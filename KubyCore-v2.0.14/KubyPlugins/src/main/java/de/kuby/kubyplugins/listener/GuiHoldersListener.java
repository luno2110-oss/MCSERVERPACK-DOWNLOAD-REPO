package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.gui.SellMenu;
import de.kuby.kubyplugins.service.EnderChestService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

/** Steuert Enderchest (Freischalten, Sortieren, Speichern) und Verkaufs-Fenster (Bestätigen, Zurückgeben). */
public final class GuiHoldersListener implements Listener {

    private final KubyPlugins plugin;

    public GuiHoldersListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof EnderChestService.Holder h) {
            enderClick(e, top, h);
        } else if (top.getHolder() instanceof SellMenu.Holder) {
            sellClick(e, top);
        }
    }

    private void enderClick(InventoryClickEvent e, Inventory top, EnderChestService.Holder h) {
        Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
        int slot = e.getRawSlot();
        if (slot < EnderChestService.SIZE && plugin.enderchests().isUnlocked(p.getUniqueId(), slot)) return;

        e.setCancelled(true);
        if (slot == EnderChestService.SORT_SLOT) {
            plugin.enderchests().sort(p.getUniqueId(), top);
            Sfx.play(plugin, p, Sfx.Kind.CLICK);
            plugin.enderchests().refresh(p.getUniqueId(), top);
        } else if (slot == EnderChestService.STAGE_SLOT) {
            feedback(p, plugin.enderchests().buyStage(p));
            plugin.enderchests().refresh(p.getUniqueId(), top);
        } else if (slot < EnderChestService.SIZE && e.isLeftClick()) {
            feedback(p, plugin.enderchests().buySlot(p, slot));
            plugin.enderchests().refresh(p.getUniqueId(), top);
        }
    }

    private void sellClick(InventoryClickEvent e, Inventory top) {
        Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(top)) {
            int slot = e.getRawSlot();
            if (slot >= 27) {
                e.setCancelled(true);
                if (slot == SellMenu.CONFIRM) SellMenu.confirm(plugin, p, top);
                return;
            }
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> SellMenu.refresh(plugin, top));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof EnderChestService.Holder) {
            for (int raw : e.getRawSlots()) {
                if (raw >= top.getSize()) continue;
                boolean free = raw < EnderChestService.SIZE
                        && plugin.enderchests().isUnlocked(e.getWhoClicked().getUniqueId(), raw);
                if (!free) {
                    e.setCancelled(true);
                    return;
                }
            }
        } else if (top.getHolder() instanceof SellMenu.Holder) {
            for (int raw : e.getRawSlots()) {
                if (raw < top.getSize() && raw >= 27) {
                    e.setCancelled(true);
                    return;
                }
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> SellMenu.refresh(plugin, top));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (inv.getHolder() instanceof EnderChestService.Holder h) {
            plugin.enderchests().save(h.owner, inv);
        } else if (inv.getHolder() instanceof SellMenu.Holder) {
            SellMenu.returnItems(plugin, (Player) e.getPlayer(), inv);
        }
    }

    /** Rechtsklick auf eine Enderchest öffnet das Plugin-Fenster (Schleichen = vanilla). */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.ENDER_CHEST) return;
        if (e.getPlayer().isSneaking()) return;
        e.setCancelled(true);
        e.getPlayer().openInventory(plugin.enderchests().open(e.getPlayer()));
    }

    private void feedback(Player p, String err) {
        if (err != null) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
        } else {
            Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
            Msg.send(p, "<green>Freigeschaltet!");
        }
    }
}
