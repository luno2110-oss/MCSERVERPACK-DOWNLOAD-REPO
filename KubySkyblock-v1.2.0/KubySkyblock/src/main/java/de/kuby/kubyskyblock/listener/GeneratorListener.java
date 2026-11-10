package de.kuby.kubyskyblock.listener;

import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.gui.GeneratorScreen;
import de.kuby.kubyskyblock.service.GeneratorService;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Generatoren laufen nur in der UI: ein Generator-Item wird per Rechtsklick auf der eigenen Insel eingesetzt (es wird
 * nie als Block gesetzt). Alte Generator-Blöcke aus der Zeit vor der Umstellung werden beim Laden des Chunks entfernt.
 */
public final class GeneratorListener implements Listener {

    private final KubySkyblock addon;

    public GeneratorListener(KubySkyblock addon) {
        this.addon = addon;
    }

    private GeneratorService gens() {
        return addon.generators();
    }

    /** Rechtsklick mit einem Generator-Item: einsetzen statt setzen. */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        Object[] info = gens().read(it);
        if (info == null) return;
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        install(e.getPlayer(), it, info);
    }

    /** Sicherheitsnetz: ein Generator-Item wird nie als Block gesetzt. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (gens().read(e.getItemInHand()) != null) e.setCancelled(true);
    }

    private void install(Player p, ItemStack it, Object[] info) {
        GeneratorService.Type t = (GeneratorService.Type) info[0];
        GeneratorService.Quality q = (GeneratorService.Quality) info[1];
        String err = gens().install(p, t, q, (Integer) info[2]);
        if (err != null) {
            Sfx.play(addon.core(), p, Sfx.Kind.ERROR);
            Msg.send(p, "<red>" + err);
            return;
        }
        it.setAmount(it.getAmount() - 1);
        Sfx.play(addon.core(), p, Sfx.Kind.SUCCESS);
        Msg.send(p, "<" + q.color + ">" + Msg.escape(gens().name(t, q)) + " <#9AD8FF>läuft jetzt auf deiner Insel. <gray>Lager und Upgrades: /insel → Generatoren");
        GeneratorScreen.island(p);
    }

    /** Alte Generator-Blöcke (Barriere/Spawner) beim Laden des Chunks entfernen. */
    @EventHandler
    public void onChunk(org.bukkit.event.world.ChunkLoadEvent e) {
        gens().chunkLoaded(e.getChunk());
    }

    /** Sollte ein alter Generator-Block noch stehen: Abbauen entfernt ihn nur (der Generator läuft im Menü weiter). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (gens().isLegacyBlock(e.getBlock())) e.setDropItems(false);
    }
}
