package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Battlepass mit zwei Spuren. FREE ist kostenlos und hat Belohnungen auf jeder dritten Stufe.
 * PREMIUM kostet Juwelen und hat Belohnungen auf jeder Stufe. Belohnungen stehen in der Liste battlepass.rewards.
 * Format: STUFE;SPUR;ART;WERT  (ART: COINS, XP, JEWELS, COMMAND, ITEM; bei ITEM z. B. NETHERITE_INGOT:5)
 */
public final class BattlepassService implements ModeData {

    public static final int MAX_LEVEL = 100;
    public static final String FREE = "FREE";
    public static final String PREMIUM = "PREMIUM";
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public record Quest(String id, String label, int target, int reward) {}

    public record Reward(int level, String track, String type, String value) {}

    private static final class Data {
        long xp;
        int claimedFree;
        int claimedPremium;
        boolean premium;
        String week = "";
        final Map<String, Integer> progress = new HashMap<>();
        final Set<String> done = new HashSet<>();
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Data> data = new HashMap<>();

    public BattlepassService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "battlepass.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            Data d = new Data();
            d.xp = yml.getLong(key + ".xp");
            d.claimedFree = yml.getInt(key + ".claimedFree", yml.getInt(key + ".claimed"));
            d.claimedPremium = yml.getInt(key + ".claimedPremium");
            d.premium = yml.getBoolean(key + ".premium");
            d.week = yml.getString(key + ".week", "");
            ConfigurationSection pr = yml.getConfigurationSection(key + ".progress");
            if (pr != null) {
                for (String q : pr.getKeys(false)) d.progress.put(q, pr.getInt(q));
            }
            for (String q : yml.getStringList(key + ".done")) d.done.add(q);
            data.put(UUID.fromString(key), d);
        }
    }

    public static String weekKey() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        return now.get(IsoFields.WEEK_BASED_YEAR) + "-W" + now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    public int xpPerLevel() {
        return Math.max(1, plugin.getConfig().getInt("battlepass.xp-per-level", 100));
    }

    public long premiumPrice() {
        return plugin.getConfig().getLong("battlepass.premium-price", 950);
    }

    public List<Quest> quests() {
        List<Quest> list = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("battlepass.quests")) {
            String[] p = raw.split(";");
            if (p.length < 4) continue;
            try {
                list.add(new Quest(p[0].trim(), p[1].trim(), Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())));
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ungültige Battlepass-Quest: " + raw);
            }
        }
        return list;
    }

    // ---------- Belohnungen ----------

    /** Alle Belohnungen. Ist die Liste in der config leer, gelten die Standardbelohnungen. */
    public List<Reward> rewards() {
        List<Reward> list = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("battlepass.rewards")) {
            String[] p = raw.split(";", 4);
            if (p.length < 4) continue;
            try {
                list.add(new Reward(Integer.parseInt(p[0].trim()), p[1].trim().toUpperCase(java.util.Locale.ROOT),
                        p[2].trim().toUpperCase(java.util.Locale.ROOT), p[3].trim()));
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Ungültige Battlepass-Belohnung: " + raw);
            }
        }
        return list.isEmpty() ? defaults() : list;
    }

    public List<Reward> rewardsAt(int level, String track) {
        List<Reward> out = new ArrayList<>();
        for (Reward r : rewards()) {
            if (r.level() == level && r.track().equals(track)) out.add(r);
        }
        return out;
    }

    private List<Reward> defaults() {
        String keyCmd = plugin.getConfig().getString("battlepass.key-command", "keys give %player% keyall 1");
        List<Reward> list = new ArrayList<>();
        for (int lvl = 1; lvl <= MAX_LEVEL; lvl++) {
            if (lvl % 3 == 0) list.add(new Reward(lvl, FREE, "COINS", String.valueOf(lvl * 50L)));
            if (lvl % 15 == 0) list.add(new Reward(lvl, FREE, "JEWELS", "5"));
            if (lvl % 30 == 0) list.add(new Reward(lvl, FREE, "XP", "500"));
            list.add(new Reward(lvl, PREMIUM, "COINS", String.valueOf(lvl * 100L)));
            if (lvl % 5 == 0) list.add(new Reward(lvl, PREMIUM, "XP", String.valueOf(lvl * 20L)));
            if (lvl % 10 == 0) list.add(new Reward(lvl, PREMIUM, "JEWELS", "25"));
            if (lvl % 20 == 0) list.add(new Reward(lvl, PREMIUM, "COMMAND", keyCmd));
            if (lvl == MAX_LEVEL) list.add(new Reward(lvl, PREMIUM, "ITEM", "NETHERITE_INGOT:5"));
        }
        return list;
    }

    // ---------- Stufen und Spuren ----------

    private Data data(UUID id) {
        return data.computeIfAbsent(id, k -> new Data());
    }

    private void rollWeek(Data d) {
        String wk = weekKey();
        if (!wk.equals(d.week)) {
            d.week = wk;
            d.progress.clear();
            d.done.clear();
        }
    }

    public synchronized long xp(UUID id) {
        return data(id).xp;
    }

    public synchronized int level(UUID id) {
        return (int) Math.min(MAX_LEVEL, data(id).xp / xpPerLevel());
    }

    public synchronized boolean hasPremium(UUID id) {
        return data(id).premium;
    }

    public synchronized int claimedOf(UUID id, String track) {
        Data d = data(id);
        return PREMIUM.equals(track) ? d.claimedPremium : d.claimedFree;
    }

    /** Gibt null bei Erfolg zurück. */
    public synchronized String buyPremium(Player p) {
        UUID id = p.getUniqueId();
        Data d = data(id);
        if (d.premium) return "Du hast den Premium-Pass schon.";
        long price = premiumPrice();
        if (!plugin.jewels().take(id, price)) return "Du brauchst " + price + " Juwelen für den Premium-Pass.";
        d.premium = true;
        save();
        return null;
    }

    /** Holt alle erreichten, noch nicht abgeholten Belohnungen einer Spur. Gibt die Anzahl der Stufen zurück. */
    public synchronized int claimTrack(Player p, String track) {
        UUID id = p.getUniqueId();
        Data d = data(id);
        int level = level(id);
        int from = PREMIUM.equals(track) ? d.claimedPremium : d.claimedFree;
        int count = 0;
        for (int lvl = from + 1; lvl <= level; lvl++) {
            for (Reward r : rewardsAt(lvl, track)) grant(p, r);
            count++;
        }
        if (PREMIUM.equals(track)) {
            d.claimedPremium = Math.max(d.claimedPremium, level);
        } else {
            d.claimedFree = Math.max(d.claimedFree, level);
        }
        if (count > 0) save();
        return count;
    }

    private void grant(Player p, Reward r) {
        UUID id = p.getUniqueId();
        try {
            switch (r.type()) {
                case "COINS" -> plugin.economy().deposit(id, Long.parseLong(r.value()));
                case "JEWELS" -> plugin.jewels().add(id, Long.parseLong(r.value()));
                case "XP" -> plugin.stats().get(id).xp += Long.parseLong(r.value());
                case "COMMAND" -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                        r.value().replace("%player%", p.getName()));
                case "ITEM" -> {
                    String[] parts = r.value().split(":");
                    Material m = Material.matchMaterial(parts[0].toUpperCase(java.util.Locale.ROOT));
                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    if (m != null) {
                        p.getInventory().addItem(new ItemStack(m, amount)).values()
                                .forEach(rest -> plugin.abholbox().add(id, rest));
                    }
                }
                default -> plugin.getLogger().warning("Unbekannte Battlepass-Art: " + r.type());
            }
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("Ungültiger Wert in Battlepass-Belohnung: " + r.value());
        }
    }

    /** Anzeige eines Belohnungs-Eintrags. */
    public static String describe(Reward r) {
        return switch (r.type()) {
            case "COINS" -> "<gold>" + r.value() + "$";
            case "XP" -> "<green>" + r.value() + " XP";
            case "JEWELS" -> "<aqua>" + r.value() + " Juwelen";
            case "COMMAND" -> "<light_purple>Spezial-Belohnung";
            case "ITEM" -> "<white>" + r.value().replace(':', ' ') + " Stück";
            default -> "<gray>" + r.value();
        };
    }

    public static Material iconOf(Reward r) {
        return switch (r.type()) {
            case "COINS" -> Material.GOLD_NUGGET;
            case "XP" -> Material.EXPERIENCE_BOTTLE;
            case "JEWELS" -> Material.EMERALD;
            case "COMMAND" -> Material.TRIPWIRE_HOOK;
            case "ITEM" -> {
                Material m = Material.matchMaterial(r.value().split(":")[0].toUpperCase(java.util.Locale.ROOT));
                yield m == null ? Material.CHEST : m;
            }
            default -> Material.PAPER;
        };
    }

    // ---------- Quests ----------

    public synchronized int progressOf(UUID id, String questId) {
        Data d = data(id);
        rollWeek(d);
        return d.progress.getOrDefault(questId, 0);
    }

    public synchronized boolean isDone(UUID id, String questId) {
        Data d = data(id);
        rollWeek(d);
        return d.done.contains(questId);
    }

    /** Fortschritt einer Quest. Bei Abschluss gibt es die BP-XP. */
    public synchronized void progress(UUID id, String questId, int amount) {
        Quest q = null;
        for (Quest candidate : quests()) {
            if (candidate.id().equals(questId)) q = candidate;
        }
        if (q == null) return;
        Data d = data(id);
        rollWeek(d);
        if (d.done.contains(questId)) return;

        int value = d.progress.merge(questId, amount, Integer::sum);
        if (value >= q.target()) {
            d.done.add(questId);
            d.xp += q.reward();
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Msg.send(p, "<gold>Quest abgeschlossen: <white>" + q.label() + " <gray>(+" + q.reward() + " BP-XP)");
                Sfx.play(plugin, p, Sfx.Kind.CHIME);
            }
        }
        save();
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        data.forEach((id, d) -> {
            String k = id.toString();
            out.set(k + ".xp", d.xp);
            out.set(k + ".claimedFree", d.claimedFree);
            out.set(k + ".claimedPremium", d.claimedPremium);
            out.set(k + ".premium", d.premium);
            out.set(k + ".week", d.week);
            d.progress.forEach((q, v) -> out.set(k + ".progress." + q, v));
            out.set(k + ".done", new ArrayList<>(d.done));
        });
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("battlepass.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    @Override
    public String key() { return "battlepass"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        Data d = data.get(id);
        if (d == null) return m;
        m.put("xp", d.xp);
        m.put("claimedFree", d.claimedFree);
        m.put("claimedPremium", d.claimedPremium);
        m.put("premium", d.premium);
        m.put("week", d.week);
        d.progress.forEach((q, v) -> m.put("progress|" + q, v));
        m.put("done", new java.util.ArrayList<>(d.done));
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            data.remove(id);
        } else {
            Data d = new Data();
            d.xp = ModeData.lng(m.get("xp"));
            d.claimedFree = ModeData.integer(m.get("claimedFree"));
            d.claimedPremium = ModeData.integer(m.get("claimedPremium"));
            d.premium = Boolean.TRUE.equals(m.get("premium"));
            Object w = m.get("week");
            d.week = w == null ? "" : w.toString();
            m.forEach((k, v) -> {
                if (k.startsWith("progress|")) d.progress.put(k.substring(9), ModeData.integer(v));
            });
            Object done = m.get("done");
            if (done instanceof java.util.List<?> l) {
                for (Object o : l) d.done.add(String.valueOf(o));
            }
            data.put(id, d);
        }
        save();
    }
}
