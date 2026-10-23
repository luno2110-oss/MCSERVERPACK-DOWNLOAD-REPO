package de.kp.jumprun.reward;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.util.Text;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

/**
 * Der Belohnungs-NPC. Er ist ein Dorfbewohner ohne KI, der per Rechtsklick das Belohnungsmenü öffnet.
 * Die zugehörige Arena wird im Entity selbst gespeichert, daher braucht es keine eigene Datei.
 */
public final class RewardNpc {

    private final NamespacedKey key;

    public RewardNpc(KPJumpRun plugin) {
        this.key = new NamespacedKey(plugin, "reward_npc");
    }

    public Villager spawn(Location location, String arena) {
        World world = location.getWorld();
        return world.spawn(location, Villager.class, npc -> {
            npc.customName(Text.item("Belohnungen"));
            npc.setCustomNameVisible(true);
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            npc.setPersistent(true);
            npc.setCollidable(false);
            npc.setProfession(Villager.Profession.NONE);
            npc.getPersistentDataContainer().set(key, PersistentDataType.STRING, arena);
        });
    }

    public boolean isNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    public String arenaOf(Entity entity) {
        return entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** Setzt die NPCs in der Nähe auf die Mitte ihres Blocks. Gibt die Anzahl zurück. */
    public int centerNear(Location location, double radius) {
        World world = location.getWorld();
        int moved = 0;
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (!isNpc(entity)) {
                continue;
            }
            Location current = entity.getLocation();
            Location centered = new Location(world,
                    current.getBlockX() + 0.5,
                    current.getBlockY(),
                    current.getBlockZ() + 0.5,
                    current.getYaw(),
                    current.getPitch());
            entity.teleport(centered);
            moved++;
        }
        return moved;
    }

    /** Entfernt alle Belohnungs-NPCs in der Nähe. Gibt die Anzahl zurück. */
    public int removeNear(Location location, double radius) {
        World world = location.getWorld();
        int removed = 0;
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (isNpc(entity)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }
}
