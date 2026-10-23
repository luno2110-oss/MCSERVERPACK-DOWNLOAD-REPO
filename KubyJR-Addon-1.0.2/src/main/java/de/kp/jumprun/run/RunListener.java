package de.kp.jumprun.run;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.arena.Arena;
import de.kp.jumprun.util.Announcer;
import de.kp.jumprun.util.Text;
import de.kp.jumprun.util.TimeFormat;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

public final class RunListener implements Listener {

    private final KPJumpRun plugin;
    private final RunManager runs;
    private final RunItems items;

    public RunListener(KPJumpRun plugin, RunManager runs) {
        this.plugin = plugin;
        this.runs = runs;
        this.items = runs.items();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        Block below = to.clone().subtract(0, 0.1, 0).getBlock();
        Run run = runs.get(player.getUniqueId());

        // Noch kein Run: Start-Block betreten startet den Run
        if (run == null) {
            Arena arena = findStart(below);
            if (arena != null) {
                runs.start(player, arena, below.getY());
            }
            return;
        }

        Arena arena = plugin.arenas().get(run.getArena());
        if (arena == null) {
            runs.end(player, true);
            return;
        }

        if (player.isOnGround()) {
            run.setLastGroundBlockY(below.getY());
        }

        if (matches(arena.getFinish(), below)) {
            runs.finish(player);
            return;
        }

        List<Location> checkpoints = arena.getCheckpoints();
        for (int i = 0; i < checkpoints.size(); i++) {
            if (!run.hasReached(i) && matches(checkpoints.get(i), below)) {
                run.markReached(i);
                Announcer.announce(player, arena, "checkpoint", Map.of(
                        "index", String.valueOf(i + 1),
                        "total", String.valueOf(checkpoints.size()),
                        "time", TimeFormat.format(run.elapsed())));
            }
        }

        // Fall-Reset: 2 Blöcke (config: fall.reset-blocks) unter dem letzten Boden-Block
        int resetBlocks = plugin.getConfig().getInt("fall.reset-blocks", 2);
        if (to.getY() <= run.getLastGroundBlockY() - resetBlocks + 1) {
            if (run.lastCheckpoint() >= 0) {
                runs.respawnAtCheckpoint(player);
            } else {
                runs.restart(player, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        if (item == null || runs.get(player.getUniqueId()) == null) {
            return;
        }

        if (items.isQuit(item)) {
            event.setCancelled(true);
            runs.quit(player);
            return;
        }

        if (items.isRestart(item)) {
            event.setCancelled(true);
            runs.restart(player, false);
            Text.send(player, "Run neu gestartet.");
            return;
        }

        if (items.isFeather(item)) {
            event.setCancelled(true);
            // Die Feder wird verbraucht, der Boost gilt einmal pro Run
            player.getInventory().setItem(RunItems.FEATHER_SLOT, null);

            double boost = plugin.getConfig().getDouble("feather.boost-blocks", 3.0);
            Vector direction = player.getLocation().getDirection().normalize().multiply(boost * 0.5);
            player.setVelocity(direction);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (runs.get(event.getPlayer().getUniqueId()) != null
                && items.isRunItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (runs.get(player.getUniqueId()) == null) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked instanceof PlayerInventory && isRunSlot(event.getSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        runs.onDeath(event.getEntity(), event.getDrops());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        runs.restorePending(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        runs.restorePending(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runs.end(event.getPlayer(), true);
    }

    private Arena findStart(Block below) {
        for (Arena arena : plugin.arenas().all()) {
            if (arena.isComplete() && matches(arena.getStart(), below)) {
                return arena;
            }
        }
        return null;
    }

    private static boolean matches(Location location, Block block) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().equals(block.getWorld())
                && location.getBlockX() == block.getX()
                && location.getBlockY() == block.getY()
                && location.getBlockZ() == block.getZ();
    }

    private static boolean isRunSlot(int slot) {
        for (int runSlot : RunItems.SLOTS) {
            if (runSlot == slot) {
                return true;
            }
        }
        return false;
    }
}
