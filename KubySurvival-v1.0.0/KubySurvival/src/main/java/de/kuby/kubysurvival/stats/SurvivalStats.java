package de.kuby.kubysurvival.stats;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Zähler für /stats in Survival: abgebaute Blöcke, Tode und besiegte Monster. Gespeichert in stats.yml. */
public final class SurvivalStats implements Listener {

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<UUID, long[]> data = new HashMap<>();   // 0 abgebaut, 1 gestorben, 2 Monster
    private boolean dirty;

    public SurvivalStats(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "stats.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                data.put(UUID.fromString(k), new long[]{y.getLong(k + ".abgebaut"), y.getLong(k + ".gestorben"), y.getLong(k + ".monster")});
            } catch (IllegalArgumentException ignored) {
            }
        }
        core.registerStat("blocks_mined", id -> get(id, 0));
        core.registerStat("deaths_survival", id -> get(id, 1));
        core.registerStat("mobs_killed", id -> get(id, 2));
        addon.getServer().getScheduler().runTaskTimer(addon, () -> {
            if (dirty) save();
        }, 6000L, 6000L);
    }

    public synchronized long get(UUID id, int i) {
        long[] d = data.get(id);
        return d == null ? 0 : d[i];
    }

    private synchronized void add(UUID id, int i) {
        data.computeIfAbsent(id, k -> new long[3])[i]++;
        dirty = true;
    }

    private boolean counts(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL && ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (counts(e.getPlayer())) add(e.getPlayer().getUniqueId(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (ModeService.SURVIVAL.equals(core.modes().modeOf(e.getEntity().getUniqueId()))) add(e.getEntity().getUniqueId(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && e.getEntity() instanceof Enemy && counts(k)) add(k.getUniqueId(), 2);
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        data.forEach((id, d) -> {
            y.set(id + ".abgebaut", d[0]);
            y.set(id + ".gestorben", d[1]);
            y.set(id + ".monster", d[2]);
        });
        try {
            addon.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            addon.getLogger().warning("stats.yml: " + ex.getMessage());
        }
    }
}
