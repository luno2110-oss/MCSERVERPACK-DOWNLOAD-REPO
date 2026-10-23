package de.kp.jumprun.editor;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.arena.Arena;
import de.kp.jumprun.util.Text;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class EditorListener implements Listener {

    private final KPJumpRun plugin;
    private final EditorStick stick;

    public EditorListener(KPJumpRun plugin) {
        this.plugin = plugin;
        this.stick = plugin.editorStick();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (!stick.isStick(item)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR;

        // Sneak + Rechtsklick wechselt den Modus
        if (player.isSneaking() && rightClick) {
            cycleMode(player, item);
            return;
        }

        // Rückkehr-Punkt: Rechtsklick setzt ihn genau an die Position des Spielers
        if (stick.getMode(item) == EditorStick.Mode.EXIT) {
            if (rightClick) {
                Arena exitArena = plugin.arenas().getOrCreate(stick.getArena(item));
                exitArena.setExit(player.getLocation().clone());
                plugin.arenas().save();
                Text.send(player, "Rückkehr-Punkt gesetzt. Nach dem Ziel landest du hier.");
            }
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        boolean rightBlock = action == Action.RIGHT_CLICK_BLOCK;
        boolean leftBlock = action == Action.LEFT_CLICK_BLOCK;
        if (!rightBlock && !leftBlock) {
            return;
        }

        Location location = block.getLocation();
        Arena arena = plugin.arenas().getOrCreate(stick.getArena(item));
        EditorStick.Mode mode = stick.getMode(item);

        if (mode == EditorStick.Mode.START_FINISH) {
            if (rightBlock) {
                arena.setStart(location);
                Text.send(player, "Start gesetzt: <white>" + coords(location) + "</white>");
            } else {
                arena.setFinish(location);
                Text.send(player, "Ziel gesetzt: <white>" + coords(location) + "</white>");
            }
        } else {
            if (rightBlock) {
                arena.getCheckpoints().add(location);
                Text.send(player, "Checkpoint <yellow>#" + arena.getCheckpoints().size()
                        + "</yellow> gesetzt: <white>" + coords(location) + "</white>");
            } else {
                if (arena.getCheckpoints().isEmpty()) {
                    Text.send(player, "<red>Keine Checkpoints zum Entfernen vorhanden.</red>");
                    return;
                }
                Location removed = arena.getCheckpoints().remove(arena.getCheckpoints().size() - 1);
                Text.send(player, "Checkpoint entfernt: <white>" + coords(removed) + "</white>");
            }
        }

        plugin.arenas().save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (stick.isStick(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (stick.isStick(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private void cycleMode(Player player, ItemStack item) {
        EditorStick.Mode next = stick.getMode(item).next();
        player.getInventory().setItemInMainHand(stick.create(stick.getArena(item), next));
        Text.send(player, "Modus: <yellow>" + next.label() + "</yellow>");
    }

    private static String coords(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }
}
