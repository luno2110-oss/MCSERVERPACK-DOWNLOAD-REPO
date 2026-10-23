package de.kp.jumprun.holo;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Bestenlisten-Hologramm. Der Zustand der Animation liegt hier, die Logik in HoloManager.
 */
public final class Holo {

    /** SHOW: Liste steht, Leiste läuft ab. HIDE: Zeilen verschwinden. REVEAL: Zeilen erscheinen. */
    enum Phase { SHOW, HIDE, REVEAL }

    /** Persönliche Anzeige für den eigenen Rang, wenn man unter den Top 10 steht. */
    static final class Personal {
        final UUID displayId;
        final Set<UUID> hiddenFrom = new HashSet<>();

        Personal(UUID displayId) {
            this.displayId = displayId;
        }
    }

    private final String name;
    private String arena;
    private Location location;
    private double scale = 1.0;
    private int seconds = 10;

    UUID boardId;
    Category category = Category.BEST_TIME;
    Phase phase = Phase.SHOW;
    int phaseTicks;
    int stepTicks;
    int visibleRows;
    double barFraction = 1.0;
    final Map<UUID, Personal> personal = new HashMap<>();

    public Holo(String name, String arena, Location location) {
        this.name = name;
        this.arena = arena;
        this.location = location;
    }

    public String name() {
        return name;
    }

    public String arena() {
        return arena;
    }

    public void arena(String arena) {
        this.arena = arena;
    }

    public Location location() {
        return location;
    }

    void location(Location location) {
        this.location = location;
    }

    public double scale() {
        return scale;
    }

    public void scale(double scale) {
        this.scale = scale;
    }

    public int seconds() {
        return seconds;
    }

    public void seconds(int seconds) {
        this.seconds = seconds;
    }

    UUID boardId() {
        return boardId;
    }
}
