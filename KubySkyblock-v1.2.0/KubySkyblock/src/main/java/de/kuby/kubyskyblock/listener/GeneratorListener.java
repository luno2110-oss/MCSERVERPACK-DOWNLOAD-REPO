package de.kuby.kubyskyblock.listener;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.gui.GeneratorScreen;
import de.kuby.kubyskyblock.service.GeneratorService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/** Generatoren aufstellen, öffnen (Rechtsklick), abbauen (Item mit Stufe zurück) und vor Explosionen und Kolben schützen. */
public final class GeneratorListener implements Listener {

    private final KubySkyblock addon;

    public GeneratorListener(KubySkyblock addon) {
        this.addon = addon;
    }

    private GeneratorService gens() {
        return addon.generators();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Object[] info = gens().read(e.getItemInHand());
        if (info == null) return;
        Player p = e.getPlayer();
        String err = gens().place(p, e.getBlockPlaced(), (GeneratorService.Type) info[0], (GeneratorService.Quality) info[1], (Integer) info[2]);
        if (err != null) {
            e.setCancelled(true);
            Sfx.play(addon.core(), p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
            return;
        }
        Sfx.play(addon.core(), p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<#9AD8FF>Generator aufgestellt. <gray>Rechtsklick öffnet ihn.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        GeneratorService.Gen g = gens().at(e.getClickedBlock());
        if (g == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        var isl = addon.islands().bySlot(g.island);
        if (isl == null || !isl.isMember(p.getUniqueId()) && !p.hasPermission("kuby.admin")) {
            Msg.send(p, "<gray>Das ist ein Generator von " + (isl == null ? "?" : de.kuby.kubyskyblock.service.IslandService.nameOf(isl.owner)) + ".");
            return;
        }
        GeneratorScreen.open(p, g);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        GeneratorService.Gen g = gens().at(e.getBlock());
        if (g == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        var isl = addon.islands().bySlot(g.island);
        if (isl == null || !isl.isMember(p.getUniqueId()) && !p.hasPermission("kuby.admin")) return;
        e.getBlock().setType(org.bukkit.Material.AIR);
        gens().remove(p, g);
        Msg.send(p, "<gray>Generator abgebaut, er liegt mit seiner Stufe in deinem Inventar.");
    }

    private void protect(List<Block> blocks) {
        blocks.removeIf(b -> gens().at(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        protect(e.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        protect(e.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (gens().at(b) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (gens().at(b) != null) e.setCancelled(true);
    }
}
