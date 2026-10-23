package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Rechtsklick auf den Blackjack-Tisch startet eine Runde, Rechtsklick auf den Slots-Automaten eine Drehung.
 * Weggehen, Ausloggen und Sterben beenden eine Blackjack-Runde.
 */
public final class BlackjackListener implements Listener {

    private final KubyPlugins plugin;

    public BlackjackListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null) return;
        String err;
        if (plugin.blackjack().isTable(e.getClickedBlock().getLocation())) {
            err = plugin.blackjack().start(e.getPlayer());
        } else if (plugin.slots().isMachine(e.getClickedBlock().getLocation())) {
            err = plugin.slots().pull(e.getPlayer());
        } else {
            return;
        }
        e.setCancelled(true);
        if (err != null) Msg.send(e.getPlayer(), "<red>" + err);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.hasChangedBlock()) plugin.blackjack().checkAway(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.blackjack().checkAway(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.blackjack().leave(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.blackjack().leave(e.getEntity().getUniqueId());
    }
}
