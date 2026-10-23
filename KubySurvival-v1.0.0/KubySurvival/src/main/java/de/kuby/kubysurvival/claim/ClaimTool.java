package de.kuby.kubysurvival.claim;

import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Claim-Axt: Linksklick setzt Ecke 1, Rechtsklick Ecke 2, danach /claim ok.
 * Linksklick auf eine Ecke eines eigenen Grundstücks und danach Rechtsklick auf die neue Position ändert dessen Größe.
 * Rahmen werden mit Partikeln gezeigt.
 */
public final class ClaimTool {

    private final KubySurvival addon;
    private final NamespacedKey tag;
    private final Map<UUID, Location[]> corners = new HashMap<>();
    /** Spieler, die gerade eine Ecke eines Claims verschieben: Claim und die gegenüberliegende Ecke (x, z). */
    private final Map<UUID, Resize> resizing = new HashMap<>();

    private record Resize(ClaimService.Claim claim, int fixedX, int fixedZ) {}

    public ClaimTool(KubySurvival addon) {
        this.addon = addon;
        this.tag = new NamespacedKey(addon, "claim_tool");
    }

    public ItemStack create() {
        ItemStack it = Items.make(Material.GOLDEN_AXE, "<gold>Claim-Axt", List.of(
                "<gray>Linksklick: <white>Ecke 1", "<gray>Rechtsklick: <white>Ecke 2", "<gray>Danach <white>/claim ok", "",
                "<gray>Größe ändern: Linksklick auf eine Ecke", "<gray>deines Grundstücks, dann Rechtsklick", "<gray>auf die neue Position."));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isTool(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    /** Linksklick: Ecke eines eigenen Claims greifen oder Ecke 1 setzen. */
    public void left(Player p, Location block) {
        ClaimService.Claim c = addon.claims().at(block);
        if (c != null && c.owner().equals(p.getUniqueId())) {
            int x = block.getBlockX(), z = block.getBlockZ();
            boolean cornerX = x == c.x1() || x == c.x2(), cornerZ = z == c.z1() || z == c.z2();
            if (cornerX && cornerZ) {
                resizing.put(p.getUniqueId(), new Resize(c, x == c.x1() ? c.x2() : c.x1(), z == c.z1() ? c.z2() : c.z1()));
                corners.remove(p.getUniqueId());
                Msg.send(p, "<gold>Ecke gegriffen. <gray>Rechtsklick auf die neue Position, um das Grundstück zu verändern.");
                show(p, c.x1(), c.z1(), c.x2(), c.z2());
                return;
            }
        }
        resizing.remove(p.getUniqueId());
        Location[] cs = corners.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
        cs[0] = block;
        cs[1] = null;
        Msg.send(p, "<gold>Ecke 1 gesetzt. <gray>Jetzt Rechtsklick auf die gegenüberliegende Ecke.");
    }

    /** Rechtsklick: Größe ändern (wenn eine Ecke gegriffen ist) oder Ecke 2 setzen. */
    public void right(Player p, Location block) {
        Resize r = resizing.remove(p.getUniqueId());
        if (r != null) {
            if (!block.getWorld().getName().equals(r.claim().world())) {
                Msg.send(p, "<red>Die neue Ecke muss in derselben Welt liegen.");
                return;
            }
            String err = addon.claims().resize(p, r.claim(), r.fixedX(), r.fixedZ(), block.getBlockX(), block.getBlockZ());
            if (err != null) {
                Msg.send(p, "<red>" + err);
                return;
            }
            ClaimService.Claim now = addon.claims().at(block);
            Msg.send(p, "<green>Grundstück angepasst. <gray>Freie Claimblöcke: <white>" + addon.claims().available(p.getUniqueId()));
            if (now != null) show(p, now.x1(), now.z1(), now.x2(), now.z2());
            return;
        }
        Location[] c = corners.get(p.getUniqueId());
        if (c == null || c[0] == null) {
            Msg.send(p, "<gray>Setze zuerst mit <white>Linksklick</white> die erste Ecke.");
            return;
        }
        if (!c[0].getWorld().equals(block.getWorld())) {
            Msg.send(p, "<red>Beide Ecken müssen in derselben Welt liegen.");
            return;
        }
        c[1] = block;
        int w = Math.abs(c[0].getBlockX() - c[1].getBlockX()) + 1;
        int l = Math.abs(c[0].getBlockZ() - c[1].getBlockZ()) + 1;
        long free = addon.claims().available(p.getUniqueId());
        Msg.send(p, "<gold>Ecke 2 gesetzt. <white>" + w + " x " + l + " = " + (long) w * l + " Blöcke <gray>(frei: " + free + ")");
        Msg.send(p, "<click:run_command:'/claim ok'><green>[Jetzt sichern]</green></click> <gray>oder /claim ok");
        show(p, Math.min(c[0].getBlockX(), c[1].getBlockX()), Math.min(c[0].getBlockZ(), c[1].getBlockZ()),
                Math.max(c[0].getBlockX(), c[1].getBlockX()), Math.max(c[0].getBlockZ(), c[1].getBlockZ()));
    }

    public Location[] corners(UUID id) {
        Location[] c = corners.get(id);
        return c == null || c[0] == null || c[1] == null ? null : c;
    }

    public void forget(UUID id) {
        corners.remove(id);
        resizing.remove(id);
    }

    /** Zeigt den Rand eines Rechtecks 10 Sekunden lang mit Partikeln (nur für diesen Spieler). */
    public void show(Player p, int x1, int z1, int x2, int z2) {
        for (int t = 0; t < 10; t++) {
            addon.getServer().getScheduler().runTaskLater(addon, () -> {
                if (!p.isOnline()) return;
                double y = p.getLocation().getY() + 1;
                for (int x = x1; x <= x2 + 1; x += 2) {
                    p.spawnParticle(Particle.HAPPY_VILLAGER, x, y, z1, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.HAPPY_VILLAGER, x, y, z2 + 1, 1, 0, 0, 0, 0);
                }
                for (int z = z1; z <= z2 + 1; z += 2) {
                    p.spawnParticle(Particle.HAPPY_VILLAGER, x1, y, z, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.HAPPY_VILLAGER, x2 + 1, y, z, 1, 0, 0, 0, 0);
                }
            }, t * 20L);
        }
    }
}
