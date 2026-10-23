package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.List;

/** Einstellungen der Hub-Welt: Border, Tageszeit, Wetter, Musik und Begrüßung. Werden beim Start und bei Änderungen angewendet. */
public final class HubSettingsService {

    public static final List<String> WEATHER = List.of("CLEAR", "RAIN", "THUNDER");
    public static final List<Integer> TIMES = List.of(1000, 6000, 12000, 18000);

    private final KubyPlugins plugin;

    public HubSettingsService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public double border() { return plugin.getConfig().getDouble("hub-settings.border-size", 60); }
    public int time() { return plugin.getConfig().getInt("hub-settings.time", 6000); }
    public boolean timeLock() { return plugin.getConfig().getBoolean("hub-settings.time-lock", true); }
    public String weather() { return plugin.getConfig().getString("hub-settings.weather", "CLEAR").toUpperCase(java.util.Locale.ROOT); }
    public boolean weatherLock() { return plugin.getConfig().getBoolean("hub-settings.weather-lock", true); }
    public String music() { return plugin.getConfig().getString("hub-settings.music", ""); }
    public String joinText() { return plugin.getConfig().getString("hub-settings.join-text", "<gold>Willkommen in der Lobby!"); }

    public void set(String key, Object value) {
        plugin.getConfig().set("hub-settings." + key, value);
        plugin.saveConfig();
    }

    public World world() {
        return Bukkit.getWorld(plugin.modes().worldOf(ModeService.HUB));
    }

    /** Wendet alle Einstellungen auf die Hub-Welt an. */
    public void applyAll() {
        World w = world();
        if (w == null) return;
        WorldBorder b = w.getWorldBorder();
        b.setSize(border());
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !timeLock());
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, !weatherLock());
        w.setTime(time());
        applyWeather(w);
    }

    private void applyWeather(World w) {
        switch (weather()) {
            case "RAIN" -> { w.setStorm(true); w.setThundering(false); }
            case "THUNDER" -> { w.setStorm(true); w.setThundering(true); }
            default -> { w.setStorm(false); w.setThundering(false); }
        }
    }

    /** Begrüßung beim Betreten der Hub-Welt. */
    public void welcome(Player p) {
        String m = music();
        if (!m.isBlank()) p.playSound(p.getLocation(), m, SoundCategory.MUSIC, 1f, 1f);
        String t = joinText();
        if (!t.isBlank()) Msg.send(p, t);
    }
}
