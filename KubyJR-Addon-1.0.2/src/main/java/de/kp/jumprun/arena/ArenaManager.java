package de.kp.jumprun.arena;

import de.kp.jumprun.KPJumpRun;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lädt und speichert alle Arenen in arenas.yml.
 */
public final class ArenaManager {

    private final KPJumpRun plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(KPJumpRun plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }

        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }

            Arena arena = new Arena(name);
            arena.setStart(section.getObject("start", Location.class));
            arena.setFinish(section.getObject("finish", Location.class));
            arena.setExit(section.getObject("exit", Location.class));

            for (Object entry : section.getList("checkpoints", List.of())) {
                if (entry instanceof Location location) {
                    arena.getCheckpoints().add(location);
                }
            }

            ConfigurationSection texts = section.getConfigurationSection("texts");
            if (texts != null) {
                for (String key : texts.getKeys(false)) {
                    arena.setText(key, texts.getString(key));
                }
            }

            ConfigurationSection sounds = section.getConfigurationSection("sounds");
            if (sounds != null) {
                for (String key : sounds.getKeys(false)) {
                    arena.setSound(key, sounds.getString(key));
                }
            }

            arenas.put(name, arena);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String base = "arenas." + arena.getName() + ".";
            yml.set(base + "start", arena.getStart());
            yml.set(base + "finish", arena.getFinish());
            yml.set(base + "exit", arena.getExit());
            yml.set(base + "checkpoints", arena.getCheckpoints());

            for (Map.Entry<String, String> entry : arena.textOverrides().entrySet()) {
                yml.set(base + "texts." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : arena.soundOverrides().entrySet()) {
                yml.set(base + "sounds." + entry.getKey(), entry.getValue());
            }
        }

        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("arenas.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public Arena get(String name) {
        return arenas.get(name);
    }

    public Arena getOrCreate(String name) {
        return arenas.computeIfAbsent(name, Arena::new);
    }

    public Collection<Arena> all() {
        return arenas.values();
    }
}
