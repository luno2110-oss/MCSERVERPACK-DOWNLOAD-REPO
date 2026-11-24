package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.bedrock.Bedrock;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.ui.UiDialog;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Glücksräder (v2.07, Wunsch des Besitzers: „ein stehendes großes Rad mit Lichteffekten und SFX, die Nadel bewegt sich
 * mit, alles interaktiv, damit man wiederkommen möchte“, Gewinne größer, dafür alle 24 h; dazu ein Rad für SMP als
 * Fenster).
 *
 * <ul>
 *   <li><b>casino</b>: das große Rad auf der Bühne im Casino (WheelStage, Rechtsklick) und dasselbe Rad als Fenster
 *       (/gluecksrad in Survival). Beide teilen sich die Wartezeit.</li>
 *   <li><b>smp</b>: nur als Fenster (/gluecksrad in SMP), Gewinne in Geld, XP, Schlüsseln und Boostern.</li>
 * </ul>
 *
 * <p>Ablauf: das Ergebnis wird nach Gewicht gezogen und sofort gespeichert (Wartezeit, Serie), das Rad holt kurz aus,
 * dreht 6 bis 9 Sekunden auslaufend genau auf dieses Feld (Wheel.plan), die Stifte schlagen die Nadel weg (Klack),
 * Lauflichter, Scheinwerfer, Lichtblöcke und Töne laufen mit. Serie: wer innerhalb von serie-stunden wieder dreht,
 * verlängert sie, jeder gold-dreh-te Dreh in Folge zahlt doppelt. Im Fenster läuft das Rad als Bild über den
 * Bildschirm (Schrift kuby:wheel, siehe WheelScreen), danach öffnet sich das Fenster mit dem Gewinn.
 *
 * <p>Daten in gluecksrad.yml: raeder (Bühnen), zeit / serie je Rad und Spieler, gewinner je Rad (letzte fünf).
 */
public final class WheelService implements Listener {

    private static final SecureRandom RNG = new SecureRandom();
    /** Ausholen vor dem Dreh: Ticks und Grad zurück. */
    private static final int WINDUP = 14;
    private static final double WINDUP_DEG = 10;
    /** Nach dem Halt: so lange bleibt das Ergebnis stehen, bevor ausgezahlt und gefeiert wird. */
    private static final int SETTLE = 8;
    private static final int WINNERS = 5;

    static final class Spin {
        final UUID player;
        final String name;
        final String wheel;
        final int target;
        final Wheel.Field field;
        final String prize;
        final boolean gold;
        final boolean jackpot;
        final int streak;
        final boolean ui;
        final boolean text;
        final double from;
        final double[] plan;
        int t;
        int lastField = -1;
        double prev;
        double[] needle = {0, 0};

        Spin(UUID player, String name, String wheel, int target, Wheel.Field field, String prize, boolean gold,
             boolean jackpot, int streak, boolean ui, boolean text, double from, double[] plan) {
            this.player = player;
            this.name = name;
            this.wheel = wheel;
            this.target = target;
            this.field = field;
            this.prize = prize;
            this.gold = gold;
            this.jackpot = jackpot;
            this.streak = streak;
            this.ui = ui;
            this.text = text;
            this.from = from;
            this.plan = plan;
            this.prev = from;
        }

        /** Winkel bei Tick t: erst ausholen (zurück), dann der geplante Lauf. */
        double angle() {
            if (t <= WINDUP) return from - WINDUP_DEG * (1 - Math.cos(Math.PI * t / WINDUP)) / 2;
            int i = Math.min(t - WINDUP, plan.length - 1);
            return plan[i];
        }

        boolean stopped() {
            return t - WINDUP >= plan.length - 1;
        }

        boolean done() {
            return t - WINDUP >= plan.length - 1 + SETTLE;
        }
    }

    private static final class Stage {
        final WheelStage st;
        Spin spin;
        int winBlink;
        boolean bigWin;
        /** Jemand in der Nähe hat seinen Dreh bereit: Lichter lebhafter, Aufforderung blinkt. */
        boolean waiting;
        /** Leerlauf: das Rad dreht sich langsam weiter (Winkel je Tick, NaN = neu vom Rad übernehmen), Nadel federt mit. */
        double idleAngle = Double.NaN;
        double[] idleNeedle = {0, 0};
        int idleHold;

        Stage(WheelStage st) {
            this.st = st;
        }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final NamespacedKey click;
    private final Map<String, String> raw = new LinkedHashMap<>();
    private final Map<String, Stage> stages = new LinkedHashMap<>();
    private final Map<String, Map<UUID, Long>> last = new HashMap<>();
    private final Map<String, Map<UUID, Integer>> streak = new HashMap<>();
    private final Map<String, List<String>> winners = new HashMap<>();
    private final Map<String, Set<UUID>> reminded = new HashMap<>();
    private final Map<UUID, Spin> uiSpins = new HashMap<>();
    /** Letzter Gewinn je Spieler für das Fenster nach dem Dreh (Rad, Anzeige, Gold, Hauptgewinn, Zeit). */
    private final Map<UUID, WheelScreen.Win> lastWin = new HashMap<>();
    /** Fenster-Rad: Winkel je Spieler (das Bild bleibt da stehen, wo es zuletzt hielt). */
    private final Map<UUID, Double> uiAngle = new HashMap<>();
    private BukkitTask task;
    private int tick;
    /** Test: letztes Ergebnis (gezogenes Feld, Feld an der Nadel nach dem Halt, Gewinn, ok/Fehler). */
    public volatile String testLast;

    public WheelService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gluecksrad.yml");
        this.tag = new NamespacedKey(plugin, "gluecksrad");
        this.click = new NamespacedKey(plugin, "gluecksrad_click");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection r = yml.getConfigurationSection("raeder");
        if (r != null) for (String id : r.getKeys(false)) raw.put(id, r.getString(id));
        ConfigurationSection z = yml.getConfigurationSection("zeit");
        if (z != null) {
            for (String k : z.getKeys(false)) {
                if (z.isConfigurationSection(k)) {
                    ConfigurationSection w = z.getConfigurationSection(k);
                    for (String u : w.getKeys(false)) putUuid(last, k, u, w.getLong(u));
                } else {
                    putUuid(last, Wheel.CASINO, k, z.getLong(k));   // alte Fassung (nur ein Rad)
                }
            }
        }
        ConfigurationSection s = yml.getConfigurationSection("serie");
        if (s != null) {
            for (String w : s.getKeys(false)) {
                ConfigurationSection ws = s.getConfigurationSection(w);
                if (ws == null) continue;
                for (String u : ws.getKeys(false)) {
                    try {
                        streak.computeIfAbsent(w, k -> new HashMap<>()).put(UUID.fromString(u), ws.getInt(u));
                    } catch (IllegalArgumentException ignored) {
                        // kaputter Eintrag
                    }
                }
            }
        }
        ConfigurationSection g = yml.getConfigurationSection("gewinner");
        if (g != null) for (String w : g.getKeys(false)) winners.put(w, new ArrayList<>(g.getStringList(w)));
    }

