package de.kp.jumprun.run;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Der aktuelle Lauf eines Spielers.
 */
public final class Run {

    private final String arena;
    private long startMillis;
    private int lastGroundBlockY;
    private final Set<Integer> checkpointsReached = new HashSet<>();
    private final Map<Integer, ItemStack> savedSlots = new HashMap<>();
    private int lastCheckpoint = -1;

    public Run(String arena, int groundBlockY) {
        this.arena = arena;
        this.lastGroundBlockY = groundBlockY;
        this.startMillis = System.currentTimeMillis();
    }

    public String getArena() {
        return arena;
    }

    public long elapsed() {
        return System.currentTimeMillis() - startMillis;
    }

    /** Stoppuhr zurücksetzen und Checkpoints vergessen. */
    public void resetTimer() {
        startMillis = System.currentTimeMillis();
        checkpointsReached.clear();
        lastCheckpoint = -1;
    }

    /** Index des zuletzt erreichten Checkpoints, -1 wenn noch keiner erreicht wurde. */
    public int lastCheckpoint() {
        return lastCheckpoint;
    }

    public int getLastGroundBlockY() {
        return lastGroundBlockY;
    }

    public void setLastGroundBlockY(int y) {
        this.lastGroundBlockY = y;
    }

    public boolean hasReached(int checkpointIndex) {
        return checkpointsReached.contains(checkpointIndex);
    }

    public void markReached(int checkpointIndex) {
        checkpointsReached.add(checkpointIndex);
        if (checkpointIndex > lastCheckpoint) {
            lastCheckpoint = checkpointIndex;
        }
    }

    public void saveSlot(int slot, ItemStack item) {
        savedSlots.put(slot, item);
    }

    public Map<Integer, ItemStack> savedSlots() {
        return savedSlots;
    }
}
