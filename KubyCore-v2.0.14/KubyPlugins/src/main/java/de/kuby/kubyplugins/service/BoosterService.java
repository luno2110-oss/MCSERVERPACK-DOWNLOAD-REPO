package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Abend-Booster: erhöht XP und Daily-Belohnungen zwischen start-hour und end-hour (Berliner Zeit). */
public final class BoosterService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final KubyPlugins plugin;

    public BoosterService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public boolean active() {
        int h = ZonedDateTime.now(ZONE).getHour();
        return h >= plugin.getConfig().getInt("booster.start-hour", 18)
                && h < plugin.getConfig().getInt("booster.end-hour", 24);
    }

    public double multiplier() {
        return active() ? plugin.getConfig().getDouble("booster.multiplier", 1.5) : 1.0;
    }
}
