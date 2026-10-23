package de.kuby.kubysurvival.claim;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Schutz der Claims (mit Freigaben für Fremde), Claim-Axt und Hinweis beim Betreten eines Grundstücks. */
public final class ClaimListener implements Listener {

    /** Blöcke, die man benutzen kann und die deshalb geschützt sind (zusätzlich zu allem mit Inventar). */
    private static final Set<Material> USABLE = Set.of(Material.LEVER, Material.REPEATER, Material.COMPARATOR,
            Material.NOTE_BLOCK, Material.JUKEBOX, Material.CAKE, Material.BEACON, Material.LECTERN,
            Material.RESPAWN_ANCHOR, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.COMPOSTER,
            Material.CHISELED_BOOKSHELF, Material.DECORATED_POT, Material.DAYLIGHT_DETECTOR, Material.BELL,
            Material.CAULDRON, Material.WATER_CAULDRON, Material.LAVA_CAULDRON, Material.POWDER_SNOW_CAULDRON,
            Material.SWEET_BERRY_BUSH, Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.FARMLAND,
            Material.TURTLE_EGG, Material.SNIFFER_EGG, Material.DRAGON_EGG, Material.VAULT, Material.CRAFTER);

    /** Items, mit denen man in fremden Claims Blöcke verändern könnte. */
    private static final Set<Material> RISKY_ITEMS = Set.of(Material.FLINT_AND_STEEL, Material.FIRE_CHARGE,
            Material.BONE_MEAL, Material.ARMOR_STAND, Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME, Material.PAINTING,
            Material.END_CRYSTAL, Material.LEAD, Material.BRUSH, Material.HONEYCOMB, Material.SHEARS,
            Material.INK_SAC, Material.GLOW_INK_SAC, Material.MINECART, Material.TNT_MINECART, Material.HOPPER_MINECART,
            Material.CHEST_MINECART, Material.FURNACE_MINECART);

    private final KubySurvival addon;
    private final KubyPlugins core;

    public ClaimListener(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
    }

    private ClaimService claims() {
        return addon.claims();
    }

    private boolean deny(Player p, Location l) {
        if (claims().canBuild(p, l)) return false;
        ClaimService.Claim c = claims().at(l);
        String owner = c == null ? null : Bukkit.getOfflinePlayer(c.owner()).getName();
        p.sendActionBar(Msg.parse("<red>Das gehört " + (owner == null ? "jemand anderem" : owner) + "."));
        return true;
    }

