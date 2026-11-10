package de.kuby.kubysmp.listener;

import de.kuby.kubysmp.KubySMP;
import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Raid-Versicherung: Besitzer-Markierung beim Aufstellen einer Kiste, Auszahlung, wenn versicherte Kisten geraidet
 * werden. MONITOR und nur nicht abgebrochene Ereignisse: früher zahlte die Versicherung auch, wenn ein Schutzgebiet
 * den Abbau oder die Explosion verhindert hatte (die Kiste stand noch, das Geld war trotzdem da).
 */
public final class InsuranceListener implements Listener {

    private final KubyPlugins plugin;

    public InsuranceListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Material m = e.getBlockPlaced().getType();
        if (m != Material.CHEST && m != Material.TRAPPED_CHEST) return;
        Block b = e.getBlockPlaced();
        java.util.UUID owner = e.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (b.getType() == m) KubySMP.get().insurance().markOwner(b, owner);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (KubySMP.get().insurance().at(e.getBlock()) != null) {
            KubySMP.get().insurance().onBreak(e.getBlock(), e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        explode(new ArrayList<>(e.blockList()), sourceOf(e.getEntity()));
    }

    /** Betten, Seelenanker usw.: Auslöser unbekannt, Kiste ist weg, aber keine Auszahlung. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        explode(new ArrayList<>(e.blockList()), null);
    }

    private void explode(List<Block> blocks, Player source) {
        for (Block b : blocks) {
            if (KubySMP.get().insurance().at(b) != null) KubySMP.get().insurance().onExplode(b, source);
        }
    }

    /** Wer hat die Explosion ausgelöst? TNT (wer es gezündet hat), Creeper (sein Ziel), Geschosse (Schütze). */
    private static Player sourceOf(Entity e) {
        if (e instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (e instanceof Creeper c && c.getTarget() instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }
}
