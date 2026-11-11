package de.kuby.kubyskyblock.listener;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inselschutz in der Skyblock-Welt: Bauen, Kisten, Türen, Schalter, Eimer, Tiere, Rahmen und Rüstungsständer nur für
 * Mitglieder der Insel (Admins überall); außerhalb der Inseln (Spawn, Loot-Inseln) baut niemand außer Admins.
 * Dazu: wer ins Leere fällt, landet zu Hause; der Bruchstein-Generator liefert je Stufe Erze; gesetzte Blöcke zählen
 * für das Insel-Level. Abbauen prüft der SkyblockListener (One-Block, Loot-Kisten).
 */
public final class IslandListener implements Listener {

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public IslandListener(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    private boolean inSkyblock(World w) {
        return addon.isSkyblockWorld(w);
    }

    private void deny(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last != null && now - last < 2000) return;
        lastWarn.put(p.getUniqueId(), now);
        p.sendActionBar(Msg.parse("<red>Das ist nicht deine Insel."));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!inSkyblock(e.getBlock().getWorld())) return;
        if (!addon.islands().canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(BlockPlaceEvent e) {
        if (!inSkyblock(e.getBlock().getWorld())) return;
        addon.islands().addPoints(e.getBlock().getLocation(), e.getBlockPlaced().getType(), 1);
        if (e.getBlockReplacedState().getType() != Material.AIR) {
            addon.islands().addPoints(e.getBlock().getLocation(), e.getBlockReplacedState().getType(), -1);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !inSkyblock(b.getWorld())) return;
        Player p = e.getPlayer();
        if (addon.loot().islandAt(b) != null || addon.oneblock().isOneBlock(b)) return;
        IslandService.Island i = addon.islands().islandAt(b.getLocation());
        if (i == null) {
            // außerhalb der Inseln: nur Feldtritte verhindern
            if (e.getAction() == Action.PHYSICAL && b.getType() == Material.FARMLAND && !p.hasPermission("kuby.admin")) e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.PHYSICAL && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        boolean interactable = b.getType().isInteractable();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !interactable && (e.getItem() == null || !e.getItem().getType().isBlock())) return;
        IslandService.Right need = rightFor(b, e.getAction() == Action.PHYSICAL);
        if (addon.islands().can(p, b.getLocation(), need)) return;
        e.setCancelled(true);
        if (e.getAction() != Action.PHYSICAL && e.getHand() == EquipmentSlot.HAND) deny(p);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (inSkyblock(e.getBlock().getWorld()) && !addon.islands().canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (inSkyblock(e.getBlock().getWorld()) && !addon.islands().canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    /** Welches Recht braucht ein Klick auf diesen Block? Kisten und Öfen: KISTEN; Türen, Knöpfe, Hebel, Druckplatten: TUEREN;
     *  alles andere (Werkbank, Betten, Redstone einstellen, Feld zertreten): BAUEN. */
    static IslandService.Right rightFor(Block b, boolean physical) {
        Material m = b.getType();
        if (physical) return org.bukkit.Tag.PRESSURE_PLATES.isTagged(m) || m == Material.TRIPWIRE ? IslandService.Right.TUEREN : IslandService.Right.BAUEN;
        if (b.getState(false) instanceof org.bukkit.inventory.InventoryHolder) return IslandService.Right.KISTEN;
        if (org.bukkit.Tag.DOORS.isTagged(m) || org.bukkit.Tag.TRAPDOORS.isTagged(m) || org.bukkit.Tag.FENCE_GATES.isTagged(m)
                || org.bukkit.Tag.BUTTONS.isTagged(m) || m == Material.LEVER) return IslandService.Right.TUEREN;
        return IslandService.Right.BAUEN;
    }

    /** Wer hat angegriffen (auch mit Pfeil oder Trank)? */
    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!inSkyblock(victim.getWorld())) return;
        Player p = attacker(e.getDamager());
        if (p == null) return;
        if (victim instanceof Player) {
            Location spawn = core.modes().spawnOf(ModeService.SKYBLOCK);
            int r = addon.getConfig().getInt("insel.pvp-spawn-radius", 40);
            boolean atSpawn = spawn != null && spawn.getWorld() == victim.getWorld() && spawn.distanceSquared(victim.getLocation()) < (double) r * r;
            if (!addon.getConfig().getBoolean("insel.pvp", true) || atSpawn) e.setCancelled(true);
            return;
        }
        if (victim instanceof Monster || victim instanceof Player) return;
        if (addon.islands().islandAt(victim.getLocation()) != null && !addon.islands().can(p, victim.getLocation(), IslandService.Right.TIERE)) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (!inSkyblock(en.getWorld()) || en instanceof org.bukkit.entity.Villager || en instanceof Player) return;
        IslandService.Right need = en instanceof org.bukkit.entity.Animals || en instanceof org.bukkit.entity.Vehicle
                ? IslandService.Right.TIERE : en instanceof org.bukkit.inventory.InventoryHolder ? IslandService.Right.KISTEN : IslandService.Right.BAUEN;
        if (addon.islands().islandAt(en.getLocation()) != null && !addon.islands().can(e.getPlayer(), en.getLocation(), need)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (inSkyblock(e.getRightClicked().getWorld()) && !addon.islands().canBuild(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent e) {
        if (!inSkyblock(e.getEntity().getWorld())) return;
        Player p = attacker(e.getRemover());
        if (p != null && !addon.islands().canBuild(p, e.getEntity().getLocation())) {
            e.setCancelled(true);
            deny(p);
        }
    }

    /** Betreten: gesperrte Spieler nie, geschlossene Inseln nur Mitglieder und Gäste. Nur beim Wechsel in eine andere Insel. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent e) {
        Location to = e.getTo(), from = e.getFrom();
        if (to.getBlockX() == from.getBlockX() && to.getBlockZ() == from.getBlockZ()) return;
        if (!inSkyblock(to.getWorld())) return;
        IslandService is = addon.islands();
        IslandService.Island target = is.islandAt(to);
        if (target == null || target == is.islandAt(from) || is.mayEnter(e.getPlayer(), target)) return;
        e.setTo(from.clone().setDirection(from.getDirection().multiply(-1)));
        blocked(e.getPlayer(), target);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null || !inSkyblock(to.getWorld())) return;
        IslandService.Island target = addon.islands().islandAt(to);
        if (target == null || addon.islands().mayEnter(e.getPlayer(), target)) return;
        e.setCancelled(true);
        blocked(e.getPlayer(), target);
    }

    private void blocked(Player p, IslandService.Island i) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(p.getUniqueId());
        if (last != null && now - last < 2000) return;
        lastWarn.put(p.getUniqueId(), now);
        p.sendActionBar(Msg.parse(i.banned.contains(p.getUniqueId()) ? "<red>Du bist auf dieser Insel gesperrt."
                : "<red>Diese Insel ist für Besucher geschlossen."));
    }

    /** Ins Leere gefallen: zurück nach Hause statt zu sterben. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.VOID || !(e.getEntity() instanceof Player p) || !inSkyblock(p.getWorld())) return;
        if (!addon.getConfig().getBoolean("insel.void-rettung", true)) return;
        e.setCancelled(true);
        p.setFallDistance(0);
        var stage = addon.stages() == null ? null : addon.stages().stageOf(p.getWorld());
        if (stage != null) {
            addon.stages().rescue(p, stage);
            Msg.send(p, "<gray>Du bist ins Leere gefallen und wieder auf deiner " + stage.label + "-Insel.");
            return;
        }
        IslandService.Island i = addon.islands().islandOf(p.getUniqueId());
        if (i != null) addon.islands().home(p, i);
        else {
            Location s = core.modes().spawnOf(ModeService.SKYBLOCK);
            if (s != null) p.teleportAsync(s);
        }
        Msg.send(p, "<gray>Du bist ins Leere gefallen und wieder zu Hause.");
    }

    /** Gesprengte Blöcke zählen nicht mehr (früher blieben die Punkte, der Block fiel als Item heraus: wieder setzen,
     *  wieder sprengen = Punkte ohne Ende). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        if (!inSkyblock(e.getLocation().getWorld())) return;
        for (Block b : e.blockList()) addon.islands().addPoints(b.getLocation(), b.getType(), -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(org.bukkit.event.block.BlockExplodeEvent e) {
        if (!inSkyblock(e.getBlock().getWorld())) return;
        for (Block b : e.blockList()) addon.islands().addPoints(b.getLocation(), b.getType(), -1);
    }

    /** Fallender Sand, Endermen, Wither usw.: alter Block weg, neuer Block dazu. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(org.bukkit.event.entity.EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player || !inSkyblock(e.getBlock().getWorld())) return;
        addon.islands().addPoints(e.getBlock().getLocation(), e.getBlock().getType(), -1);
        addon.islands().addPoints(e.getBlock().getLocation(), e.getTo(), 1);
    }

    /** Verbrannte Blöcke zählen nicht mehr. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(org.bukkit.event.block.BlockBurnEvent e) {
        if (inSkyblock(e.getBlock().getWorld())) addon.islands().addPoints(e.getBlock().getLocation(), e.getBlock().getType(), -1);
    }

    /** Generator: Bruchstein und Stein aus Lava und Wasser werden je Stufe der Insel zu anderen Blöcken. In der
     *  Nether-Stufe wird Basalt (Lava, Seelenerde, Blaueis) zu Nether-Blöcken, in der End-Stufe Bruchstein zu End-Blöcken. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) {
        Material m = e.getNewState().getType();
        if (!inSkyblock(e.getBlock().getWorld())) return;
        var stage = addon.stages() == null ? null : addon.stages().stageOf(e.getBlock().getWorld());
        if (stage != null) {
            boolean fits = stage == de.kuby.kubyskyblock.service.StageService.Stage.NETHER ? m == Material.BASALT
                    : m == Material.COBBLESTONE || m == Material.STONE;
            if (!fits) return;
            IslandService.Island i = addon.islands().islandAt(e.getBlock().getLocation());
            Material out = i == null ? null : addon.stages().generate(i, stage);
            if (out != null) e.getNewState().setType(out);
            return;
        }
        if (m != Material.COBBLESTONE && m != Material.STONE) return;
        IslandService.Island i = addon.islands().islandAt(e.getBlock().getLocation());
        if (i == null) return;
        Material out = addon.islands().generate(i);
        if (out != Material.COBBLESTONE) e.getNewState().setType(out);
    }
}
