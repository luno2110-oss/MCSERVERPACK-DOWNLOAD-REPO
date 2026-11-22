package de.kuby.kubyplugins.listener;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Flächen-Werkzeuge aus dem XP-Shop (v2.05): 3x3- und 5x5-Spitzhacken (und -Schaufeln). Baut der Spieler einen Block
 * ab, gehen die Blöcke ringsum in der Ebene der getroffenen Seite mit (Blick nach unten/oben: waagerecht, sonst
 * senkrecht zur Blickrichtung).
 *
 * <p>Schutz: für jeden weiteren Block wird ein eigenes BlockBreakEvent ausgelöst; Grundstücke, Inselschutz,
 * OneBlock, Jobs, Battlepass und XP-Quellen sehen also jeden Block einzeln, und abgelehnte Blöcke bleiben stehen.
 * Nur Blöcke, für die das Werkzeug passt (isPreferredTool), keine unzerstörbaren Blöcke, keine Blöcke mit Inhalt
 * (Truhen, Spawner, Öfen …). Schleichen baut nur einen Block ab. Jeder weitere Block kostet Haltbarkeit
 * (Haltbarkeit-Verzauberung wirkt), das Werkzeug bricht nie mitten in der Fläche.
 */
public final class AreaToolListener implements Listener {

    /** Marke am Werkzeug: 3 oder 5. */
    public static final NamespacedKey KEY = NamespacedKey.fromString("kuby:area");

    private final KubyPlugins plugin;
    /** Spieler, deren Fläche gerade abgebaut wird (die ausgelösten Ereignisse nicht nochmal ausdehnen). */
    private final Set<UUID> busy = new HashSet<>();

    public AreaToolListener(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    /** Fläche des Werkzeugs (3, 5) oder 0. */
    public static int area(ItemStack tool) {
        if (tool == null || tool.getType().isAir() || !tool.hasItemMeta()) return 0;
        Integer v = tool.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER);
        return v == null || (v != 3 && v != 5) ? 0 : v;
    }

    /**
     * Blöcke der Fläche um center (ohne center), Ebene nach der getroffenen Seite. Reihenfolge: von innen nach außen.
     */
    public static List<Block> plane(Block center, BlockFace face, int size) {
        int r = size / 2;
        List<Block> out = new ArrayList<>();
        for (int ring = 1; ring <= r; ring++) {
            for (int a = -ring; a <= ring; a++) {
                for (int b = -ring; b <= ring; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                    Block x = switch (face) {
                        case UP, DOWN -> center.getRelative(a, 0, b);
                        case EAST, WEST -> center.getRelative(0, a, b);
                        default -> center.getRelative(a, b, 0);
                    };
                    out.add(x);
                }
            }
        }
        return out;
    }

    /** Getroffene Seite: aus dem Blick, sonst aus dem Neigungswinkel. */
    private static BlockFace face(Player p) {
        BlockFace f = null;
        try {
            f = p.getTargetBlockFace(6);
        } catch (RuntimeException ignored) {
            // ältere Server
        }
        if (f != null) return f;
        float pitch = p.getLocation().getPitch();
        if (pitch > 50) return BlockFace.UP;
        if (pitch < -50) return BlockFace.DOWN;
        return p.getFacing().getOppositeFace();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId()) || p.isSneaking() || p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        int size = area(tool);
        if (size == 0) return;
        Block center = e.getBlock();
        if (!center.isPreferredTool(tool)) return;
        BlockFace face = face(p);
        busy.add(p.getUniqueId());
        try {
            for (Block b : plane(center, face, size)) {
                ItemStack now = p.getInventory().getItemInMainHand();
                if (area(now) == 0) break;
                if (!breakable(b, now)) continue;
                if (almostBroken(now)) break;
                BlockBreakEvent ev = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                if (ev.isDropItems()) b.breakNaturally(now, true);
                else b.setType(Material.AIR);
                if (ev.getExpToDrop() > 0) {
                    b.getWorld().spawn(b.getLocation().add(0.5, 0.5, 0.5), org.bukkit.entity.ExperienceOrb.class,
                            o -> o.setExperience(ev.getExpToDrop()));
                }
                p.damageItemStack(EquipmentSlot.HAND, 1);
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    private static boolean breakable(Block b, ItemStack tool) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid() || m.getHardness() < 0 || !m.isSolid() && !b.isPreferredTool(tool)) return false;
        if (!b.isPreferredTool(tool)) return false;
        return !(b.getState(false) instanceof TileState);
    }

    /** Werkzeug fast kaputt: lieber aufhören, als es in der Fläche zu zerbrechen. */
    private static boolean almostBroken(ItemStack tool) {
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable d)) return false;
        int max = tool.getType().getMaxDurability();
        return max > 0 && d.getDamage() >= max - 2;
    }
}
