package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.TotenkisteService;
import de.kuby.kubyplugins.service.TotenkisteService.Box;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Öffnen, Schützen, Abbauen und Sprengen der Totenkiste sowie das Sichern beim Tod. */
public final class TotenkisteListener implements Listener {

    private final KubyPlugins plugin;

    public TotenkisteListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.CHEST) return;
        Box b = plugin.totenkisten().at(e.getClickedBlock());
        if (b == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!plugin.totenkisten().canOpen(p.getUniqueId(), b)) {
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Diese Totenkiste ist vollständig gesichert.");
            return;
        }
        p.openInventory(plugin.totenkisten().open(b));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Box b = plugin.totenkisten().at(e.getBlock());
        if (b == null) return;
        Player p = e.getPlayer();
        if (p.getUniqueId().equals(b.owner)) {
            if (b.level == 3) {
                e.setCancelled(true);
                Msg.send(p, "<red>Senke die Stufe zuerst per Rechtsklick auf die Stufen-Anzeige.");
                return;
            }
            e.setDropItems(false);
            List<ItemStack> items = plugin.totenkisten().emptyAll(b);
            plugin.totenkisten().remove(b);
            for (ItemStack it : items) {
                p.getInventory().addItem(it).values().forEach(rest -> plugin.abholbox().add(p.getUniqueId(), rest));
            }
            Msg.send(p, "<gray>Totenkiste abgebaut, Inhalt zurück in dein Inventar.");
            return;
        }
        if (b.level >= 2) {
            e.setCancelled(true);
            Sfx.play(plugin, p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>Diese Totenkiste ist gesichert.");
            return;
        }
        e.setDropItems(false);
        dropBox(b, e.getBlock());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        protect(e.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        protect(e.blockList());
    }

    private void protect(List<Block> blocks) {
        blocks.removeIf(bl -> {
            Box b = plugin.totenkisten().at(bl);
            if (b == null) return false;
            if (b.level >= 2) return true;
            dropBox(b, bl);
            return false;
        });
    }

    /** Entfernt die Kiste und lässt den Inhalt auf den Boden fallen. */
    private void dropBox(Box b, Block block) {
        List<ItemStack> items = plugin.totenkisten().emptyAll(b);
        plugin.totenkisten().remove(b);
        block.setType(Material.AIR, false);
        for (ItemStack it : items) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), it);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof TotenkisteService.Holder h)) return;
        Player p = (Player) e.getWhoClicked();
        Box b = plugin.totenkisten().byKey(h.key);
        if (b == null) {
            e.setCancelled(true);
            return;
        }
        boolean owner = p.getUniqueId().equals(b.owner);
        boolean topClick = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        int slot = e.getRawSlot();

        if (topClick && slot >= 45) {
            e.setCancelled(true);
            if (owner) controls(p, b, slot, e, top);
            return;
        }
        if (topClick && slot >= b.unlocked) {
            e.setCancelled(true);
            if (owner && e.isLeftClick()) {
                feedback(p, plugin.totenkisten().buySlot(p, b));
                plugin.totenkisten().refresh(b, top);
            }
            return;
        }
        if (owner) return;
        if (b.level == 1) return;

        boolean allowed = false;
        if (!topClick && !e.isShiftClick()) {
            allowed = true;
        } else if (b.level == 2 && topClick) {
            ItemStack cur = e.getCurrentItem();
            boolean cursorEmpty = e.getCursor() == null || e.getCursor().getType().isAir();
            allowed = cursorEmpty && cur != null && !cur.getType().isAir() && !plugin.totenkisten().protectedItem(cur);
        }
        if (!allowed) e.setCancelled(true);
    }

    private void controls(Player p, Box b, int slot, InventoryClickEvent e, Inventory top) {
        if (slot == 47) {
            int target = e.isRightClick() ? b.level - 1 : b.level + 1;
            feedback(p, plugin.totenkisten().setLevel(p, b, target));
        } else if (slot == 49) {
            feedback(p, plugin.totenkisten().buySlot(p, b));
        } else {
            return;
        }
        plugin.totenkisten().refresh(b, top);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof TotenkisteService.Holder h)) return;
        Box b = plugin.totenkisten().byKey(h.key);
        boolean owner = b != null && e.getWhoClicked().getUniqueId().equals(b.owner);
        for (int raw : e.getRawSlots()) {
            if (raw >= top.getSize()) continue;
            if (!owner || raw >= 45 || (b != null && raw >= b.unlocked)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof TotenkisteService.Holder h)) return;
        Box b = plugin.totenkisten().byKey(h.key);
        if (b != null) plugin.totenkisten().save(b, inv);
    }

    /** Beim Tod wandert das Inventar in die eigene Totenkiste, Duelle bleiben unberührt. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory()) return;
        Player p = e.getEntity();
        if (plugin.pvp().isDueling(p.getUniqueId())) return;
        Box b = plugin.totenkisten().ownedBy(p.getUniqueId());
        if (b == null) return;
        List<ItemStack> left = plugin.totenkisten().depositDeath(b, p.getInventory().getContents());
        e.getDrops().clear();
        e.getDrops().addAll(left);
        Msg.send(p, "<gray>Dein Inventar ist in deiner Totenkiste bei <white>" + b.x + " / " + b.y + " / " + b.z
                + " <gray>sicher.");
    }

    private void feedback(Player p, String err) {
        Sfx.play(plugin, p, err == null ? Sfx.Kind.SUCCESS : Sfx.Kind.ERROR);
        if (err != null) Msg.send(p, "<red>" + err);
    }
}
