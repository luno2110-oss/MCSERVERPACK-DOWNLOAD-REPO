package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/** Abbauen der eigenen Modelle und Wiederherstellen nach dem Laden eines Chunks. */
public final class ModelListener implements Listener {

    private final KubyPlugins plugin;

    public ModelListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!e.getEntity().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "kp_model_hit"),
                org.bukkit.persistence.PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        plugin.models().remove(e.getEntity(), p);
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        plugin.models().ensureChunk(e.getChunk());
    }
}
