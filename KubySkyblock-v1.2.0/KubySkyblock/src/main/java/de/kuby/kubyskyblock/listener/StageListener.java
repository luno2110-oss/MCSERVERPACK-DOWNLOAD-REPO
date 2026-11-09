package de.kuby.kubyskyblock.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService;
import de.kuby.kubyskyblock.service.StageService;
import de.kuby.kubyskyblock.service.StageService.Stage;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.PortalType;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Portale in Skyblock: Ein Nether-Portal auf der Insel führt zur eigenen Nether-Insel, ein End-Portal zur End-Insel
 * (wenn freigeschaltet, sonst ein Hinweis auf die Bedingungen). In den Stufen-Welten führt jedes Portal zurück nach
 * Hause. Tiere, Monster und Items gehen nicht durch Portale. In der End-Stufe gibt es keinen Drachen.
 */
public final class StageListener implements Listener {

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final Map<UUID, Long> lastHint = new HashMap<>();

    public StageListener(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    private final Map<UUID, Long> lastPortal = new HashMap<>();

    /**
     * Portal betreten (Nether- oder End-Portal) in Skyblock: das Vanilla-Portal läuft nie, wir reisen sofort selbst.
     * So hängt es nicht an allow-nether/allow-end, an der Vanilla-Zielwelt (world_nether) oder an der Wartezeit im
     * Portal. Das Ereignis kommt jeden Tick, solange man im Portal steht: 3 Sekunden Sperre je Spieler.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPortalEnter(EntityPortalEnterEvent e) {
        if (!(e.getEntity() instanceof Player p) || addon.stages() == null || !addon.isSkyblockWorld(p.getWorld())) return;
        PortalType type = e.getPortalType();
        if (type != PortalType.NETHER && type != PortalType.ENDER) return;
        e.setCancelled(true);
        if (!ready(p)) return;
        use(p, type == PortalType.ENDER ? Stage.END : Stage.NETHER);
    }

    /** Rückfall, falls doch ein Vanilla-Portal-Ereignis kommt: nie in die Vanilla-Welten, sondern wie oben. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent e) {
        Player p = e.getPlayer();
        if (!addon.isSkyblockWorld(p.getWorld()) || addon.stages() == null) return;
        e.setCancelled(true);
        if (!ready(p)) return;
        use(p, e.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL ? Stage.END : Stage.NETHER);
    }

    private boolean ready(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastPortal.get(p.getUniqueId());
        if (last != null && now - last < 3000) return false;
        lastPortal.put(p.getUniqueId(), now);
        return true;
    }

    /** In einer Stufen-Welt: nach Hause. Auf der Insel: zur Stufen-Insel (gesperrt: Hinweis mit Stand). */
    private void use(Player p, Stage target) {
        StageService ss = addon.stages();
        IslandService.Island i = addon.islands().islandOf(p.getUniqueId());
        if (i == null) return;
        if (ss.stageOf(p.getWorld()) != null) {
            addon.islands().home(p, i);
            return;
        }
        if (!ss.unlocked(i, target)) {
            long now = System.currentTimeMillis();
            Long last = lastHint.get(p.getUniqueId());
            if (last != null && now - last < 5000) return;
            lastHint.put(p.getUniqueId(), now);
            Msg.send(p, "<" + target.color + ">Die " + target.label + "-Stufe ist noch gesperrt <gray>(" + ss.progressText(i, target)
                    + "). Mehr mit <white>/insel stufen</white>.");
            return;
        }
        String err = ss.travel(p, target);
        if (err != null) Msg.send(p, "<red>" + err);
    }

    /** Keine Wesen und Items durch Portale (sonst landen sie in der normalen Nether- oder End-Welt). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        if (addon.isSkyblockWorld(e.getEntity().getWorld())) e.setCancelled(true);
    }

    /** Portale werden in Skyblock nicht von selbst gebaut (Ziel ist immer die eigene Stufen-Insel). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        if (e.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR && addon.isSkyblockWorld(e.getWorld())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getEntityType() != EntityType.ENDER_DRAGON || addon.stages() == null) return;
        if (addon.stages().stageOf(e.getLocation().getWorld()) != null) e.setCancelled(true);
    }

    /** Tod in einer Stufen-Welt: wieder auf der eigenen Stufen-Insel (außer Bett oder Seelenanker). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (addon.stages() == null || e.isBedSpawn() || e.isAnchorSpawn()) return;
        Stage s = addon.stages().stageOf(e.getPlayer().getWorld());
        if (s == null) return;
        IslandService.Island i = addon.islands().islandOf(e.getPlayer().getUniqueId());
        if (i == null || !addon.stages().unlocked(i, s)) return;
        e.setRespawnLocation(addon.stages().center(i, s).add(0.5, 0, 0.5));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        if (addon.stages() != null && addon.stages().stageOf(e.getWorld()) == Stage.END) addon.stages().calmDragon(e.getWorld());
    }
}
