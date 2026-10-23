package de.kuby.kubysmp.listener;

import de.kuby.kubysmp.KubySMP;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

/** Löst Auszahlungen aus, wenn versicherte Kisten geraidet oder gesprengt werden. */
public final class InsuranceListener implements Listener {

    private final KubyPlugins plugin;

    public InsuranceListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (KubySMP.get().insurance().at(e.getBlock()) != null) {
            KubySMP.get().insurance().onBreak(e.getBlock(), e.getPlayer());
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        explode(new ArrayList<>(e.blockList()));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        explode(new ArrayList<>(e.blockList()));
    }

    private void explode(List<Block> blocks) {
        for (Block b : blocks) {
            if (KubySMP.get().insurance().at(b) != null) KubySMP.get().insurance().onExplode(b);
        }
    }
}
