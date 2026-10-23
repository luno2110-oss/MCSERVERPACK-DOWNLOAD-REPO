package de.kp.jumprun.reward;

import de.kp.jumprun.KPJumpRun;
import de.kp.jumprun.stats.StatsManager;
import de.kp.jumprun.stats.StatsManager.Record;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.StatsService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prüft, welche Belohnungsstufen ein Spieler abholen kann, und speichert abgeholte Stufen (rewards.yml).
 * Coins, Juwelen und XP werden direkt über die Konten von KubyPlugins gutgeschrieben.
 */
public final class RewardManager {

    public enum State { LOCKED, AVAILABLE, CLAIMED }

    private final KPJumpRun plugin;
    private final StatsManager stats;
    private final KubyPlugins kuby;
    private final File file;
    /** Schlüssel "uuid|arena" → abgeholte Stufen in Sekunden */
    private final Map<String, Set<Integer>> claims = new HashMap<>();

    public RewardManager(KPJumpRun plugin, StatsManager stats, KubyPlugins kuby) {
        this.plugin = plugin;
        this.stats = stats;
        this.kuby = kuby;
        this.file = new File(plugin.getDataFolder(), "rewards.yml");
    }

    public List<RewardTier> tiers() {
        return RewardTier.DEFAULTS;
    }

    public void load() {
        claims.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("claims");
        if (root == null) {
            return;
        }

        for (String uuid : root.getKeys(false)) {
            ConfigurationSection player = root.getConfigurationSection(uuid);
            if (player == null) {
                continue;
            }
            for (String arena : player.getKeys(false)) {
                claims.put(key(UUID.fromString(uuid), arena), new HashSet<>(player.getIntegerList(arena)));
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Set<Integer>> entry : claims.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            yml.set("claims." + parts[0] + "." + parts[1], new ArrayList<>(entry.getValue()));
        }

        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("rewards.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Beste Zeit in Millisekunden, -1 wenn noch keine Zeit vorhanden ist. */
    public long bestMillis(UUID uuid, String arena) {
        Record record = stats.records(arena).get(uuid);
        return record != null && record.hasTime() ? record.bestMillis() : -1;
    }

    public State state(UUID uuid, String arena, RewardTier tier) {
        Set<Integer> done = claims.get(key(uuid, arena));
        if (done != null && done.contains(tier.seconds())) {
            return State.CLAIMED;
        }

        long best = bestMillis(uuid, arena);
        if (best >= 0 && best <= tier.seconds() * 1000L) {
            return State.AVAILABLE;
        }
        return State.LOCKED;
    }

    /** Holt eine Stufe ab. Gibt false zurück, wenn sie nicht verfügbar ist. */
    public boolean claim(Player player, String arena, RewardTier tier) {
        UUID id = player.getUniqueId();
        if (state(id, arena, tier) != State.AVAILABLE) {
            return false;
        }

        claims.computeIfAbsent(key(id, arena), k -> new HashSet<>()).add(tier.seconds());
        save();

        if (tier.coins() > 0) {
            kuby.economy().deposit(id, tier.coins());
            kuby.economy().save();
        }
        if (tier.gems() > 0) {
            kuby.jewels().add(id, tier.gems());
        }
        if (tier.xp() > 0) {
            StatsService.Stats st = kuby.stats().get(id);
            st.xp += tier.xp();
            kuby.stats().save();
        }
        return true;
    }

    private static String key(UUID uuid, String arena) {
        return uuid + "|" + arena;
    }
}