    private static void putUuid(Map<String, Map<UUID, Long>> m, String wheel, String uuid, long v) {
        try {
            m.computeIfAbsent(wheel, k -> new HashMap<>()).put(UUID.fromString(uuid), v);
        } catch (IllegalArgumentException ignored) {
            // kaputter Eintrag
        }
    }

    private synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        raw.forEach((k, v) -> yml.set("raeder." + k, v));
        long cut = System.currentTimeMillis() - 30L * 86_400_000L;
        last.forEach((w, m) -> m.forEach((k, v) -> {
            if (v > cut) yml.set("zeit." + w + "." + k, v);
        }));
        streak.forEach((w, m) -> m.forEach((k, v) -> {
            Long t = last.getOrDefault(w, Map.of()).get(k);
            if (v > 0 && t != null && t > cut) yml.set("serie." + w + "." + k, v);
        }));
        winners.forEach((w, l) -> yml.set("gewinner." + w, l));
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("gluecksrad.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // =================================================================================================================
    // Räder, Modi, Wartezeit, Serie
    // =================================================================================================================

    /** Rad für den Modus des Spielers: Survival = casino, SMP = smp, sonst keins. */
    public String wheelFor(Player p) {
        String mode = plugin.modes().modeOf(p.getUniqueId());
        if (ModeService.SURVIVAL.equals(mode)) return Wheel.CASINO;
        if (ModeService.SMP.equals(mode)) return Wheel.SMP;
        return null;
    }

    static String modeOf(String wheel) {
        return Wheel.SMP.equals(wheel) ? ModeService.SMP : ModeService.SURVIVAL;
    }

    public List<Wheel.Field> fields(String wheel) {
        return Wheel.fields(cfg(), wheel);
    }

    public long hours(String wheel) {
        return Math.max(1, cfg().getLong("gluecksrad." + wheel + ".stunden", 24));
    }

    public long cooldownMs(String wheel) {
        return hours(wheel) * 3_600_000L;
    }

    private long streakMs(String wheel) {
        return Math.max(hours(wheel) + 1, cfg().getLong("gluecksrad." + wheel + ".serie-stunden", 48)) * 3_600_000L;
    }

    public int goldEvery(String wheel) {
        return Math.max(2, cfg().getInt("gluecksrad." + wheel + ".gold-dreh", 7));
    }

    /** Millisekunden bis zum nächsten Gratis-Dreh (0 = bereit). */
    public synchronized long left(String wheel, UUID id) {
        Long t = last.getOrDefault(wheel, Map.of()).get(id);
        return t == null ? 0 : Math.max(0, t + cooldownMs(wheel) - System.currentTimeMillis());
    }

    /** Laufende Serie (0 = keine oder abgelaufen). */
    public synchronized int streak(String wheel, UUID id) {
        Long t = last.getOrDefault(wheel, Map.of()).get(id);
        if (t == null || System.currentTimeMillis() - t > streakMs(wheel)) return 0;
        return streak.getOrDefault(wheel, Map.of()).getOrDefault(id, 0);
    }

    /** Serie, die der nächste Dreh hätte (1 = neu). */
    public int nextStreak(String wheel, UUID id) {
        return streak(wheel, id) + 1;
    }

    public boolean nextGold(String wheel, UUID id) {
        return nextStreak(wheel, id) % goldEvery(wheel) == 0;
    }

    /** Wie lange die Serie noch hält (ms; 0 = keine Serie). */
    public synchronized long streakLeft(String wheel, UUID id) {
        Long t = last.getOrDefault(wheel, Map.of()).get(id);
        if (t == null || streak(wheel, id) == 0) return 0;
        return Math.max(0, t + streakMs(wheel) - System.currentTimeMillis());
    }

    public static String duration(long ms) {
        long m = (ms + 59_999) / 60_000;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min";
    }

    private static String clock(long ms) {
        java.time.LocalTime at = java.time.LocalTime.now().plusSeconds(ms / 1000);
        return String.format("%02d:%02d", at.getHour(), at.getMinute());
    }

    public boolean hasWheel() {
        return !raw.isEmpty();
    }

    // =================================================================================================================
    // Bühnen laden und aufbauen
    // =================================================================================================================

    private Stage load(String id) {
        Stage b = stages.get(id);
        if (b != null) return b;
        String line = raw.get(id);
        if (line == null) return null;
        String[] p = line.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            Location c = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            b = new Stage(new WheelStage(id, c, Float.parseFloat(p[4]), Double.parseDouble(p[5]), tag, click));
            stages.put(id, b);
            return b;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void build(Stage b) {
        List<Wheel.Field> fs = fields(Wheel.CASINO);
        Wheel.Field jf = fs.get(Wheel.jackpot(fs));
        b.st.build(Component.text(jf.name().replaceFirst("(?i)^hauptgewinn\\s*", ""), NamedTextColor.WHITE), jackpotItem(jf.prize()));
        idle(b);
    }

    private static ItemStack jackpotItem(String prize) {
        String kind = prize.split(":")[0];
        Material m = switch (kind) {
            case "chips" -> Material.GOLD_BLOCK;
            case "coins" -> Material.GOLD_INGOT;
            case "jewels" -> Material.EMERALD;
            case "key" -> Material.TRIPWIRE_HOOK;
            case "cosmetic" -> Material.GOLDEN_HELMET;
            default -> Material.NETHER_STAR;
        };
        return new ItemStack(m);
    }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void respawn() {
        for (String id : raw.keySet()) {
            Stage b = load(id);
            if (b == null) continue;
            Location c = b.st.center();
            if (c.getWorld().isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4) && !b.st.ready()) build(b);
        }
        start();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        for (String id : raw.keySet()) {
            Stage b = load(id);
            if (b == null) continue;
            Location c = b.st.center();
            if (c.getWorld().equals(e.getWorld()) && (c.getBlockX() >> 4) == e.getChunk().getX() && (c.getBlockZ() >> 4) == e.getChunk().getZ()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!b.st.ready()) build(b);
                }, 5L);
            }
        }
    }

    /** Beim Abschalten: laufende Drehs sofort auszahlen (das Feld steht fest), Anzeigen weg. */
    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Stage b : stages.values()) {
            if (b.spin != null) finish(b.spin, b, false);
            b.spin = null;
            b.st.removeAll();
        }
        for (Spin s : new ArrayList<>(uiSpins.values())) finish(s, null, false);
        uiSpins.clear();
        save();
    }

    // =================================================================================================================
    // Fenster und /gluecksrad
    // =================================================================================================================

    /** /gluecksrad: das Fenster zum Rad des Modus. */
    public void open(Player p) {
        String wheel = wheelFor(p);
        if (wheel == null) {
            CasinoText.send(p, "<gray>Glücksräder gibt es in <white>Survival</white> (im Casino und mit /gluecksrad) und in <white>SMP</white>.");
            return;
        }
        UiDialog.show(p, WheelScreen.canvas(view(p, wheel), actions(wheel)), WheelScreen.HEIGHT);
    }

    /** Kurze Info im Chat (z. B. aus dem Hilfe-Menü), mit Klick zum Fenster. */
    public void info(Player p) {
        String wheel = wheelFor(p);
        if (wheel == null) {
            open(p);
            return;
        }
        long l = left(wheel, p.getUniqueId());
        if (l == 0) {
            CasinoText.send(p, "<#F2C94C>Dein Glücksrad ist bereit!</#F2C94C> <click:run_command:'/gluecksrad'><white>[Jetzt drehen]</white></click>");
        } else {
            CasinoText.send(p, "<gray>Nächster Gratis-Dreh am Glücksrad in <white>" + duration(l) + "</white> (ab <white>"
                    + clock(l) + " Uhr</white>).");
        }
    }

    WheelScreen.View view(Player p, String wheel) {
        UUID id = p.getUniqueId();
        List<Wheel.Field> fs = fields(wheel);
        boolean spinning = uiSpins.containsKey(id);
        for (Stage b : stages.values()) if (b.spin != null && b.spin.player.equals(id)) spinning = true;
        WheelScreen.Win win = lastWin.get(id);
        if (win != null && (!win.wheel().equals(wheel) || System.currentTimeMillis() - win.at() > 120_000)) win = null;
        List<String> wl;
        synchronized (this) {
            wl = new ArrayList<>(winners.getOrDefault(wheel, List.of()));
        }
        return new WheelScreen.View(wheel, fs, Wheel.jackpot(fs), left(wheel, id), streak(wheel, id), goldEvery(wheel),
                nextGold(wheel, id), streakLeft(wheel, id), hours(wheel), spinning, win, wl,
                Wheel.CASINO.equals(wheel) && hasWheel());
    }

    WheelScreen.Actions actions(String wheel) {
        return new WheelScreen.Actions() {
            @Override
            public java.util.function.Consumer<Player> spin() {
                return pl -> uiSpin(pl, wheel);
            }

            @Override
            public java.util.function.Consumer<Player> refresh() {
                return WheelService.this::open;
            }

            @Override
            public java.util.function.Consumer<Player> casino() {
                return pl -> pl.performCommand("casino");
            }
        };
    }

    // =================================================================================================================
    // Drehen: Ziehung (für Bühne und Fenster gleich)
    // =================================================================================================================

    /** Prüft, ob der Spieler drehen darf. Fehlermeldung oder null. */
    private String check(Player p, String wheel, boolean test) {
        UUID id = p.getUniqueId();
        if (Wheel.CASINO.equals(wheel)) {
            String blocked = plugin.casino().checkMode(p);
            if (blocked != null) return blocked;
        } else if (!wheel.equals(wheelFor(p))) {
            return "Dieses Glücksrad gibt es nur in SMP.";
        }
        if (uiSpins.containsKey(id)) return "Dein Rad dreht schon.";
        for (Stage b : stages.values()) if (b.spin != null && b.spin.player.equals(id)) return "Dein Rad dreht schon.";
        long l = left(wheel, id);
        if (l > 0 && !test) return "WAIT";
        return null;
    }

    private void waitHint(Player p, String wheel) {
        UUID id = p.getUniqueId();
        long l = left(wheel, id);
        p.sendActionBar(Msg.parse("<gray>Nächster Gratis-Dreh in <white>" + duration(l) + "</white> · ab " + clock(l) + " Uhr"));
        Sfx.play(plugin, p, Sfx.Kind.DRUM_LOW);
    }

    /** Ergebnis ziehen und sofort speichern (Wartezeit und Serie), Plan fürs Rad ab Winkel from. */
    private Spin draw(Player p, String wheel, double from, boolean ui, int ticks, double offset) {
        UUID id = p.getUniqueId();
        List<Wheel.Field> fs = fields(wheel);
        int target = Wheel.pick(fs, RNG);
        Wheel.Field f = fs.get(target);
        int st = nextStreak(wheel, id);
        boolean gold = st % goldEvery(wheel) == 0;
        String prize = gold ? Wheel.doubled(f.prize()) : f.prize();
        synchronized (this) {
            last.computeIfAbsent(wheel, k -> new HashMap<>()).put(id, System.currentTimeMillis());
            streak.computeIfAbsent(wheel, k -> new HashMap<>()).put(id, st);
            reminded.computeIfAbsent(wheel, k -> new HashSet<>()).remove(id);
        }
        save();
        double[] plan = Wheel.plan(from - WINDUP_DEG, target, offset, 3 + RNG.nextInt(2), ticks);
        return new Spin(id, p.getName(), wheel, target, f, prize, gold, target == Wheel.jackpot(fs), st, ui,
                ui && Bedrock.is(p), from, plan);
    }

    // =================================================================================================================
    // Bühne
    // =================================================================================================================

    private void handle(Player p, Entity e) {
        String v = e.getPersistentDataContainer().get(click, PersistentDataType.STRING);
        if (v == null) return;
        Stage b = load(v.split(":")[0]);
        if (b == null) return;
        stageSpin(p, b, false);
    }

    /** Dreh auf der Bühne starten. test = Wartezeit übergehen (Belastungstest, Bilder). Fehlermeldung oder null. */
    private String stageSpin(Player p, Stage b, boolean test) {
        if (b.spin != null) {
            if (!b.spin.player.equals(p.getUniqueId())) CasinoText.error(p, "Das Glücksrad dreht gerade für " + b.spin.name + ". Gleich bist du dran!");
            return "dreht schon";
        }
        String err = check(p, Wheel.CASINO, test);
        if ("WAIT".equals(err)) {
            waitHint(p, Wheel.CASINO);
            info(p);
            return "Wartezeit";
        }
        if (err != null) {
            CasinoText.error(p, err);
            return err;
        }
        int ticks = test ? 120 : 130 + RNG.nextInt(50);
        double off = Wheel.OFF_MIN + RNG.nextDouble() * (Wheel.OFF_MAX - Wheel.OFF_MIN);
        double from = Double.isNaN(b.idleAngle) ? b.st.angle() : b.idleAngle;
        Spin s = draw(p, Wheel.CASINO, from, false, ticks, off);
        b.spin = s;
        b.idleAngle = Double.NaN;
        b.winBlink = 0;
        b.st.info(Component.text(p.getName() + (s.gold ? " dreht · GOLD-DREH!" : " dreht …"), s.gold ? TextColor.color(0xFFE870) : TextColor.color(0xE8E4DC)));
        b.st.spots(true);
        Location fx = b.st.effectLocation();
        fx.getWorld().playSound(fx, Sound.BLOCK_PISTON_CONTRACT, 0.8f, 0.6f);
        fx.getWorld().playSound(fx, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.7f);
        if (s.gold) fx.getWorld().playSound(fx, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
        start();
        return null;
    }

    private void tickStage(Stage b) {
        WheelStage st = b.st;
        Spin s = b.spin;
        Location fx = st.effectLocation();
        World w = fx.getWorld();
        if (s == null) {
            idleTick(b);
            return;
        }
        s.t++;
        double a = s.angle();
        if (s.t <= WINDUP) {
            // Ausholen: Rad zieht zurück, Trommelwirbel wird schneller
            st.spinTo(a, 1);
            if (s.t % 2 == 0) w.playSound(fx, Sound.BLOCK_NOTE_BLOCK_SNARE, 0.55f, 0.8f + s.t * 0.05f);
            boolean[] lit = new boolean[WheelStage.RIM];
            for (int i = 0; i < lit.length; i++) lit[i] = (i + s.t) % 3 == 0;
            st.rimBulbs(lit, s.gold);
            if (s.t == WINDUP) {
                w.playSound(fx, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.2f);
                w.playSound(fx, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.4f);
                w.spawnParticle(Particle.FIREWORK, fx, 18, 0.9, 0.9, 0.1, 0.06);
            }
            s.prev = a;
            return;
        }
        if (s.t % 2 == 0 || s.stopped()) st.spinTo(a, 2);
        if (s.t % 10 == 0) st.burst(-a * 0.08, 10);
        // Nadel: Stifte schlagen sie weg, sie federt zurück
        s.needle = Wheel.needleStep(s.needle, a);
        st.needle(s.needle[0]);
        double speed = Math.abs(a - s.prev);
        if (Wheel.pegPassed(s.prev, a)) {
            float pitch = (float) (1.0 + Math.min(1.0, speed / 25.0));
            w.playSound(fx, Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, pitch);
            w.playSound(fx, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.45f, 1.5f);
            if (speed < 6) w.spawnParticle(Particle.WAX_OFF, st.at(0, WheelStage.HC + WheelStage.R - 0.1, 0.3), 3, 0.05, 0.05, 0.02, 0.4);
        }
        int pf = Wheel.pointerField(a);
        if (pf != s.lastField) {
            s.lastField = pf;
            List<Wheel.Field> fs = fields(Wheel.CASINO);
            st.prize(WheelStage.prizeText(fs.get(pf).name(), pf == Wheel.jackpot(fs)));
        }
        // Lauflicht: drei helle Lämpchen rennen mit dem Rad, die Bühne blinkt im Takt
        if (s.t % 2 == 0) {
            int head = (int) Math.floor(a / 360.0 * WheelStage.RIM * 1.5);
            boolean[] lit = new boolean[WheelStage.RIM];
            for (int k = 0; k < 3; k++) lit[Math.floorMod(head - k, WheelStage.RIM)] = true;
            st.rimBulbs(lit, s.gold);
            boolean[] pl = new boolean[WheelStage.PLAT_BULBS];
            for (int i = 0; i < pl.length; i++) pl[i] = (i + s.t / 2) % 4 == 0;
            st.platBulbs(pl, s.gold);
            boolean[] sg = new boolean[WheelStage.SIGN_BULBS];
            for (int i = 0; i < sg.length; i++) sg[i] = (i + s.t / 4) % 2 == 0;
            st.signBulbs(sg, s.gold);
        }
        // echtes Licht pulsiert mit (Lichtblöcke; seltener, damit der Server nicht ständig Licht neu rechnet)
        if (s.t % 6 == 0) st.lights(11 + (s.t / 6) % 5);
        // Scheinwerfer: Lichtstrahl aus Partikeln aufs Rad
        if (s.t % 4 == 0) beams(st, s.gold);
        // Spannung kurz vor dem Halt: Herzschlag
        int rest = s.plan.length - 1 - (s.t - WINDUP);
        if (rest > 0 && rest <= 36 && rest % 9 == 0) w.playSound(fx, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.8f, 0.6f);
        if (speed > 18 && s.t % 3 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, st.at(0, WheelStage.HC, 0.3), 4, 1.2, 1.2, 0.05, 0.02);
        s.prev = a;
        if (s.done()) {
            b.spin = null;
            finish(s, b, true);
        }
    }

    private void beams(WheelStage st, boolean gold) {
        Particle.DustOptions dust = new Particle.DustOptions(gold ? Color.fromRGB(0xFFE870) : Color.fromRGB(0xFFF4D0), 0.7f);
        for (int side = 0; side < 2; side++) {
            Location[] l = st.spotBeam(side);
            Location from = l[0], to = l[1];
            World w = from.getWorld();
            double steps = 10;
            for (int i = 1; i <= steps; i++) {
                double k = i / steps;
                Location at = from.clone().add(to.clone().subtract(from).toVector().multiply(k));
                w.spawnParticle(Particle.DUST, at, 1, 0.04, 0.04, 0.04, 0, dust);
            }
        }
    }

    /** Leerlauf-Drehung (Wunsch des Besitzers: „dauerhaft langsam leicht drehen, als Idle-Animation“): Grad je Tick. */
    private static final double IDLE_SPEED = 0.3;

    private void idleTick(Stage b) {
        WheelStage st = b.st;
        if (tick % 2 == 0) st.podium();
        // nach einem Dreh bleibt das Ergebnis erst stehen (Blinken + gut 3 s), dann dreht das Rad langsam weiter
        if (b.winBlink > 0) b.idleHold = 70;
        else if (b.idleHold > 0) b.idleHold--;
        else idleSpin(b);
        if (b.winBlink > 0) {
            b.winBlink--;
            if (b.winBlink % 4 == 0) {
                boolean on = (b.winBlink / 4) % 2 == 0;
                boolean[] lit = new boolean[Math.max(WheelStage.RIM, Math.max(WheelStage.PLAT_BULBS, WheelStage.SIGN_BULBS))];
                java.util.Arrays.fill(lit, on);
                st.rimBulbs(lit, b.bigWin);
                st.platBulbs(lit, b.bigWin);
                st.signBulbs(lit, b.bigWin);
                if (b.winBlink % 8 == 0) st.lights(on ? 15 : 10);
            }
            if (b.bigWin && b.winBlink % 10 == 0) confetti(st, 14);
            if (b.winBlink == 0) {
                st.spots(false);
                idle(b);
            }
            return;
        }
        // Ruhe: langsames Lauflicht, Schild im Wechsel; wartet jemand in der Nähe mit bereitem Dreh, wird es lebhafter
        if (tick % 20 == 0) {
            boolean w = readyNearby(st);
            if (b.waiting && !w) idle(b);
            b.waiting = w;
        }
        int pace = b.waiting ? 3 : 10;
        if (tick % pace == 0) {
            int head = tick / pace;
            boolean[] lit = new boolean[WheelStage.RIM];
            for (int i = 0; i < lit.length; i++) lit[i] = (i + head) % 6 == 0;
            st.rimBulbs(lit, false);
            boolean[] pl = new boolean[WheelStage.PLAT_BULBS];
            for (int i = 0; i < pl.length; i++) pl[i] = (i + head) % 2 == 0;
            st.platBulbs(pl, false);
        }
        if (tick % 20 == 0) {
            boolean[] sg = new boolean[WheelStage.SIGN_BULBS];
            for (int i = 0; i < sg.length; i++) sg[i] = (i + tick / 20) % 2 == 0;
            st.signBulbs(sg, false);
            st.lights(b.waiting ? 13 + (tick / 20) % 3 : 12);
            if (b.waiting) {
                boolean on = (tick / 20) % 2 == 0;
                st.info(Component.text(on ? "▶ Rechtsklick aufs Rad: dein Gratis-Dreh wartet! ◀" : "Rechtsklick aufs Rad: dein Gratis-Dreh wartet!",
                        on ? TextColor.color(0xFFE870) : TextColor.color(0xF2C94C)));
            }
        }
        // persönliche Wartezeit für alle in der Nähe (Actionbar)
        if (tick % 40 == 0) {
            Location c = st.center();
            double r = 9 * st.size();
            for (Player p : c.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(c) > r * r || !Wheel.CASINO.equals(wheelFor(p))) continue;
                UUID id = p.getUniqueId();
                long l = left(Wheel.CASINO, id);
                int ns = nextStreak(Wheel.CASINO, id), ge = goldEvery(Wheel.CASINO);
                String serie = " <dark_gray>·</dark_gray> <gray>Serie <white>" + ((ns - 1) % ge + 1) + "/" + ge
                        + (ns % ge == 0 ? " <#FFE870>GOLD-DREH" : "");
                p.sendActionBar(Msg.parse(l == 0 ? "<#F2C94C>Glücksrad: dein Gratis-Dreh ist bereit!" + serie
                        : "<gray>Glücksrad: nächster Dreh in <white>" + duration(l) + "</white>" + serie));
            }
        }
    }

    private void idleSpin(Stage b) {
        WheelStage st = b.st;
        if (Double.isNaN(b.idleAngle)) b.idleAngle = st.angle();
        b.idleAngle += IDLE_SPEED;
        // Ziel 10 Ticks voraus, weich nachgezogen: so steht die Anzeige jeden Tick etwa bei idleAngle
        if (tick % 10 == 0) st.spinTo(b.idleAngle + IDLE_SPEED * 10, 10);
        b.idleNeedle = Wheel.needleStep(b.idleNeedle, b.idleAngle);
        st.needle(b.idleNeedle[0]);
    }

    private boolean readyNearby(WheelStage st) {
        Location c = st.center();
        double r = 9 * st.size();
        for (Player p : c.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(c) > r * r) continue;
            if (Wheel.CASINO.equals(wheelFor(p)) && left(Wheel.CASINO, p.getUniqueId()) == 0) return true;
        }
        return false;
    }

    private void idle(Stage b) {
        b.bigWin = false;
        b.st.prize(WheelStage.prizeText("Gratis-Dreh alle " + hours(Wheel.CASINO) + " Stunden", false));
        b.st.info(Component.text("Rechtsklick aufs Rad · /gluecksrad zeigt Gewinne und Serie", TextColor.color(0xACA48E)));
    }

    private void confetti(WheelStage st, int n) {
        Location c = st.confettiLocation();
        World w = c.getWorld();
        int[] cols = {0xFFE870, 0xF2A21C, 0xD8384A, 0x2A64C0, 0x1EA454, 0x8234B4, 0xFFFFFF};
        for (int i = 0; i < n; i++) {
            Particle.DustOptions d = new Particle.DustOptions(Color.fromRGB(cols[RNG.nextInt(cols.length)]), 1.1f);
            w.spawnParticle(Particle.DUST, c.clone().add((RNG.nextDouble() - 0.5) * 4 * st.size(), (RNG.nextDouble() - 0.2) * 1.2, (RNG.nextDouble() - 0.5) * 1.5),
                    1, 0, 0, 0, 0, d);
        }
    }

    // =================================================================================================================
    // Fenster-Dreh (Bild über den Bildschirm)
    // =================================================================================================================

    /** Dreh im Fenster. Fehlermeldung oder null. */
    String uiSpin(Player p, String wheel) {
        String err = check(p, wheel, false);
        if ("WAIT".equals(err)) {
            waitHint(p, wheel);
            open(p);
            return "Wartezeit";
        }
        if (err != null) {
            CasinoText.error(p, err);
            return err;
        }
        UUID id = p.getUniqueId();
        double from = uiAngle.getOrDefault(id, 10.0 * RNG.nextInt(36));
        // Halt genau auf einem Bild (10 Grad): Versatz 10 hinter der Feldmitte
        Spin s = draw(p, wheel, from, true, 110 + RNG.nextInt(40), 10);
        uiSpins.put(id, s);
        UiDialog.close(p);
        if (!s.text) {
            p.sendTitlePart(TitlePart.TIMES, Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400)));
            p.sendTitlePart(TitlePart.SUBTITLE, Component.empty());
            p.sendTitlePart(TitlePart.TITLE, WheelScreen.frame(wheel, from, 0));
        }
        Sfx.play(plugin, p, Sfx.Kind.WHOOSH);
        if (s.gold) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.6f);
            p.sendActionBar(Component.text("✦ GOLD-DREH: doppelter Gewinn! ✦", TextColor.color(0xFFE870), TextDecoration.BOLD));
        }
        start();
        return null;
    }

    private void tickUi(Spin s) {
        Player p = Bukkit.getPlayer(s.player);
        if (p == null) {
            uiSpins.remove(s.player);
            finish(s, null, false);
            return;
        }
        s.t++;
        double a = s.angle();
        s.needle = Wheel.needleStep(s.needle, a);
        if (!s.text) p.sendTitlePart(TitlePart.TITLE, WheelScreen.frame(s.wheel, a, s.needle[0]));
        if (s.t <= WINDUP) {
            if (s.t % 2 == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_SNARE, 0.5f, 0.8f + s.t * 0.05f);
            s.prev = a;
            return;
        }
        if (s.t == WINDUP + 1) p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1.3f);
        if (Wheel.pegPassed(s.prev, a)) {
            float pitch = (float) (1.0 + Math.min(1.0, Math.abs(a - s.prev) / 25.0));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, pitch);
        }
        int pf = Wheel.pointerField(a);
        if (pf != s.lastField || s.t % 20 == 0) {
            s.lastField = pf;
            List<Wheel.Field> fs = fields(s.wheel);
            boolean jp = pf == Wheel.jackpot(fs);
            p.sendActionBar(Component.text("▼ ", TextColor.color(0xF2C94C)).append(Component.text(fs.get(pf).name(),
                    jp ? TextColor.color(0xFFE870) : NamedTextColor.WHITE, TextDecoration.BOLD)).append(Component.text(" ▼", TextColor.color(0xF2C94C))));
        }
        int rest = s.plan.length - 1 - (s.t - WINDUP);
        if (rest > 0 && rest <= 36 && rest % 9 == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.7f, 0.6f);
        s.prev = a;
        if (s.stopped() && s.t - WINDUP == s.plan.length - 1 && !s.text) {
            // letztes Bild länger stehen lassen
            p.sendTitlePart(TitlePart.TIMES, Title.Times.times(Duration.ZERO, Duration.ofMillis(2600), Duration.ofMillis(500)));
            p.sendTitlePart(TitlePart.TITLE, WheelScreen.frame(s.wheel, a, 0));
        }
        if (s.done()) {
            uiSpins.remove(s.player);
            uiAngle.put(s.player, a % 360);
            finish(s, null, true);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player q = Bukkit.getPlayer(s.player);
                if (q != null && s.wheel.equals(wheelFor(q))) open(q);
            }, 30L);
        }
    }

    // =================================================================================================================
    // Ticken, Auszahlen
    // =================================================================================================================

    private void tick() {
        tick++;
        for (Stage b : stages.values()) {
            if (b.st.ready()) tickStage(b);
        }
        for (Spin s : new ArrayList<>(uiSpins.values())) tickUi(s);
        // einmal je Minute: wer wieder drehen darf, bekommt eine Nachricht (einmal), läuft die Serie bald ab, auch
        if (tick % 1200 == 0) {
            for (Player p : Bukkit.getOnlinePlayers()) remind(p, false);
        }
    }

    private void remind(Player p, boolean join) {
        String wheel = wheelFor(p);
        if (wheel == null || !cfg().getBoolean("gluecksrad." + wheel + ".erinnerung", true)) return;
        UUID id = p.getUniqueId();
        synchronized (this) {
            boolean played = last.getOrDefault(wheel, Map.of()).containsKey(id);
            if ((!played && !join) || left(wheel, id) > 0) return;
            Set<UUID> r = reminded.computeIfAbsent(wheel, k -> new HashSet<>());
            if (!join && r.contains(id)) return;
            r.add(id);
        }
        int s = streak(wheel, id);
        long sl = streakLeft(wheel, id);
        String serie = s > 0 ? " <gray>Serie <white>" + s + "</white>" + (sl > 0 && sl < 6 * 3_600_000L ? " läuft in <white>" + duration(sl) + "</white> ab!" : "")
                + (nextGold(wheel, id) ? " <#FFE870>Nächster Dreh: GOLD-DREH (doppelt)!" : "") : "";
        CasinoText.send(p, "<#F2C94C>✦ Dein Glücksrad ist bereit!</#F2C94C> <click:run_command:'/gluecksrad'><white>[Jetzt drehen]</white></click>" + serie);
        Sfx.play(plugin, p, Sfx.Kind.CHIME);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) remind(p, true);
        }, 160L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Spin s = uiSpins.remove(id);
        if (s != null) finish(s, null, false);
        uiAngle.remove(id);
    }

    /** Gewinn auszahlen, Ansage, Gewinner-Tafel (effects: Feuerwerk, Töne, Titel). */
    private void finish(Spin s, Stage b, boolean effects) {
        double end = s.plan[s.plan.length - 1];
        int landed = Wheel.pointerField(end);
        String[] res = give(s.player, s.wheel, s.prize);
        String err = res[0];
        String shown = (s.gold ? "GOLD-DREH: " : "") + (res[1] != null ? res[1] : s.field.name());
        testLast = s.target + ";" + landed + ";" + s.prize + ";" + (err == null ? "ok" : err);
        long value = Wheel.value(s.prize);
        boolean big = s.jackpot || value >= cfg().getLong("gluecksrad." + s.wheel + ".ansage-ab", 100_000);
        synchronized (this) {
            List<String> wl = winners.computeIfAbsent(s.wheel, k -> new ArrayList<>());
            wl.add(0, s.name + ";" + (s.gold ? "★ " : "") + (res[1] != null ? res[1] : s.field.name()) + ";" + System.currentTimeMillis()
                    + ";" + (s.gold ? 1 : 0) + ";" + (s.jackpot ? 1 : 0));
            while (wl.size() > WINNERS) wl.remove(wl.size() - 1);
        }
        save();
        lastWin.put(s.player, new WheelScreen.Win(s.wheel, shown, s.gold, s.jackpot, System.currentTimeMillis()));
        Player p = Bukkit.getPlayer(s.player);
        if (p != null) {
            if (err != null) CasinoText.error(p, "Gewinn " + s.field.name() + " konnte nicht ausgezahlt werden: " + err);
            else CasinoText.send(p, "<green>Glücksrad: <white>" + shown + "</white>! <gray>Nächster Gratis-Dreh in "
                    + duration(cooldownMs(s.wheel)) + "." + (s.gold ? "" : " Serie " + s.streak + ", Gold-Dreh bei "
                    + ((s.streak / goldEvery(s.wheel)) + 1) * goldEvery(s.wheel) + "."));
        }
        if (b != null) {
            List<Wheel.Field> fs = fields(Wheel.CASINO);
            b.st.prize(WheelStage.prizeText(fs.get(landed).name(), s.jackpot || s.gold));
            b.st.info(Component.text(s.name + " gewinnt " + shown + "!", TextColor.color(0xF2C94C)));
            b.winBlink = big ? 160 : 80;
            b.bigWin = big;
        }
        if (big) {
            Bukkit.broadcast(Msg.parse("<#F2C94C>✦ Glücksrad" + (Wheel.SMP.equals(s.wheel) ? " (SMP)" : "") + ": <white>" + s.name
                    + "</white> gewinnt <#FFE870><b>" + Msg.escape(shown) + "</b></#FFE870>!"));
        }
        if (!effects) return;
        if (p != null) {
            boolean title = big || s.gold;
            if (title) {
                p.showTitle(Title.title(Component.text(s.jackpot ? "HAUPTGEWINN!" : s.gold ? "GOLD-DREH!" : "GROSSER GEWINN!", TextColor.color(0xFFE870), TextDecoration.BOLD),
                        Component.text(res[1] != null ? res[1] : s.field.name(), NamedTextColor.WHITE),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2500), Duration.ofMillis(600))));
            }
            if (s.jackpot) Sfx.play(plugin, p, Sfx.Kind.JACKPOT);
            else if (big || s.gold) Sfx.play(plugin, p, Sfx.Kind.VICTORY);
            else jingle(p);
        }
        if (b != null) {
            Location fx = b.st.effectLocation();
            World w = fx.getWorld();
            w.playSound(fx, Sound.BLOCK_ANVIL_LAND, 0.2f, 1.9f);
            if (big || s.gold) {
                w.playSound(fx, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, fx, s.jackpot ? 220 : 110, 0.9, 0.9, 0.3, 0.45);
                w.spawnParticle(Particle.FIREWORK, fx, 70, 1.0, 1.0, 0.3, 0.14);
                confetti(b.st, 60);
                int bursts = s.jackpot ? 8 : 3;
                for (int i = 0; i < bursts; i++) {
                    int delay = 6 + i * 9;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        Location l = b.st.confettiLocation().add((RNG.nextDouble() - 0.5) * 4, RNG.nextDouble() * 1.5, 0);
                        w.spawnParticle(Particle.FIREWORK, l, 50, 0.1, 0.1, 0.1, 0.18);
                        w.playSound(l, RNG.nextBoolean() ? Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST : Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.2f, 1f);
                    }, delay);
                }
                if (s.jackpot) w.playSound(fx, Sound.BLOCK_BELL_USE, 1f, 0.8f);
            } else {
                w.playSound(fx, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
                w.spawnParticle(Particle.WAX_ON, fx, 40, 0.8, 0.8, 0.2, 0.6);
                confetti(b.st, 20);
            }
        }
    }

    private void jingle(Player p) {
        float[] notes = {1.0f, 1.26f, 1.5f, 2.0f};
        for (int i = 0; i < notes.length; i++) {
            float pitch = notes[i];
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, pitch);
            }, i * 3L);
        }
    }

    /**
     * Gewinn geben (mehrere mit + verbunden, z. B. booster:xp:2:60+sell:2:60). Rückgabe: [Fehler oder null,
     * geänderte Anzeige oder null] (Kosmetik schon im Besitz: Ersatz-Betrag).
     */
    private String[] give(UUID id, String wheel, String prize) {
        String mode = modeOf(wheel);
        String kind = "";
        String note = null;
        for (String part : prize.split("\\+")) {
            String[] p = part.trim().split(":");
            if (!isKind(p[0]) && !kind.isEmpty()) {
                String[] q = new String[p.length + 1];
                q[0] = kind;
                System.arraycopy(p, 0, q, 1, p.length);
                p = q;
            }
            kind = p[0];
            try {
                switch (p[0]) {
                    case "chips" -> plugin.casino().giveChips(id, Long.parseLong(p[1].trim()));
                    case "coins" -> plugin.modes().depositIn(id, mode, Long.parseLong(p[1].trim()));
                    case "xp" -> plugin.modes().xpIn(id, mode, Long.parseLong(p[1].trim()));
                    case "jewels" -> plugin.jewels().add(id, Long.parseLong(p[1].trim()));
                    case "key" -> {
                        if (!plugin.keys().give(id, mode, p[1], p.length > 2 ? Integer.parseInt(p[2].trim()) : 1))
                            return new String[]{"Schlüssel " + p[1] + " fehlt in keys.types", null};
                    }
                    case "booster" -> plugin.booster().grant(id, p[1], Double.parseDouble(p[2].trim()), Integer.parseInt(p[3].trim()));
                    case "tag" -> {
                        if (plugin.tags().displayOf(p[1]) == null) return new String[]{"Titel " + p[1] + " fehlt", null};
                        if (plugin.tags().has(id, p[1]) && p.length > 2) note = refund(id, wheel, Long.parseLong(p[2].trim()));
                        else plugin.chipShop().grant(id, "tag:" + p[1]);
                    }
                    case "cosmetic" -> {
                        var item = plugin.cosmetics().find(p[1]);
                        if (item == null) return new String[]{"Kosmetik " + p[1] + " fehlt", null};
                        if (plugin.cosmetics().owns(id, item) && p.length > 2) note = refund(id, wheel, Long.parseLong(p[2].trim()));
                        else plugin.chipShop().grant(id, "cosmetic:" + p[1]);
                    }
                    default -> {
                        return new String[]{"unbekannte Art " + p[0], null};
                    }
                }
            } catch (RuntimeException e) {
                return new String[]{"falsch eingetragen (" + prize + ")", null};
            }
        }
        return new String[]{null, note};
    }

    private String refund(UUID id, String wheel, long amount) {
        if (Wheel.SMP.equals(wheel)) {
            plugin.modes().depositIn(id, ModeService.SMP, amount);
            return Msg.coins(amount) + " $ (hattest du schon)";
        }
        plugin.casino().giveChips(id, amount);
        return CasinoText.num(amount) + " Chips (hattest du schon)";
    }

    private static boolean isKind(String k) {
        return switch (k) {
            case "chips", "coins", "xp", "jewels", "key", "booster", "tag", "cosmetic" -> true;
            default -> false;
        };
    }

    /** Prüft die Felder beider Räder (SELFTEST): Fehler oder leere Liste. */
    public static List<String> checkFields(KubyPlugins plugin) {
        List<String> err = new ArrayList<>();
        for (String wheel : new String[]{Wheel.CASINO, Wheel.SMP}) {
            List<Wheel.Field> fs = Wheel.fields(plugin.getConfig(), wheel);
            for (int i = 0; i < fs.size(); i++) {
                String kind = "";
                for (String part : fs.get(i).prize().split("\\+")) {
                    String[] p = part.trim().split(":");
                    if (!isKind(p[0]) && !kind.isEmpty()) {
                        String[] q = new String[p.length + 1];
                        q[0] = kind;
                        System.arraycopy(p, 0, q, 1, p.length);
                        p = q;
                    }
                    kind = p[0];
                    String bad = switch (p[0]) {
                        case "chips", "coins", "jewels", "xp" -> p.length == 2 && p[1].matches("\\d+") ? null : "Betrag";
                        case "key" -> p.length >= 2 && plugin.keys().type(p[1]) != null ? null : "Schlüssel fehlt in keys.types";
                        case "booster" -> p.length == 4 && p[2].matches("[0-9.]+") && p[3].matches("\\d+")
                                && List.of("xp", "sell", "cashback").contains(p[1]) ? null : "Format booster:xp|sell|cashback:faktor:min";
                        case "tag" -> p.length >= 2 && plugin.tags().displayOf(p[1]) != null ? null : "Titel fehlt";
                        case "cosmetic" -> p.length >= 2 && plugin.cosmetics().find(p[1]) != null ? null : "Kosmetik fehlt";
                        default -> "unbekannte Art";
                    };
                    if (bad == null && Wheel.CASINO.equals(wheel) && (p[0].equals("coins") || p[0].equals("xp")))
                        bad = "im Casino-Rad gibt es keine " + p[0] + " (Survival)";
                    if (bad == null && Wheel.SMP.equals(wheel) && p[0].equals("chips")) bad = "Chips gibt es nur im Casino-Rad";
                    if (bad != null) err.add(wheel + " Feld " + i + " (" + fs.get(i).prize() + "): " + bad);
                }
            }
        }
        return err;
    }

    // =================================================================================================================
    // Klicks, Aufbau, Test
    // =================================================================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(click, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        handle(e.getPlayer(), e.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        if (!e.getAttacked().getPersistentDataContainer().has(click, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        handle(e.getPlayer(), e.getAttacked());
    }

    /** Belastungstest: Bühne aufbauen (ohne Speichern). center = Mitte der Bühne am Boden. */
    public void testBoard(String id, Location center, float yaw, double size) {
        raw.put(id, center.getWorld().getName() + ";" + center.getX() + ";" + center.getY() + ";" + center.getZ() + ";" + yaw + ";" + size);
        Stage old = stages.remove(id);
        if (old != null) old.st.removeAll();
        Stage b = load(id);
        if (b != null) build(b);
        start();
    }

    /** Belastungstest: Dreh ohne Wartezeit. Fehlermeldung oder null. */
    public String testSpin(Player p, String id) {
        Stage b = load(id);
        if (b == null || !b.st.ready()) return "kein Glücksrad " + id;
        return stageSpin(p, b, true);
    }

    /** Belastungstest/Bilder: Fenster-Dreh ohne Wartezeit. Fehlermeldung oder null. */
    public String testUiSpin(Player p) {
        String wheel = wheelFor(p);
        if (wheel == null) return "kein Rad in diesem Modus";
        synchronized (this) {
            last.getOrDefault(wheel, new HashMap<>()).remove(p.getUniqueId());
        }
        return uiSpin(p, wheel);
    }

    public Location wheelCenter(String id) {
        Stage b = load(id);
        return b == null ? null : b.st.at(0, WheelStage.HC, 0);
    }

    /** Stehplatz vor der Bühne mit Blick aufs Rad (Kamera, Rundgang). */
    public Location viewpoint(String id, double dist) {
        Stage b = load(id);
        return b == null ? null : b.st.viewpoint(dist);
    }

    /** /casino gluecksrad aufstellen [Größe] | entfernen | zuruecksetzen <Spieler> */
    public void command(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!p.hasPermission("kuby.admin") || sub.isEmpty()) {
            open(p);
            return;
        }
        switch (sub) {
            case "aufstellen", "aufbauen", "rad" -> {
                double size = 1.0;
                if (args.length > 2) {
                    try {
                        size = Math.max(0.6, Math.min(1.5, Double.parseDouble(args[2].replace(',', '.'))));
                    } catch (NumberFormatException ex) {
                        Msg.send(p, "<red>Nutzung: /casino gluecksrad aufstellen [Größe 0,6–1,5]");
                        return;
                    }
                }
                // Bühne vor den Spieler stellen, Vorderseite zu ihm; Blickrichtung auf 90 Grad gerundet
                float yaw = Math.round(p.getLocation().getYaw() / 90f) * 90f;
                double r = Math.toRadians(yaw);
                double dist = (WheelStage.PLAT_D / 2 + 1.6) * size;
                Location at = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5)
                        .add(-Math.sin(r) * dist, 0, Math.cos(r) * dist);
                at.setY(p.getLocation().getBlockY());
                float front = yaw + 180f;
                String id = "g" + System.currentTimeMillis() % 100000;
                raw.put(id, p.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ() + ";" + front + ";" + size);
                save();
                Stage b = load(id);
                if (b != null) build(b);
                start();
                Msg.send(p, "<green>Glücksrad-Bühne aufgestellt (" + String.format(Locale.GERMAN, "%.0f x %.0f x %.0f",
                        (WheelStage.PLAT_W + 3.6) * size, WheelStage.PLAT_D * size, (WheelStage.HC + WheelStage.R + 1.7) * size)
                        + " Blöcke). Spieler drehen alle " + hours(Wheel.CASINO) + " Stunden gratis. Entfernen: /casino gluecksrad entfernen");
            }
            case "entfernen" -> {
                Stage best = null;
                double bd = 100;
                for (String id : raw.keySet()) {
                    Stage b = load(id);
                    if (b == null || !b.st.center().getWorld().equals(p.getWorld())) continue;
                    double d = b.st.center().distanceSquared(p.getLocation());
                    if (d < bd) {
                        bd = d;
                        best = b;
                    }
                }
                if (best == null) {
                    Msg.send(p, "<red>Keine Glücksrad-Bühne im Umkreis von 10 Blöcken.");
                    return;
                }
                if (best.spin != null) finish(best.spin, best, false);
                best.spin = null;
                best.st.removeAll();
                stages.remove(best.st.id());
                raw.remove(best.st.id());
                save();
                Msg.send(p, "<green>Glücksrad entfernt.");
            }
            case "zuruecksetzen", "reset" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : p;
                if (t == null) {
                    Msg.send(p, "<red>Spieler nicht online.");
                    return;
                }
                synchronized (this) {
                    for (Map<UUID, Long> m : last.values()) m.remove(t.getUniqueId());
                }
                save();
                Msg.send(p, "<green>Wartezeit von " + t.getName() + " zurückgesetzt (Serie bleibt).");
            }
            default -> Msg.send(p, "<gray>" + raw.size() + " Glücksrad-Bühnen. Aufstellen: freie Fläche vor dir (gut 9 x 4 Blöcke,"
                    + " 6 hoch), <white>/casino gluecksrad aufstellen [Größe 0,6–1,5]</white>. Weiter: entfernen, zuruecksetzen <Spieler>."
                    + " Gewinne: gluecksrad.casino.felder in der config.yml.");
        }
    }
}
