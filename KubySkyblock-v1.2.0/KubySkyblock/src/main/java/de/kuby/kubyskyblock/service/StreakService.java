package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Belohnung pro abgebautem Block. Zwischen 18 und 24 Uhr (Berlin) steigt der Multiplikator
 * mit jedem Block der Serie: 1. Block x1,0, 2. Block x1,1, 3. Block x1,2 ... (+0,1 je Block).
 */
public final class StreakService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private static final class Streak {
        LocalDate day;
        int count;
    }

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<UUID, Streak> streaks = new HashMap<>();
    private final long baseMoney;
    private final long baseXp;

    public StreakService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "streaks.yml");
        this.baseMoney = addon.getConfig().getLong("reward.money", 2);
        this.baseXp = addon.getConfig().getLong("reward.xp", 1);
    }

    public static boolean inWindow() {
        int h = ZonedDateTime.now(ZONE).getHour();
        return h >= 18 && h < 24;
    }

    /** Belohnung für einen abgebauten Block. */
    public void reward(Player p) {
        Streak s = streaks.computeIfAbsent(p.getUniqueId(), k -> new Streak());
        LocalDate today = LocalDate.now(ZONE);
        if (s.day == null || !s.day.equals(today)) {
            s.day = today;
            s.count = 0;
        }
        double mult = 1.0;
        if (inWindow()) {
            s.count++;
            mult = 1.0 + 0.1 * (s.count - 1);
        } else {
            s.count = 0;
        }
        long money = Math.round(baseMoney * mult);
        long xp = Math.round(baseXp * mult);
        core.economy().deposit(p.getUniqueId(), money);
        core.stats().get(p.getUniqueId()).xp += xp;
        if (inWindow()) {
            p.sendActionBar(Msg.parse("<gold>Serie <white>x" + String.format(Locale.GERMANY, "%.1f", mult)
                    + " <dark_gray>| <green>+" + money + "$ <dark_gray>| <green>+" + xp + " XP"));
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        streaks.forEach((id, s) -> {
            out.set(id + ".day", s.day == null ? "" : s.day.toString());
            out.set(id + ".count", s.count);
        });
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("streaks.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
