package de.kuby.kubyskyblock.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Skyblock-Welt: Werkzeug, Loot-Kisten, One-Block, Abbau nur auf der eigenen Insel. */
public final class SkyblockListener implements Listener {

    private final KubySkyblock addon;
    private final KubyPlugins core;

    public SkyblockListener(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    /** Zuletzt von Spielern gesetzte Blöcke: zählen beim Abbauen nicht für Quests (kein Setzen-und-Abbauen). */
    private static final int PLACED_MAX = 200_000;
    private final java.util.LinkedHashSet<Long> placed = new java.util.LinkedHashSet<>();

    private static long key(Block b) {
        return ((long) (b.getX() & 0x3FFFFFF) << 38) | ((long) (b.getZ() & 0x3FFFFFF) << 12) | (b.getY() & 0xFFF);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!inSkyblock(e.getBlock().getWorld())) return;
        placed.add(key(e.getBlock()));
        if (placed.size() > PLACED_MAX) {
            var it = placed.iterator();
            it.next();
            it.remove();
        }
    }

    /** Zählt der Abbau für Quests? Pflanzen nur reif, sonst nur Blöcke, die kein Spieler gesetzt hat. */
    private boolean countsForQuests(Block b) {
        boolean wasPlaced = placed.remove(key(b));
        if (b.getBlockData() instanceof org.bukkit.block.data.Ageable a && org.bukkit.Tag.CROPS.isTagged(b.getType())) {
            return a.getAge() >= a.getMaximumAge();
        }
        return !wasPlaced;
    }

    private boolean inSkyblock(org.bukkit.World w) {
        return w != null && w.getName().equals(core.modes().worldOf(ModeService.SKYBLOCK));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !inSkyblock(e.getPlayer().getWorld())) return;
        var p = e.getPlayer();
        // Werkzeug: Ecken markieren (Admin)
        if (addon.command().isTool(e.getItem()) && e.getClickedBlock() != null && p.hasPermission("kuby.admin")) {
            e.setCancelled(true);
            boolean left = e.getAction() == Action.LEFT_CLICK_BLOCK;
            boolean right = e.getAction() == Action.RIGHT_CLICK_BLOCK;
            if (left || right) {
                addon.setCorner(p.getUniqueId(), left ? 1 : 2, e.getClickedBlock().getLocation());
                de.kuby.kubyplugins.util.Msg.send(p, "<gray>Ecke " + (left ? 1 : 2) + " gesetzt.");
            }
            return;
        }
        // Loot-Kiste öffnen
        Block b = e.getClickedBlock();
        if (b != null && e.getAction() == Action.RIGHT_CLICK_BLOCK && b.getType() == Material.CHEST) {
            var isl = addon.loot().islandAt(b);
            if (isl != null) {
                e.setCancelled(true);
                String err = addon.loot().open(p, isl);
                if (err != null) de.kuby.kubyplugins.util.Msg.send(p, "<gray>" + err);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!inSkyblock(e.getBlock().getWorld())) return;
        var p = e.getPlayer();
        Block b = e.getBlock();
        // One-Block: Loot geben, neuer zufälliger Block
        if (addon.oneblock().isOneBlock(b)) {
            e.setCancelled(true);
            addon.oneblock().harvest(p, b);
            return;
        }
        // Loot-Kisten sind geschützt
        if (addon.loot().islandAt(b) != null && !p.hasPermission("kuby.admin")) {
            e.setCancelled(true);
            return;
        }
        // Abbau nur auf der eigenen Insel (Mitglieder), außerhalb nur Admins
        if (!addon.islands().canBuild(p, b.getLocation())) {
            e.setCancelled(true);
            p.sendActionBar(de.kuby.kubyplugins.util.Msg.parse("<red>Das ist nicht deine Insel."));
            return;
        }
        addon.islands().addPoints(b.getLocation(), b.getType(), -1);
        if (countsForQuests(b)) core.questProgress(p, "abbau", b.getType(), 1);
        addon.streaks().reward(p);
        addon.progress().recordBlock(p);
    }
}
