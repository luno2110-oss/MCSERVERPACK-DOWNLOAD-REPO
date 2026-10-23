package de.kuby.kubyplugins.util;

import de.kuby.kubyplugins.KubyPlugins;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Soundeffekte. Jede Art hat eine eigene Klangfolge: positiv (hell, aufsteigend),
 * negativ (dumpf, absteigend) oder besonders (mehrteilig mit Verzögerung).
 * Ein- und ausschalten über sfx.enabled in der config.yml oder im Einstellungs-Menü.
 */
public final class Sfx {

    public enum Kind {
        // Oberfläche
        CLICK, OPEN, TICK,
        // Positiv
        SUCCESS, PING, COIN, CHIME, LEVEL_UP, SUCCESS_BIG, TRADE,
        // Negativ
        ERROR, FAIL, DEFEAT, DRUM_LOW, DEATH,
        // Besondere Momente
        JACKPOT, HORN, VICTORY, EPIC, BOUNTY, KILL, TELEPORT, QUIZ, WHOOSH,
        // Aliase für bestehende Aufrufe
        DUEL_START, WIN, LOSE
    }

    private record Step(Sound sound, float volume, float pitch, int delay) {}

    private Sfx() {}

    public static void play(KubyPlugins plugin, Player p, Kind kind) {
        play(plugin, p, kind, 1.0f);
    }

    /** pitchMul verschiebt die Tonhöhe, z. B. für einen aufsteigenden Countdown. */
    public static void play(KubyPlugins plugin, Player p, Kind kind, float pitchMul) {
        if (p == null || !plugin.getConfig().getBoolean("sfx.enabled", true)) return;
        for (Step s : steps(kind)) {
            float pitch = s.pitch() * pitchMul;
            if (s.delay() <= 0) {
                p.playSound(p.getLocation(), s.sound(), s.volume(), pitch);
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) p.playSound(p.getLocation(), s.sound(), s.volume(), pitch);
                }, s.delay());
            }
        }
    }

    private static List<Step> steps(Kind kind) {
        return switch (kind) {
            case CLICK -> List.of(new Step(Sound.UI_BUTTON_CLICK, 0.5f, 1.0f, 0));
            case OPEN -> List.of(new Step(Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1.2f, 0));
            case TICK -> List.of(new Step(Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, 1.0f, 0));

            case SUCCESS -> List.of(new Step(Sound.BLOCK_BELL_USE, 0.8f, 1.2f, 0));
            case PING -> List.of(new Step(Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f, 0));
            case COIN -> List.of(
                    new Step(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f, 0),
                    new Step(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 2.0f, 1));
            case CHIME -> List.of(
                    new Step(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.0f, 0),
                    new Step(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f, 3));
            case LEVEL_UP -> List.of(new Step(Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.0f, 0));
            case SUCCESS_BIG -> List.of(new Step(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f, 0));
            case TRADE -> List.of(
                    new Step(Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f, 0),
                    new Step(Sound.BLOCK_CHEST_CLOSE, 0.7f, 0.9f, 2));

            case ERROR -> List.of(
                    new Step(Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f, 0),
                    new Step(Sound.ENTITY_VILLAGER_NO, 0.6f, 0.9f, 2));
            case FAIL -> List.of(
                    new Step(Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f, 0),
                    new Step(Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f, 4));
            case DEFEAT, LOSE -> List.of(
                    new Step(Sound.ENTITY_VILLAGER_HURT, 0.8f, 0.8f, 0),
                    new Step(Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.5f, 3));
            case DRUM_LOW -> List.of(new Step(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.6f, 0));
            case DEATH -> List.of(new Step(Sound.ENTITY_PLAYER_DEATH, 0.6f, 1.0f, 0));

            case JACKPOT -> List.of(
                    new Step(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f, 0),
                    new Step(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f, 2),
                    new Step(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f, 4),
                    new Step(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.6f, 1.0f, 6));
            case HORN, DUEL_START -> List.of(new Step(Sound.EVENT_RAID_HORN, 0.8f, 1.0f, 0));
            case VICTORY, WIN -> List.of(
                    new Step(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f, 0),
                    new Step(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f, 3));
            case EPIC -> List.of(
                    new Step(Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.0f, 0),
                    new Step(Sound.ITEM_TOTEM_USE, 0.6f, 1.0f, 4));
            case BOUNTY -> List.of(
                    new Step(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.8f, 0.8f, 0),
                    new Step(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.7f, 5));
            case KILL -> List.of(
                    new Step(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f, 0),
                    new Step(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f, 1));
            case TELEPORT -> List.of(
                    new Step(Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f, 0),
                    new Step(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f, 2));
            case QUIZ -> List.of(
                    new Step(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.8f, 0),
                    new Step(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f, 3),
                    new Step(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.3f, 6));
            case WHOOSH -> List.of(new Step(Sound.UI_LOOM_SELECT_PATTERN, 0.6f, 1.5f, 0));
        };
    }
}
