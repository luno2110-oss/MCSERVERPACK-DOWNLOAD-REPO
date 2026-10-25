package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Items;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drei Bereiche auf dem Server: Hub (Lobby), SMP (PvP) und Survival (ruhig).
 * Jeder Modus hat eigene Daten: Inventar, Geld, XP, Trophäen, Kills, Tode, Enderchest und Abholbox.
 * Juwelen bleiben über alle Modi gleich. Beim Wechseln wird der aktuelle Stand gespeichert und der andere geladen.
 */
public final class ModeService {

    public static final String HUB = "hub";
    public static final String SMP = "smp";
    public static final String SURVIVAL = "survival";
    public static final String SKYBLOCK = "skyblock";

    public record Snap(long money, long xp, int trophies, int kills, int deaths, ItemStack[] inv,
                       EnderChestService.State ec, List<ItemStack> ab, Map<String, Map<String, Object>> extras) {}

    private static final int INV_SIZE = 41;

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, String> current = new HashMap<>();
    private final Map<UUID, Map<String, Snap>> snaps = new HashMap<>();
    private final List<ModeData> extras = new ArrayList<>();

    public ModeService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "profiles.yml");
        load();
    }

    // ---------- Welt und Spawn ----------

    public String worldOf(String mode) {
        return plugin.getConfig().getString("modes." + mode + ".world", mode);
    }

    public boolean isHub(World w) {
        return w != null && w.getName().equals(worldOf(HUB));
    }

    public Location spawnOf(String mode) {
        Location loc = Locs.parse(plugin.getConfig().getString("modes." + mode + ".spawn"));
        if (loc != null) return loc;
        World w = Bukkit.getWorld(worldOf(mode));
        return w == null ? null : w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    /** Addons melden hier Aktivitäten, bei denen die Lobby-Regeln für den Spieler aussetzen (z. B. ein Run). */
    private final List<java.util.function.Predicate<org.bukkit.entity.Player>> activities = new ArrayList<>();

    public void registerActivity(java.util.function.Predicate<org.bukkit.entity.Player> check) {
        activities.add(check);
    }

    public boolean hasActivity(org.bukkit.entity.Player p) {
        for (java.util.function.Predicate<org.bukkit.entity.Player> c : activities) {
            if (c.test(p)) return true;
        }
        return false;
    }

    public synchronized String modeOf(UUID id) {
        return current.getOrDefault(id, HUB);
    }

    // ---------- Wechsel ----------

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String switchTo(Player p, String target) {
        UUID id = p.getUniqueId();
        String from = modeOf(id);
        Location loc = spawnOf(target);
        if (loc == null) return "Der Spawn für diesen Modus ist noch nicht eingerichtet.";
        if (from.equals(target)) {
            p.teleportAsync(loc);
            return null;
        }
        if (busy(p)) return "Du kannst den Modus gerade nicht wechseln.";

        plugin.edit().forget(p);
        capture(p, from);
        current.put(id, target);
        apply(p, target);
        if (HUB.equals(from)) plugin.visibility().clear(p);
        Location before = p.getLocation();
        p.teleportAsync(loc);
        plugin.teleportFx().play(p, before);
        plugin.teleportFx().play(p, loc);
        if (HUB.equals(target)) plugin.hubSettings().welcome(p);
        fireEnter(target, p);
        Msg.send(p, "<gray>Du bist jetzt in <white>" + label(target) + "<gray>.");
        save();
        return null;
    }

    /** Prüfungen, ob ein Spieler gerade nicht wechseln darf (Addons können hier eigene eintragen). */
    private final List<java.util.function.Predicate<org.bukkit.entity.Player>> busyChecks = new ArrayList<>();

    public void registerBusyCheck(java.util.function.Predicate<org.bukkit.entity.Player> check) {
        busyChecks.add(check);
    }

    private boolean busy(Player p) {
        for (java.util.function.Predicate<org.bukkit.entity.Player> c : busyChecks) {
            if (c.test(p)) return true;
        }
        return false;
    }

    /** Hooks für Addons, die beim Betreten eines Modus etwas tun (z. B. Skyblock-Insel anlegen). */
    private final java.util.Map<String, java.util.function.Consumer<Player>> enterHooks = new java.util.HashMap<>();

    public void onEnter(String mode, java.util.function.Consumer<Player> hook) {
        enterHooks.put(mode, hook);
    }

    private void fireEnter(String mode, Player p) {
        java.util.function.Consumer<Player> hook = enterHooks.get(mode);
        if (hook != null) hook.accept(p);
    }

    public static String label(String mode) {
        return switch (mode) {
            case SMP -> "SMP";
            case SURVIVAL -> "Survival";
            case SKYBLOCK -> "Skyblock";
            default -> "Hub";
        };
    }

    // ---------- Geld und Items für einen bestimmten Modus ----------

    private Snap emptySnap() {
        return new Snap(0, 0, 100, 0, 0, new ItemStack[INV_SIZE], null, new ArrayList<>(), new HashMap<>());
    }

    /**
     * Zahlt Geld in einem bestimmten Modus aus. Ist der Spieler online in diesem Modus, geht es direkt aufs Konto.
     * Sonst landet es im gespeicherten Stand des Modus (wird beim nächsten Betreten geladen). So landet z. B. ein
     * Markt-Verkauf in Survival nicht im SMP-Konto und geht beim Einloggen nicht verloren.
     */
    public synchronized void depositIn(UUID id, String mode, long amount) {
        if (amount == 0) return;
        Player online = Bukkit.getPlayer(id);
        boolean live = mode.equals(modeOf(id));
        if (live) plugin.economy().deposit(id, amount);
        if (live && online != null) return;
        Map<String, Snap> byMode = snaps.computeIfAbsent(id, k -> new HashMap<>());
        Snap sn = byMode.getOrDefault(mode, emptySnap());
        byMode.put(mode, new Snap(sn.money() + amount, sn.xp(), sn.trophies(), sn.kills(), sn.deaths(), sn.inv(), sn.ec(),
                sn.ab(), sn.extras()));
        save();
    }

    /** Geld eines Spielers in einem Modus: live vom Konto, sonst aus dem gespeicherten Stand (auch offline). */
    public synchronized long moneyIn(UUID id, String mode) {
        if (mode.equals(modeOf(id)) && Bukkit.getPlayer(id) != null) return plugin.economy().balance(id);
        Snap s = snaps.getOrDefault(id, Map.of()).get(mode);
        return s == null ? 0 : s.money();
    }

    /** Legt ein Item in die Abholbox eines bestimmten Modus (gleiche Regeln wie depositIn). */
    public synchronized void abholboxIn(UUID id, String mode, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Player online = Bukkit.getPlayer(id);
        boolean live = mode.equals(modeOf(id));
        if (live) plugin.abholbox().add(id, item.clone());
        if (live && online != null) return;
        Map<String, Snap> byMode = snaps.computeIfAbsent(id, k -> new HashMap<>());
        Snap sn = byMode.getOrDefault(mode, emptySnap());
        List<ItemStack> ab = new ArrayList<>(sn.ab());
        ab.add(item.clone());
        byMode.put(mode, new Snap(sn.money(), sn.xp(), sn.trophies(), sn.kills(), sn.deaths(), sn.inv(), sn.ec(), ab, sn.extras()));
        save();
    }

    // ---------- Join und Quit ----------

    public synchronized void onJoin(Player p) {
        UUID id = p.getUniqueId();
        if (!current.containsKey(id)) {
            // Bestehende Spieler werden dem SMP zugeordnet, damit ihr Fortschritt erhalten bleibt
            if (hasLegacyData(p)) {
                capture(p, SMP);
                current.put(id, SMP);
            } else {
                current.put(id, HUB);
            }
            save();
        }
        String mode = modeOf(id);
        if (!HUB.equals(mode) && spawnOf(HUB) != null && plugin.playerSettings() != null
                && !plugin.playerSettings().get(id, "reconnect")) {
            // Auto-Neuverbindung aus: in die Lobby. Der Stand im Modus (so wie er beim Ausloggen war) bleibt gespeichert.
            capture(p, mode);
            current.put(id, HUB);
            mode = HUB;
            save();
        }
        apply(p, mode);
        Location loc = spawnOf(mode);
        if (loc != null) p.teleportAsync(loc);
        if (HUB.equals(mode)) plugin.hubSettings().welcome(p);
        fireEnter(mode, p);
    }

    public synchronized void onQuit(Player p) {
        capture(p, modeOf(p.getUniqueId()));
        save();
    }

    private boolean hasLegacyData(Player p) {
        UUID id = p.getUniqueId();
        var st = plugin.stats().get(id);
        if (plugin.economy().balance(id) > 0 || st.xp > 0 || st.kills > 0 || st.deaths > 0 || st.trophies != 100) {
            return true;
        }
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.getType().isAir()) return true;
        }
        return false;
    }

    // ---------- Speichern und Laden eines Modus ----------

    private void capture(Player p, String mode) {
        UUID id = p.getUniqueId();
        var st = plugin.stats().get(id);
        ItemStack[] inv = p.getInventory().getContents().clone();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null) inv[i] = inv[i].clone();
        }
        Snap s = new Snap(plugin.economy().balance(id), st.xp, st.trophies, st.kills, st.deaths, inv,
                plugin.enderchests().exportState(id), plugin.abholbox().items(id), exportExtras(id));
        snaps.computeIfAbsent(id, k -> new HashMap<>()).put(mode, s);
    }

    private void apply(Player p, String mode) {
        UUID id = p.getUniqueId();
        Snap s = snaps.getOrDefault(id, Map.of()).get(mode);
        if (s == null) s = new Snap(0, 0, 100, 0, 0, new ItemStack[INV_SIZE], null, new ArrayList<>(), new HashMap<>());

        plugin.economy().set(id, s.money());
        var st = plugin.stats().get(id);
        st.xp = s.xp();
        st.trophies = s.trophies();
        st.kills = s.kills();
        st.deaths = s.deaths();
        plugin.stats().save();

        p.getInventory().clear();
        p.getInventory().setContents(s.inv().clone());
        plugin.enderchests().importState(id, s.ec());
        plugin.abholbox().importItems(id, s.ab());
        importExtras(id, s.extras());

        if (HUB.equals(mode)) lobbyItems(p);
    }

    /** Lobby-Ausrüstung: Kompass in der Mitte, Farbstoff ganz rechts. */
    public void lobbyItems(Player p) {
        p.getInventory().clear();
        refreshLobby(p);
    }

    public void refreshLobby(Player p) {
        if (!isHub(p.getWorld())) return;
        p.getInventory().setItem(4, Items.make(Material.COMPASS, "<gold>Modus-Wahl", List.of(
                "<gray>Klick: <white>Modus wählen")));
        p.getInventory().setItem(8, plugin.visibility().dyeItem(p.getUniqueId()));
        if (p.hasPermission("kuby.admin")) {
            p.getInventory().setItem(0, Items.make(Material.NETHER_STAR, "<light_purple>Bearbeitungsmodus", List.of(
                    "<gray>Rechtsklick: Hub bearbeiten")));
        }
    }

    // ---------- Speicherung ----------

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            UUID id = UUID.fromString(key);
            String cur = yml.getString(key + ".current");
            if (cur != null) current.put(id, cur);
            ConfigurationSection sec = yml.getConfigurationSection(key);
            if (sec == null) continue;
            for (String mode : sec.getKeys(false)) {
                String base = key + "." + mode;
                if (yml.contains(base + ".money")) {
                    snaps.computeIfAbsent(id, k -> new HashMap<>()).put(mode, readSnap(yml, base));
                }
            }
        }
    }

    private Snap readSnap(YamlConfiguration yml, String base) {
        ItemStack[] inv = new ItemStack[INV_SIZE];
        ConfigurationSection is = yml.getConfigurationSection(base + ".inv");
        if (is != null) {
            for (String k : is.getKeys(false)) {
                int idx = Integer.parseInt(k);
                Object o = is.get(k);
                if (idx >= 0 && idx < INV_SIZE && o instanceof ItemStack it) inv[idx] = it;
            }
        }
        boolean[] unlocked = new boolean[45];
        for (int i : yml.getIntegerList(base + ".ec.unlocked")) {
            if (i >= 0 && i < unlocked.length) unlocked[i] = true;
        }
        ItemStack[] ecItems = new ItemStack[45];
        ConfigurationSection es = yml.getConfigurationSection(base + ".ec.items");
        if (es != null) {
            for (String k : es.getKeys(false)) {
                int idx = Integer.parseInt(k);
                Object o = es.get(k);
                if (idx >= 0 && idx < ecItems.length && o instanceof ItemStack it) ecItems[idx] = it;
            }
        }
        List<ItemStack> ab = new ArrayList<>();
        ConfigurationSection as = yml.getConfigurationSection(base + ".ab");
        if (as != null) {
            for (String k : as.getKeys(false)) {
                Object o = as.get(k);
                if (o instanceof ItemStack it) ab.add(it);
            }
        }
        return new Snap(yml.getLong(base + ".money"), yml.getLong(base + ".xp"),
                yml.getInt(base + ".trophies", 100), yml.getInt(base + ".kills"), yml.getInt(base + ".deaths"),
                inv, new EnderChestService.State(unlocked, ecItems), ab, readExtras(yml, base));
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        current.forEach((id, mode) -> out.set(id + ".current", mode));
        snaps.forEach((id, byMode) -> byMode.forEach((mode, s) -> writeSnap(out, id + "." + mode, s)));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("profiles.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void writeSnap(YamlConfiguration out, String base, Snap s) {
        out.set(base + ".money", s.money());
        out.set(base + ".xp", s.xp());
        out.set(base + ".trophies", s.trophies());
        out.set(base + ".kills", s.kills());
        out.set(base + ".deaths", s.deaths());
        for (int i = 0; i < s.inv().length; i++) {
            if (s.inv()[i] != null) out.set(base + ".inv." + i, s.inv()[i]);
        }
        List<Integer> unl = new ArrayList<>();
        if (s.ec() != null) {
            for (int i = 0; i < s.ec().unlocked().length; i++) {
                if (s.ec().unlocked()[i]) unl.add(i);
            }
            for (int i = 0; i < s.ec().items().length; i++) {
                if (s.ec().items()[i] != null) out.set(base + ".ec.items." + i, s.ec().items()[i]);
            }
        }
        out.set(base + ".ec.unlocked", unl);
        for (int i = 0; i < s.ab().size(); i++) out.set(base + ".ab." + i, s.ab().get(i));
        writeExtras(out, base, s.extras());
    }

    /** Registriert alle Daten, die pro Modus getrennt sind. */
    public void registerExtras(List<ModeData> list) {
        extras.addAll(list);
    }

    private Map<String, Map<String, Object>> exportExtras(UUID id) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (ModeData d : extras) out.put(d.key(), d.export(id));
        return out;
    }

    private void importExtras(UUID id, Map<String, Map<String, Object>> data) {
        for (ModeData d : extras) d.importData(id, data == null ? null : data.get(d.key()));
    }

    private Map<String, Map<String, Object>> readExtras(YamlConfiguration yml, String base) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        for (ModeData d : extras) {
            ConfigurationSection cs = yml.getConfigurationSection(base + ".x." + d.key());
            if (cs == null) continue;
            Map<String, Object> m = new HashMap<>();
            for (String k : cs.getKeys(false)) m.put(k, cs.get(k));
            out.put(d.key(), m);
        }
        return out;
    }

    private void writeExtras(YamlConfiguration out, String base, Map<String, Map<String, Object>> data) {
        if (data == null) return;
        for (Map.Entry<String, Map<String, Object>> e : data.entrySet()) {
            ConfigurationSection cs = out.createSection(base + ".x." + e.getKey());
            e.getValue().forEach(cs::set);
        }
    }
}
