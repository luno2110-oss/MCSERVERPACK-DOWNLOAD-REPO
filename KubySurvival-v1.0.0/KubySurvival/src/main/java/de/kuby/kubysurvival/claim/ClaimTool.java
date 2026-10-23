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

/** Claim-Schaufel: zwei Rechtsklicks markieren die Ecken. Danach /claim erstellen. Rahmen werden mit Partikeln gezeigt. */
public final class ClaimTool {

    private final KubySurvival addon;
    private final NamespacedKey tag;
    private final Map<UUID, Location[]> corners = new HashMap<>();

    public ClaimTool(KubySurvival addon) {
        this.addon = addon;
        this.tag = new NamespacedKey(addon, "claim_tool");
    }

    public ItemStack create() {
        ItemStack it = Items.make(Material.GOLDEN_SHOVEL, "<gold>Claim-Schaufel", List.of(
                "<gray>Rechtsklick auf einen Block: Ecke setzen", "<gray>Zwei Ecken, dann <white>/claim erstellen"));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isTool(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    /** Setzt abwechselnd Ecke 1 und Ecke 2 und zeigt Größe und Kosten. */
    public void mark(Player p, Location block) {
        Location[] c = corners.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
        if (c[0] == null || c[1] != null) {
            c[0] = block;
            c[1] = null;
            Msg.send(p, "<gold>Ecke 1 gesetzt. <gray>Jetzt die gegenüberliegende Ecke anklicken.");
            return;
        }
        c[1] = block;
        if (!c[0].getWorld().equals(c[1].getWorld())) {
            Msg.send(p, "<red>Beide Ecken müssen in derselben Welt liegen.");
            c[1] = null;
            return;
        }
        int w = Math.abs(c[0].getBlockX() - c[1].getBlockX()) + 1;
        int l = Math.abs(c[0].getBlockZ() - c[1].getBlockZ()) + 1;
        long free = addon.claims().available(p.getUniqueId());
        Msg.send(p, "<gold>Ecke 2 gesetzt. <white>" + w + " x " + l + " = " + (long) w * l + " Blöcke <gray>(frei: " + free + ")");
        Msg.send(p, "<click:run_command:'/claim erstellen'><green>[Jetzt claimen]</green></click> <gray>oder /claim erstellen");
        show(p, Math.min(c[0].getBlockX(), c[1].getBlockX()), Math.min(c[0].getBlockZ(), c[1].getBlockZ()),
                Math.max(c[0].getBlockX(), c[1].getBlockX()), Math.max(c[0].getBlockZ(), c[1].getBlockZ()));
    }

    public Location[] corners(UUID id) {
        Location[] c = corners.get(id);
        return c == null || c[0] == null || c[1] == null ? null : c;
    }

    public void forget(UUID id) {
        corners.remove(id);
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
