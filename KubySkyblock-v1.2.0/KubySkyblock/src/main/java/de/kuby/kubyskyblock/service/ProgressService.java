package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fortschritt der Skyblock-Spieler: Checkliste aller Items, Erfolge, Meilensteine mit Belohnung. */
public final class ProgressService {

    public record Ach(String id, String name, String desc, String type, long target, long money, long xp, long jewels) {}

    private static final int[] MILESTONES = {10, 25, 50, 75, 100};

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private final List<Material> allItems = new ArrayList<>();
    private final List<Ach> achievements = new ArrayList<>();
    private final Map<UUID, Set<Material>> collected = new HashMap<>();
    private final Map<UUID, long[]> counters = new HashMap<>();   // [0]=Blöcke, [1]=One-Block, [2]=Loot
    private final Map<UUID, Set<Integer>> milestones = new HashMap<>();
    private final Map<UUID, Set<String>> achieved = new HashMap<>();

    public ProgressService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "progress.yml");
        for (Material m : Material.values()) if (m.isItem() && !m.isAir()) allItems.add(m);
        ConfigurationSection a = addon.getConfig().getConfigurationSection("achievements");
        if (a != null) {
            for (String id : a.getKeys(false)) {
                ConfigurationSection s = a.getConfigurationSection(id);
                if (s == null) continue;
                achievements.add(new Ach(id, s.getString("name", id), s.getString("desc", ""), s.getString("type", "blocks"),
                        s.getLong("target", 1), s.getLong("money", 0), s.getLong("xp", 0), s.getLong("jewels", 0)));
            }
        }
        load();
    }

    public List<Material> allItems() { return allItems; }
    public List<Ach> achievements() { return achievements; }
    public Set<Material> collected(UUID id) { return collected.getOrDefault(id, Set.of()); }
    public Set<String> achieved(UUID id) { return achieved.getOrDefault(id, Set.of()); }
    public long counter(UUID id, int i) { return counters.getOrDefault(id, new long[3])[i]; }

    public int percent(UUID id) {
        return allItems.isEmpty() ? 0 : collected(id).size() * 100 / allItems.size();
    }

    public void recordItem(Player p, Material m) {
        if (m == null || !m.isItem() || m.isAir()) return;
        Set<Material> set = collected.computeIfAbsent(p.getUniqueId(), k -> EnumSet.noneOf(Material.class));
        if (set.add(m)) {
            checkMilestones(p);
            checkAchievements(p);
        }
    }

    public void recordBlock(Player p) { bump(p, 0); }
    public void recordOneBlock(Player p) { bump(p, 1); }
    public void recordLoot(Player p) { bump(p, 2); }

    private void bump(Player p, int i) {
        counters.computeIfAbsent(p.getUniqueId(), k -> new long[3])[i]++;
        checkAchievements(p);
    }

    private void checkMilestones(Player p) {
        int pct = percent(p.getUniqueId());
        Set<Integer> done = milestones.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        for (int th : MILESTONES) {
            if (pct >= th && done.add(th)) {
                core.economy().deposit(p.getUniqueId(), 500L * th);
                core.stats().get(p.getUniqueId()).xp += 25L * th;
                Msg.send(p, "<gold>Checkliste " + th + "% erreicht! <gray>Belohnung in deiner Kiste.");
                addon.loot().giveRoll(p, "overpowered", 3);
                Sfx.play(core, p, Sfx.Kind.LEVEL_UP);
            }
        }
    }

    private void checkAchievements(Player p) {
        UUID id = p.getUniqueId();
        Set<String> done = achieved.computeIfAbsent(id, k -> new HashSet<>());
        for (Ach a : achievements) {
            if (done.contains(a.id())) continue;
            long value = switch (a.type()) {
                case "items" -> collected(id).size();
                case "oneblock" -> counter(id, 1);
                case "loot" -> counter(id, 2);
                default -> counter(id, 0);
            };
            if (value >= a.target()) {
                done.add(a.id());
                core.economy().deposit(id, a.money());
                core.stats().get(id).xp += a.xp();
                if (a.jewels() > 0) core.jewels().add(id, a.jewels());
                Msg.send(p, "<green>Erfolg: <white>" + a.name() + " <gray>- " + a.desc());
                Sfx.play(core, p, Sfx.Kind.EPIC);
            }
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        collected.forEach((id, set) -> {
            List<String> names = new ArrayList<>();
            for (Material m : set) names.add(m.name());
            out.set(id + ".collected", names);
        });
        counters.forEach((id, c) -> out.set(id + ".counters", List.of(c[0], c[1], c[2])));
        milestones.forEach((id, s) -> out.set(id + ".milestones", new ArrayList<>(s)));
        achieved.forEach((id, s) -> out.set(id + ".achieved", new ArrayList<>(s)));
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("progress.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            UUID id = UUID.fromString(key);
            Set<Material> set = EnumSet.noneOf(Material.class);
            for (String n : yml.getStringList(key + ".collected")) {
                try { set.add(Material.valueOf(n)); } catch (IllegalArgumentException ignored) { }
            }
            collected.put(id, set);
            List<Long> c = yml.getLongList(key + ".counters");
            if (c.size() == 3) counters.put(id, new long[] {c.get(0), c.get(1), c.get(2)});
            Set<Integer> ms = new HashSet<>(yml.getIntegerList(key + ".milestones"));
            milestones.put(id, ms);
            achieved.put(id, new HashSet<>(yml.getStringList(key + ".achieved")));
        }
    }
}
