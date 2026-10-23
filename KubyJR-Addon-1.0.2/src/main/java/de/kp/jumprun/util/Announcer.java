package de.kp.jumprun.util;

import de.kp.jumprun.arena.Arena;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Gibt Text und Sound einer Arena für ein Ereignis aus.
 */
public final class Announcer {

    private Announcer() {
    }

    public static void announce(Player player, Arena arena, String key, Map<String, String> vars) {
        if (arena == null) {
            return;
        }

        String text = arena.text(key);
        Map<String, String> all = new HashMap<>(vars);
        all.put("arena", arena.getName());
        for (Map.Entry<String, String> var : all.entrySet()) {
            text = text.replace("{" + var.getKey() + "}", var.getValue());
        }
        if (!text.isBlank()) {
            Text.send(player, text);
        }

        String sound = arena.sound(key);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1f, 1f);
        }
    }
}
