package de.kuby.kubysurvival.farm;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Teleports;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Farmwelt: eigene Oberwelt nur zum Abbauen. Keine Grundstücke (die gibt es nur in der Survival-Welt), keine Homes,
 * keine Portale. Einmal pro Woche (farmwelt.reset-day / reset-hour) wird sie gelöscht und mit neuem Seed neu erstellt.
 * Vorher gibt es Warnungen, wer drin ist, landet am Survival-Spawn. Wer bei einem Reset offline in der Farmwelt war,
 * landet beim nächsten Einloggen ebenfalls am Spawn.
 */
public final class FarmWorldService implements Listener {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int[] WARN_MINUTES = {60, 30, 10, 5, 1};

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private long lastReset;
    private int generation;
    /** Spieler, die die Farmwelt beim Ausloggen in Generation x verlassen haben. */
    private final Map<UUID, Integer> leftIn = new HashMap<>();
    private int lastWarn = -1;
    private boolean resetting;

    public FarmWorldService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "farmwelt.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        lastReset = yml.getLong("last-reset", System.currentTimeMillis());
        generation = yml.getInt("generation", 1);
        var left = yml.getConfigurationSection("left-in");
        if (left != null) {
            for (String k : left.getKeys(false)) {
                try {
                    leftIn.put(UUID.fromString(k), left.getInt(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (enabled()) {
            World w = world();
            if (w == null) w = create(yml.getLong("seed", ThreadLocalRandom.current().nextLong()));
            if (w != null) save();
            addon.getServer().getScheduler().runTaskTimer(addon, this::tick, 1200L, 1200L);
        }
    }

    // ---------- Einstellungen ----------

    public boolean enabled() {
        return core.getConfig().getBoolean("farmwelt.enabled", true);
    }

    public String worldName() {
        return core.getConfig().getString("farmwelt.world", "farmwelt");
    }

    public World world() {
        return Bukkit.getWorld(worldName());
    }

    public boolean isFarm(World w) {
        return w != null && w.getName().equals(worldName());
    }

    private int border() {
        return Math.max(500, core.getConfig().getInt("farmwelt.border", 6000));
    }

    /** Nächster Reset nach dem letzten. */
    public ZonedDateTime nextReset() {
        DayOfWeek day;
        try {
            day = DayOfWeek.valueOf(core.getConfig().getString("farmwelt.reset-day", "MONDAY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            day = DayOfWeek.MONDAY;
        }
        int hour = Math.max(0, Math.min(23, core.getConfig().getInt("farmwelt.reset-hour", 4)));
        ZonedDateTime last = Instant.ofEpochMilli(lastReset).atZone(ZONE);
        ZonedDateTime next = last.with(TemporalAdjusters.nextOrSame(day)).withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(last)) next = next.plusWeeks(1);
        return next;
    }

    public static String left(Duration d) {
        if (d.isNegative()) return "gleich";
        long days = d.toDays();
        if (days > 0) return days + (days == 1 ? " Tag " : " Tage ") + d.toHoursPart() + " Std.";
        if (d.toHours() > 0) return d.toHours() + " Std. " + d.toMinutesPart() + " Min.";
        return Math.max(1, d.toMinutes()) + " Min.";
    }

    // ---------- Welt ----------

    private World create(long seed) {
        World w = new WorldCreator(worldName()).environment(World.Environment.NORMAL).seed(seed).createWorld();
        if (w == null) {
            addon.getLogger().warning("Farmwelt konnte nicht erstellt werden.");
            return null;
        }
        w.getWorldBorder().setCenter(0, 0);
        w.getWorldBorder().setSize(border());
        w.setSpawnLocation(0, w.getHighestBlockYAt(0, 0) + 1, 0);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        yml.set("seed", seed);
        try {
            yml.save(file);
        } catch (IOException ignored) {
        }
        return w;
    }

    private void tick() {
        if (resetting || !enabled()) return;
        Duration until = Duration.between(ZonedDateTime.now(ZONE), nextReset());
        long min = until.toMinutes();
        for (int warn : WARN_MINUTES) {
            if (min < warn && warn != lastWarn && min >= warn - 1) {
                lastWarn = warn;
                World w = world();
                if (w != null) {
                    for (Player p : w.getPlayers()) {
                        Msg.send(p, "<gold><b>Farmwelt:</b> <white>Neustart in " + warn + (warn == 1 ? " Minute" : " Minuten")
                                + "<gray>. Nimm deine Sachen mit!");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1f);
                    }
                }
            }
        }
        if (!until.isNegative() && !until.isZero()) return;
        reset();
    }

    /** Löscht die Farmwelt und erstellt sie mit neuem Seed neu. */
    public void reset() {
        resetting = true;
        try {
            World w = world();
            if (w != null) {
                for (Player p : w.getPlayers()) {
                    Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
                    if (spawn != null) p.teleport(spawn);
                    Msg.send(p, "<gold>Die Farmwelt wird neu erstellt. <gray>Du bist jetzt am Spawn.");
                }
                if (!Bukkit.unloadWorld(w, false)) {
                    addon.getLogger().warning("Farmwelt ließ sich nicht entladen, Reset verschoben.");
                    return;
                }
                deleteFolder(new File(Bukkit.getWorldContainer(), worldName()).toPath());
            }
            generation++;
            lastReset = System.currentTimeMillis();
            lastWarn = -1;
            create(ThreadLocalRandom.current().nextLong());
            save();
            addon.getLogger().info("Farmwelt neu erstellt (Generation " + generation + ").");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
                    Msg.send(p, "<green><b>Die Farmwelt ist frisch!</b> <gray>Neue Rohstoffe warten: <white>/farmwelt");
                }
            }
        } finally {
            resetting = false;
        }
    }

    private void deleteFolder(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    addon.getLogger().warning("Farmwelt: " + path + " ließ sich nicht löschen: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            addon.getLogger().warning("Farmwelt löschen: " + e.getMessage());
        }
    }

    // ---------- Spieler ----------

    /** /farmwelt: zufälliger sicherer Ort in der Farmwelt. */
    public void travel(Player p) {
        if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) {
            Msg.send(p, "<red>Die Farmwelt gehört zu Survival.");
            return;
        }
        World w = world();
        if (!enabled() || w == null || resetting) {
            Msg.send(p, "<red>Die Farmwelt ist gerade nicht erreichbar.");
            return;
        }
        int r = border() / 2 - 50;
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-r, r);
            int z = ThreadLocalRandom.current().nextInt(-r, r);
            w.getChunkAt(x >> 4, z >> 4).load(true);
            int y = w.getHighestBlockYAt(x, z);
            Material ground = w.getType(x, y, z);
            if (!ground.isSolid() || ground == Material.CACTUS || ground == Material.MAGMA_BLOCK
                    || ground.name().endsWith("LEAVES") || y < w.getSeaLevel() - 1) continue;
            Teleports.delayed(core, p, new Location(w, x + 0.5, y + 1, z + 0.5), "Farmwelt");
            return;
        }
        Msg.send(p, "<red>Kein sicherer Ort gefunden, versuch es nochmal.");
    }

    public void info(Player p) {
        ZonedDateTime next = nextReset();
        World w = world();
        Msg.send(p, "<aqua><b>Farmwelt</b>");
        Msg.send(p, "<gray>Zum Abbauen da: alles farmen erlaubt, keine Grundstücke, keine Homes.");
        Msg.send(p, "<gray>Neustart: <white>" + next.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM, HH:mm 'Uhr'", Locale.GERMAN))
                + " <gray>(in " + left(Duration.between(ZonedDateTime.now(ZONE), next)) + ")");
        Msg.send(p, "<gray>Größe: <white>" + border() + " x " + border() + " <gray>· Spieler drin: <white>"
                + (w == null ? 0 : w.getPlayers().size()));
        Msg.send(p, "<click:run_command:'/farmwelt'><green>[Hinreisen]</click>");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (isFarm(e.getFrom().getWorld())) {
            e.setCancelled(true);
            Msg.send(e.getPlayer(), "<red>Aus der Farmwelt führen keine Portale. <gray>Zurück mit <white>/spawn");
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        // Tod in der Farmwelt ohne Bett: zurück zum Survival-Spawn statt zum Welt-Spawn der Farmwelt.
        if (isFarm(e.getPlayer().getWorld()) && !e.isBedSpawn() && !e.isAnchorSpawn()) {
            Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
            if (spawn != null) e.setRespawnLocation(spawn);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (isFarm(e.getPlayer().getWorld())) leftIn.put(e.getPlayer().getUniqueId(), generation);
        else leftIn.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Integer gen = leftIn.remove(p.getUniqueId());
        if (gen != null && gen != generation && isFarm(p.getWorld())) {
            Location spawn = core.modes().spawnOf(ModeService.SURVIVAL);
            if (spawn == null) return;
            addon.getServer().getScheduler().runTask(addon, () -> {
                if (!p.isOnline()) return;
                p.teleport(spawn);
                Msg.send(p, "<gold>Die Farmwelt wurde inzwischen neu erstellt. <gray>Du bist jetzt am Spawn.");
            });
        }
    }

    public synchronized void save() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        yml.set("last-reset", lastReset);
        yml.set("generation", generation);
        yml.set("left-in", null);
        for (Map.Entry<UUID, Integer> e : leftIn.entrySet()) yml.set("left-in." + e.getKey(), e.getValue());
        try {
            addon.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("farmwelt.yml: " + e.getMessage());
        }
    }
}
