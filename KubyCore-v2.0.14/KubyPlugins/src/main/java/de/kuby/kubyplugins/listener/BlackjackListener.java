package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoRules;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
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
 * Rechtsklick auf den Blackjack-Tisch startet eine Runde, Rechtsklick auf den Slots-Automaten eine Drehung,
 * Rechtsklick auf den Crash-Automaten steigt ein oder aus. Schleichen + Rechtsklick öffnet die Regeln des Spiels,
 * Rechtsklick auf Kassentisch oder Roulette-Tisch öffnet das Regelbuch.
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
        Location at = e.getClickedBlock().getLocation();
        Player p = e.getPlayer();
        String game;
        if (plugin.blackjack().isTable(at)) game = "blackjack";
        else if (plugin.roulette().isTable(at)) game = "roulette";
        else if (plugin.slots().isMachine(at)) game = "slots";
        else if (plugin.crash().isMachine(at)) game = "crash";
        else if (plugin.casino().isKasse(at)) game = "kasse";
        else return;
        e.setCancelled(true);
        // Kassentisch öffnet die Kasse; Roulette-Tisch (Einsätze per Befehl) und Schleichen + Rechtsklick die Regeln.
        if (game.equals("kasse")) {
            String blocked = plugin.casino().checkMode(p);
            if (blocked != null) Msg.send(p, "<red>" + blocked);
            else new de.kuby.kubyplugins.casino.KasseMenu(plugin).open(p);
            return;
        }
        if (p.isSneaking() || game.equals("roulette")) {
            CasinoRules.open(plugin, p, game);
            return;
        }
        String err = switch (game) {
            case "blackjack" -> plugin.blackjack().start(p);
            case "slots" -> plugin.slots().pull(p);
            default -> plugin.crash().click(p);
        };
        if (err != null) Msg.send(p, "<red>" + err);
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
