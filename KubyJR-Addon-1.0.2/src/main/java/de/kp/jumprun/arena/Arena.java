package de.kp.jumprun.arena;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine Parkour-Strecke mit Start, Ziel, Checkpoints sowie eigenen Texten und Sounds.
 * Alle Punkte werden als Block-Position gespeichert.
 */
public final class Arena {

    /** Ereignisse, für die man Texte und Sounds anpassen kann. */
    public static final List<String> TEXT_KEYS = List.of("start", "checkpoint", "finish", "fail-start", "fail-checkpoint");

    public static final Map<String, String> DEFAULT_TEXTS = Map.of(
            "start", "Run gestartet, viel Erfolg!",
            "checkpoint", "Checkpoint <yellow>{index}/{total}</yellow> erreicht · <white>{time}</white>",
            "finish", "<green>Ziel erreicht!</green> Zeit: <white>{time}</white>",
            "fail-start", "<red>Gefallen! Der Run beginnt von vorne.</red>",
            "fail-checkpoint", "<red>Gefallen! Zurück zum Checkpoint.</red>");

    public static final Map<String, String> DEFAULT_SOUNDS = Map.of(
            "start", "minecraft:block.note_block.pling",
            "checkpoint", "minecraft:entity.experience_orb.pickup",
            "finish", "minecraft:ui.toast.challenge_complete",
            "fail-start", "minecraft:entity.villager.no",
            "fail-checkpoint", "minecraft:entity.villager.no");

    private final String name;
    private Location start;
    private Location finish;
    private Location exit;
    private final List<Location> checkpoints = new ArrayList<>();
    private final Map<String, String> textOverrides = new HashMap<>();
    private final Map<String, String> soundOverrides = new HashMap<>();

    public Arena(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Location getStart() {
        return start;
    }

    public void setStart(Location start) {
        this.start = start;
    }

    public Location getFinish() {
        return finish;
    }

    public void setFinish(Location finish) {
        this.finish = finish;
    }

    /** Rückkehr-Punkt: hier steht der Spieler nach dem Ziel. */
    public Location getExit() {
        return exit;
    }

    public void setExit(Location exit) {
        this.exit = exit;
    }

    public List<Location> getCheckpoints() {
        return checkpoints;
    }

    public boolean isComplete() {
        return start != null && finish != null;
    }

    /** Text für ein Ereignis, eigene Einstellung oder Standard. */
    public String text(String key) {
        return textOverrides.getOrDefault(key, DEFAULT_TEXTS.getOrDefault(key, ""));
    }

    /** Eigenen Text setzen, null stellt den Standard wieder her. */
    public void setText(String key, String value) {
        if (value == null) {
            textOverrides.remove(key);
        } else {
            textOverrides.put(key, value);
        }
    }

    public Map<String, String> textOverrides() {
        return textOverrides;
    }

    /** Sound-Einstellung roh, "none" bedeutet aus. */
    public String soundSetting(String key) {
        return soundOverrides.getOrDefault(key, DEFAULT_SOUNDS.getOrDefault(key, "none"));
    }

    public void setSound(String key, String value) {
        soundOverrides.put(key, value);
    }

    public Map<String, String> soundOverrides() {
        return soundOverrides;
    }

    /** Abspielbarer Sound-Key oder null, wenn der Sound aus ist. */
    public String sound(String key) {
        String value = soundSetting(key);
        return value == null || value.isEmpty() || value.equals("none") ? null : value;
    }
}