    // ---------- Bauen ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (deny(e.getPlayer(), e.getBlockClicked().getLocation())) e.setCancelled(true);
    }

    // ---------- Benutzen ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        // Claim-Axt: Linksklick Ecke 1 bzw. Ecke greifen, Rechtsklick Ecke 2 bzw. neue Position.
        if ((e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && e.getHand() == EquipmentSlot.HAND && b != null && addon.claimTool().isTool(e.getItem())) {
            e.setCancelled(true);
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) addon.claimTool().left(p, b.getLocation());
            else addon.claimTool().right(p, b.getLocation());
            return;
        }
        if (b == null) return;
        if (e.getAction() == Action.PHYSICAL) {
            // Ackerboden zertrampeln, Druckplatten, Stolperdraht. Druckplatten kann der Besitzer freigeben.
            if (Tag.PRESSURE_PLATES.isTagged(b.getType()) && allowed(b)) return;
            if (deny(p, b.getLocation())) e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (claims().canBuild(p, b.getLocation())) return;
        if (usable(b)) {
            if (allowed(b) && !risky(e.getItem())) return;
            deny(p, b.getLocation());
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            e.setCancelled(true);
            return;
        }
        if (risky(e.getItem())) {
            deny(p, b.getLocation());
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    /** Hat der Besitzer diesen Block für Fremde freigegeben (Türen, Knöpfe/Hebel, Kisten)? */
    private boolean allowed(Block b) {
        ClaimService.Claim c = claims().at(b.getLocation());
        if (c == null) return true;
        Material m = b.getType();
        String flag = null;
        if (Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m)) flag = "tueren";
        else if (Tag.BUTTONS.isTagged(m) || Tag.PRESSURE_PLATES.isTagged(m) || m == Material.LEVER) flag = "knoepfe";
        else if (b.getState(false) instanceof Container || Tag.SHULKER_BOXES.isTagged(m)) flag = "kisten";
        return flag != null && claims().allows(c.owner(), flag);
    }

    private boolean animalsAllowed(Entity t) {
        if (!(t instanceof Animals)) return false;
        ClaimService.Claim c = claims().at(t.getLocation());
        return c != null && claims().allows(c.owner(), "tiere");
    }

    private boolean usable(Block b) {
        Material m = b.getType();
        return USABLE.contains(m) || Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m)
                || Tag.BUTTONS.isTagged(m) || Tag.ANVIL.isTagged(m) || Tag.BEDS.isTagged(m) || Tag.FLOWER_POTS.isTagged(m)
                || Tag.ALL_SIGNS.isTagged(m) || Tag.CANDLES.isTagged(m) || Tag.CANDLE_CAKES.isTagged(m)
                || Tag.PRESSURE_PLATES.isTagged(m) || Tag.SHULKER_BOXES.isTagged(m)
                || b.getState(false) instanceof Container;
    }

    private boolean risky(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        String n = m.name();
        return RISKY_ITEMS.contains(m) || n.endsWith("_SPAWN_EGG") || n.endsWith("_BOAT") || n.endsWith("_RAFT")
                || n.endsWith("_HOE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_DYE");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (!(t instanceof Hanging || t instanceof ArmorStand || t instanceof Animals)) return;
        if (animalsAllowed(t)) return;
        if (deny(e.getPlayer(), t.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageEntity(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!(victim instanceof Hanging || victim instanceof ArmorStand || victim instanceof Animals)) return;
        Player attacker = attacker(e.getDamager());
        if (attacker == null) {
            // Explosionen und Ähnliches treffen in Claims keine Tiere, Rahmen oder Rüstungsständer.
            if (claims().at(victim.getLocation()) != null) e.setCancelled(true);
            return;
        }
        if (deny(attacker, victim.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = attacker(e.getRemover());
        if (p != null) {
            if (deny(p, e.getEntity().getLocation())) e.setCancelled(true);
        } else if (claims().at(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingExplode(HangingBreakEvent e) {
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION && claims().at(e.getEntity().getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---------- Umwelt ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> claims().at(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> claims().at(b.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (claims().at(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (claims().at(e.getBlock().getLocation()) == null) return;
        if (e.getPlayer() != null) {
            if (deny(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
        } else if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            e.setCancelled(true);
        }
    }

    /** Wasser und Lava dürfen nicht von außen in einen Claim fließen. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        ClaimService.Claim to = claims().at(e.getToBlock().getLocation());
        if (to != null && !Objects.equals(to, claims().at(e.getBlock().getLocation()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (crosses(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (crosses(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    /** True, wenn ein Kolben Blöcke in einen anderen Claim schiebt oder aus ihm zieht. */
    private boolean crosses(Block piston, List<Block> moved, BlockFace dir) {
        ClaimService.Claim home = claims().at(piston.getLocation());
        ClaimService.Claim head = claims().at(piston.getRelative(dir).getLocation());
        if (!Objects.equals(home, head)) return true;
        for (Block b : moved) {
            if (!Objects.equals(home, claims().at(b.getLocation()))) return true;
            if (!Objects.equals(home, claims().at(b.getRelative(dir).getLocation()))) return true;
        }
        return false;
    }

    // ---------- Hinweise ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        ClaimService.Claim from = claims().at(e.getFrom());
        ClaimService.Claim to = claims().at(e.getTo());
        if (Objects.equals(from, to)) return;
        if (to == null) {
            e.getPlayer().sendActionBar(Msg.parse("<gray>Wildnis"));
        } else {
            String owner = Bukkit.getOfflinePlayer(to.owner()).getName();
            e.getPlayer().sendActionBar(Msg.parse("<green>Grundstück von <white>" + (owner == null ? "?" : owner)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        addon.claimTool().forget(e.getPlayer().getUniqueId());
    }
}
