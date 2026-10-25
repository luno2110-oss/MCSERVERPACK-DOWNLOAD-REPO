package de.kuby.kubyplugins.casino;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.CasinoFx;
import de.kuby.kubyplugins.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Crash wie im Clip: Einsätze beim Creeper (Truhe "CRASH") oder an den Schildern, nach dem Countdown steigt der
 * Multiplikator und die Kurve wächst über den Bildschirm. Aussteigen mit Schleichen + Klick, Klick oder Schlag auf ein
 * Schild, dem Chat-Knopf [AUSZAHLEN] oder /cashout. Wer beim Crash noch drin ist, verliert den Einsatz.
 *
 * <p>Der Crash-Punkt steht beim Start fest: P(Crash erst ab m) = (1 − Hausvorteil) / m, gedeckelt durch den Höchstwert;
 * wer den Höchstwert erreicht, wird automatisch ausgezahlt. Einsätze liegen bis zum Ende der Runde in crash.yml und
 * kommen nach einem Absturz des Servers zurück.
 */
public final class CrashGame implements Listener, CommandExecutor {

    public enum Phase { IDLE, BETTING, RUNNING, CRASHED }

    private static final long TICK = 2L;
    private static final int CRASH_SHOWN_TICKS = 100;
    public static final long[] CHIPS = {10, 50, 100, 500, 1000, 5000};
    private static final TextColor GREEN = TextColor.color(0x5AE878);
    private static final TextColor RED = TextColor.color(0xFF4A4A);
    private static final TextColor YELLOW = TextColor.color(0xF6E23A);
    private static final TextColor ORANGE = TextColor.color(0xF2A02A);
    private static final TextColor LIGHT = TextColor.color(0xE8E4DC);
    private static final SecureRandom RNG = new SecureRandom();

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final NamespacedKey infoTag;
    private CrashScreen screen;
    private String screenRaw;
    private String titleRaw;
    private String roundsRaw;
    private String infoRaw;
    private final Set<String> signs = new LinkedHashSet<>();
    private final Map<UUID, Long> bets = new LinkedHashMap<>();
    private final Map<UUID, Double> cashed = new HashMap<>();
    private final Map<UUID, Long> lastBet = new HashMap<>();
    private final Deque<Double> history = new ArrayDeque<>();
    private BukkitTask task;
    private Phase phase = Phase.IDLE;
    private int ticks;
    private double crashAt;
    private double current = 1.0;
    private int lostPlayers;
    private long lostChips;
    private boolean maxed;
    private String lastSignState = "";

    public CrashGame(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crash.yml");
        this.tag = new NamespacedKey(plugin, "crash_screen");
        this.infoTag = new NamespacedKey(plugin, "crash_info");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        screenRaw = yml.getString("bildschirm");
        titleRaw = yml.getString("titel");
        roundsRaw = yml.getString("runden");
        infoRaw = yml.getString("info");
        signs.addAll(yml.getStringList("schilder"));
        for (double d : yml.getDoubleList("verlauf")) history.addLast(d);
        // Einsätze aus einer abgebrochenen Runde (Absturz) zurückgeben
        ConfigurationSection esc = yml.getConfigurationSection("treuhand");
        if (esc != null) {
            for (String k : esc.getKeys(false)) {
                try {
                    plugin.casino().giveChips(UUID.fromString(k), esc.getLong(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
            plugin.getLogger().info("Crash: offene Einsätze aus der letzten Sitzung zurückgegeben (" + esc.getKeys(false).size() + ").");
        }
        save();
    }

    // =================================================================================================================
    // Laden, Speichern, Aufbau
    // =================================================================================================================

    private static String locString(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ();
    }

    private static Location parseLoc(String raw) {
        if (raw == null) return null;
        String[] p = raw.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            Location l = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
            if (p.length >= 5) l.setYaw(Float.parseFloat(p[4]));
            return l;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private CrashScreen screen() {
        if (screen != null) return screen;
        if (screenRaw == null) return null;
        String[] p = screenRaw.split(";");
        Location c = parseLoc(screenRaw);
        if (c == null || p.length < 7) return null;
        try {
            c.setYaw(0);
            screen = new CrashScreen(c, Float.parseFloat(p[4]), Double.parseDouble(p[5]), Double.parseDouble(p[6]),
                    parseLoc(titleRaw), parseLoc(roundsRaw), tag);
            return screen;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean hasScreen() {
        return screenRaw != null;
    }

    public void respawn() {
        CrashScreen s = screen();
        if (s != null) {
            Location c = s.center();
            if (c.getWorld().isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4) && !s.ready()) build();
        }
        if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
    }

    private void build() {
        CrashScreen s = screen();
        if (s == null) return;
        s.build();
        buildInfo();
        showState();
        s.rounds(roundsText());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        CrashScreen s = screen();
        if (s == null) return;
        Location c = s.center();
        if (!c.getWorld().equals(e.getWorld()) || (c.getBlockX() >> 4) != e.getChunk().getX() || (c.getBlockZ() >> 4) != e.getChunk().getZ()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!s.ready()) build();
        }, 5L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        // laufende Einsätze zurück (wer schon ausgestiegen ist, hat sein Geld)
        for (Map.Entry<UUID, Long> e : bets.entrySet()) {
            if (!cashed.containsKey(e.getKey())) plugin.casino().giveChips(e.getKey(), e.getValue());
        }
        bets.clear();
        cashed.clear();
        if (screen != null) screen.removeAll();
        removeInfo();
        save();
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("bildschirm", screenRaw);
        yml.set("titel", titleRaw);
        yml.set("runden", roundsRaw);
        yml.set("info", infoRaw);
        yml.set("schilder", new ArrayList<>(signs));
        yml.set("verlauf", new ArrayList<>(history));
        for (Map.Entry<UUID, Long> e : bets.entrySet()) {
            if (!cashed.containsKey(e.getKey())) yml.set("treuhand." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("crash.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // =================================================================================================================
    // Spielen
    // =================================================================================================================

    public Phase phase() {
        return phase;
    }

    public double current() {
        return current;
    }

    public long betOf(UUID id) {
        return bets.getOrDefault(id, 0L);
    }

    public long lastBetOf(UUID id) {
        return lastBet.getOrDefault(id, 0L);
    }

    /** Noch im Spiel (gesetzt, Runde läuft, nicht ausgestiegen). */
    public boolean live(UUID id) {
        return phase == Phase.RUNNING && bets.containsKey(id) && !cashed.containsKey(id);
    }

    public int secondsLeft() {
        long wait = plugin.getConfig().getLong("casino.crash.wait-seconds", 10) * 20;
        return (int) Math.max(0, (wait - ticks + 19) / 20);
    }

    public long minBet() {
        return plugin.getConfig().getLong("casino.crash.min-bet", 10);
    }

    public long maxBet() {
        return plugin.getConfig().getLong("casino.crash.max-bet", 100000);
    }

    public void open(Player p) {
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) {
            CasinoText.error(p, blocked);
            return;
        }
        new CrashMenu(plugin, this).open(p);
    }

    /** Setzt (weitere) Chips für die kommende Runde. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public String bet(Player p, long amount) {
        String blocked = plugin.casino().checkMode(p);
        if (blocked != null) return blocked;
        String closed = plugin.casino().closedMessage();
        if (closed != null) return closed;
        if (screen() == null) return "Hier steht noch kein Crash-Bildschirm.";
        if (phase == Phase.RUNNING) return "Die Runde läuft schon. Setz für die nächste, sobald BEREIT steht.";
        UUID id = p.getUniqueId();
        long have = bets.getOrDefault(id, 0L);
        if (amount < 1) return "Der Einsatz muss größer als 0 sein.";
        if (have + amount < minBet()) return "Mindestens " + CasinoText.chips(minBet()) + ".";
        if (have + amount > maxBet()) return "Höchstens " + CasinoText.chips(maxBet()) + " je Runde.";
        if (!plugin.casino().takeChips(id, amount)) return "So viele Chips hast du nicht. Tausch sie an der Kasse.";
        long feed = Math.round(amount * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
        if (feed > 0) plugin.casino().addJackpot(feed);
        bets.put(id, have + amount);
        lastBet.put(id, have + amount);
        if (phase == Phase.IDLE) {
            phase = Phase.BETTING;
            ticks = 0;
        }
        save();
        p.sendMessage(prefix().append(Component.text("Einsatz ", NamedTextColor.GRAY))
                .append(Component.text(CasinoText.chips(have + amount), YELLOW))
                .append(Component.text(" gesetzt. Sneaken + Klick, Chat-Knopf oder /cashout", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.7f, 1.3f);
        showState();
        return null;
    }

    /** Steigt aus der laufenden Runde aus. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    public String cashout(Player p) {
        UUID id = p.getUniqueId();
        if (phase != Phase.RUNNING) return bets.containsKey(id) ? "Die Runde hat noch nicht angefangen." : "Du hast keinen laufenden Einsatz.";
        Long bet = bets.get(id);
        if (bet == null) return "Du bist in dieser Runde nicht dabei.";
        if (cashed.containsKey(id)) return "Du bist schon ausgestiegen.";
        pay(id, bet, current);
        return null;
    }

    private void pay(UUID id, long bet, double at) {
        long payout = (long) Math.floor(bet * at);
        cashed.put(id, at);
        plugin.casino().giveChips(id, payout);
        plugin.casinoLog().record("Crash", id, bet, payout);
        save();
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.sendMessage(prefix().append(Component.text("Ausgestiegen bei ", NamedTextColor.GRAY))
                    .append(Component.text(fmt(at), GREEN).decorate(TextDecoration.BOLD))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("+" + CasinoText.chips(payout - bet), GREEN)));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
            p.sendActionBar(Component.text("+" + CasinoText.chips(payout - bet), GREEN).append(Component.text(" · bei " + fmt(at), LIGHT)));
        }
        CasinoFx.show(plugin, id, fxAt(), at >= 5 ? CasinoFx.Result.BIG_WIN : CasinoFx.Result.WIN, payout - bet);
    }

    private void tick() {
        CrashScreen s = screen();
        if (s == null) return;
        ticks += (int) TICK;
        switch (phase) {
            case IDLE -> {
                if (ticks % 40 == 0) refreshMenus();
            }
            case BETTING -> {
                long wait = plugin.getConfig().getLong("casino.crash.wait-seconds", 10) * 20;
                if (ticks >= wait) {
                    start();
                } else if (ticks % 20 == 0) {
                    showState();
                    refreshMenus();
                }
            }
            case RUNNING -> {
                double rate = plugin.getConfig().getDouble("casino.crash.rate", 0.07);
                double cap = plugin.getConfig().getDouble("casino.crash.max-multiplier", 50);
                double next = Math.exp(rate * ticks / 20.0);
                if (next >= crashAt) {
                    current = crashAt;
                    if (crashAt >= cap) reachMax(cap);
                    else crash();
                } else {
                    current = next;
                    showState();
                    if (ticks % 4 == 0) {
                        for (UUID id : bets.keySet()) {
                            if (cashed.containsKey(id)) continue;
                            Player p = Bukkit.getPlayer(id);
                            if (p != null) {
                                p.sendActionBar(Component.text(fmt(current), GREEN).decorate(TextDecoration.BOLD)
                                        .append(Component.text(" · Auszahlen: ", NamedTextColor.GRAY))
                                        .append(Component.text(CasinoText.chips((long) Math.floor(bets.get(id) * current)), YELLOW))
                                        .append(Component.text(" · Sneaken + Klick", NamedTextColor.DARK_GRAY)));
                            }
                        }
                    }
                    if (ticks % 10 == 0) refreshMenus();
                }
            }
            case CRASHED -> {
                if (ticks >= CRASH_SHOWN_TICKS) {
                    phase = bets.isEmpty() ? Phase.IDLE : Phase.BETTING;
                    ticks = 0;
                    s.hideCurve();
                    showState();
                    refreshMenus();
                }
            }
        }
    }

    private void start() {
        phase = Phase.RUNNING;
        ticks = 0;
        current = 1.0;
        maxed = false;
        double edge = plugin.getConfig().getDouble("casino.crash.house-edge", 0.03);
        double cap = plugin.getConfig().getDouble("casino.crash.max-multiplier", 50);
        double u = RNG.nextDouble();
        double c = Math.floor((1 - edge) / (1 - u) * 100) / 100;
        crashAt = Math.max(1.0, Math.min(cap, c));
        Component button = Component.text("[AUSZAHLEN]", GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/cashout"))
                .hoverEvent(HoverEvent.showText(Component.text("Jetzt aussteigen", GREEN)));
        for (UUID id : bets.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.sendMessage(prefix().append(Component.text("Runde läuft! ", NamedTextColor.GRAY)).append(button));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        }
        showState();
        refreshMenus();
    }

    private void crash() {
        phase = Phase.CRASHED;
        ticks = 0;
        lostPlayers = 0;
        lostChips = 0;
        for (Map.Entry<UUID, Long> e : bets.entrySet()) {
            if (cashed.containsKey(e.getKey())) continue;
            lostPlayers++;
            lostChips += e.getValue();
            Player online = Bukkit.getPlayer(e.getKey());
            if (online != null) {
                online.sendMessage(prefix().append(Component.text("Crash bei " + fmt(crashAt) + " — Einsatz -"
                        + CasinoText.chips(e.getValue()) + " weg.", RED)));
                online.sendActionBar(Component.text("Crash bei " + fmt(crashAt), RED).decorate(TextDecoration.BOLD));
            }
            CasinoFx.show(plugin, e.getKey(), fxAt(), CasinoFx.Result.LOSE, -e.getValue());
            plugin.casinoLog().record("Crash", e.getKey(), e.getValue(), 0);
        }
        Location fx = fxAt();
        if (fx != null) {
            fx.getWorld().playSound(fx, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            de.kuby.kubyplugins.util.Fx.particle(plugin, fx, org.bukkit.Particle.SMOKE, 30, 1.2, 0.6, 0.1, 0.02);
        }
        endRound();
    }

    /** Höchstwert erreicht: alle, die noch drin sind, werden dort ausgezahlt. */
    private void reachMax(double cap) {
        maxed = true;
        for (Map.Entry<UUID, Long> e : new ArrayList<>(bets.entrySet())) {
            if (!cashed.containsKey(e.getKey())) pay(e.getKey(), e.getValue(), cap);
        }
        phase = Phase.CRASHED;
        ticks = 0;
        lostPlayers = 0;
        lostChips = 0;
        endRound();
    }

    private void endRound() {
        history.addFirst(crashAt);
        while (history.size() > 8) history.removeLast();
        bets.clear();
        cashed.clear();
        save();
        showState();
        if (screen != null) screen.rounds(roundsText());
        refreshMenus();
    }

    private Location fxAt() {
        CrashScreen s = screen();
        return s == null ? null : s.center().add(0, 0.4, 0);
    }

    // =================================================================================================================
    // Anzeige
    // =================================================================================================================

    public static String fmt(double m) {
        return String.format(Locale.GERMAN, "%.2fx", m);
    }

    public static Component prefix() {
        return Component.text("CRASH", TextColor.color(0xF2502A)).decorate(TextDecoration.BOLD)
                .append(Component.text(" ▶ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false));
    }

    private static Component bigTitle(String text) {
        return Component.text(de.kuby.kubyplugins.ui.Canvas.titleChars(text, false))
                .font(net.kyori.adventure.key.Key.key("kuby", "title")).color(NamedTextColor.WHITE);
    }

    private static final Component HINT = Component.text("Sneaken + Klick = Auszahlen", TextColor.color(0x9A9AA4));

    private void showState() {
        CrashScreen s = screen();
        if (s == null || !s.ready()) return;
        switch (phase) {
            case IDLE -> {
                s.texts(bigTitle("BEREIT"), 0.6f, Component.text("Warte auf Einsätze..", YELLOW), HINT);
                s.hideCurve();
            }
            case BETTING -> {
                s.texts(bigTitle("BEREIT"), 0.6f, Component.text("Einsätze", YELLOW)
                        .append(Component.text(" — ", NamedTextColor.GRAY)).append(Component.text(secondsLeft() + "s", LIGHT)), HINT);
                s.hideCurve();
            }
            case RUNNING -> {
                s.texts(Component.text(fmt(current), GREEN).decorate(TextDecoration.BOLD), 1.0f,
                        Component.text("Läuft", NamedTextColor.GRAY), HINT);
                plot(s, false, Component.text(fmt(current), GREEN).decorate(TextDecoration.BOLD));
            }
            case CRASHED -> {
                if (maxed) {
                    s.texts(Component.text(fmt(crashAt), ORANGE).decorate(TextDecoration.BOLD), 1.0f,
                            Component.text("MAXIMUM", ORANGE).decorate(TextDecoration.BOLD)
                                    .append(Component.text(" — alle ausgezahlt", LIGHT).decoration(TextDecoration.BOLD, false)),
                            Component.empty());
                    plot(s, false, Component.text(fmt(crashAt), ORANGE).decorate(TextDecoration.BOLD));
                } else {
                    Component line = Component.text("CRASH", RED).decorate(TextDecoration.BOLD)
                            .append(Component.text(" — ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
                            .append(Component.text(lostPlayers == 0 ? "alle rechtzeitig ausgestiegen"
                                    : lostPlayers + " Spieler verloren " + CasinoText.chips(lostChips), LIGHT)
                                    .decoration(TextDecoration.BOLD, false));
                    s.texts(Component.text(fmt(crashAt), RED).decorate(TextDecoration.BOLD), 1.0f, line, Component.empty());
                    plot(s, true, Component.text(fmt(crashAt), RED).decorate(TextDecoration.BOLD));
                }
            }
        }
        updateSigns();
    }

    /** Kurve bis zum aktuellen Wert: x = Zeit, y = Multiplikator, beide Achsen wachsen mit. */
    private void plot(CrashScreen s, boolean red, Component label) {
        double rate = plugin.getConfig().getDouble("casino.crash.rate", 0.07);
        double t = Math.log(Math.max(1.0, current)) / rate;
        if (t < 0.05) {
            s.hideCurve();
            return;
        }
        double tMax = Math.max(10, t * 1.06);
        double mMax = Math.max(1.8, 1 + (current - 1) * 1.1);
        int n = CrashScreen.SEGMENTS;
        double[] xs = new double[n + 1], ys = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double ti = t * i / n;
            double m = Math.exp(rate * ti);
            xs[i] = s.plotLeft() + ti / tMax * s.plotWidth();
            ys[i] = s.plotBottom() + (m - 1) / (mMax - 1) * s.plotHeight();
        }
        s.curve(xs, ys, red, label);
    }

    private Component roundsText() {
        TextComponent.Builder b = Component.text();
        b.append(Component.text("LETZTE RUNDEN", ORANGE).decorate(TextDecoration.BOLD)).append(Component.newline());
        int i = 0;
        for (double m : history) {
            if (i++ >= 6) break;
            if (i > 1) b.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
            b.append(Component.text(fmt(m), m < 2 ? TextColor.color(0xFF6A6A) : GREEN));
        }
        if (history.isEmpty()) b.append(Component.text("noch keine", NamedTextColor.GRAY));
        return b.build();
    }

    // =================================================================================================================
    // Schilder
    // =================================================================================================================

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private Component[] signLines() {
        return switch (phase) {
            case IDLE -> new Component[]{Component.text("CRASH", ORANGE).decorate(TextDecoration.BOLD),
                    Component.text("Warte auf", LIGHT), Component.text("Einsätze", LIGHT), Component.empty()};
            case BETTING -> new Component[]{Component.text("CRASH", ORANGE).decorate(TextDecoration.BOLD),
                    Component.text("Einsatz", LIGHT), Component.text(secondsLeft() + "s", LIGHT), Component.empty()};
            case RUNNING -> new Component[]{Component.text("CASHOUT", GREEN).decorate(TextDecoration.BOLD),
                    Component.text("Klick oder Schlag", LIGHT), Component.text(fmt(current), GREEN).decorate(TextDecoration.BOLD),
                    Component.text("auszahlen", TextColor.color(0xC8C8C8))};
            case CRASHED -> maxed
                    ? new Component[]{Component.text("MAXIMUM", ORANGE).decorate(TextDecoration.BOLD),
                    Component.text("bei", LIGHT), Component.text(fmt(crashAt), ORANGE).decorate(TextDecoration.BOLD), Component.empty()}
                    : new Component[]{Component.text("CRASH", RED).decorate(TextDecoration.BOLD),
                    Component.text("bei", LIGHT), Component.text(fmt(crashAt), RED).decorate(TextDecoration.BOLD), Component.empty()};
        };
    }

    private void updateSigns() {
        if (signs.isEmpty()) return;
        // laufende Runde: Schilder höchstens alle 0,4 s neu schreiben
        String state = phase + ":" + (phase == Phase.RUNNING ? fmt(current) : phase == Phase.BETTING ? secondsLeft() : "");
        if (state.equals(lastSignState) || (phase == Phase.RUNNING && ticks % 8 != 0)) return;
        lastSignState = state;
        Component[] lines = signLines();
        for (String k : signs) {
            Location l = parseLoc(k);
            if (l == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            if (!(l.getBlock().getState() instanceof Sign sign)) continue;
            SignSide side = sign.getSide(Side.FRONT);
            for (int i = 0; i < 4; i++) side.line(i, lines[i]);
            side.setGlowingText(true);
            sign.setWaxed(true);
            sign.update(true, false);
        }
    }

    private void signClick(Player p) {
        if (live(p.getUniqueId())) {
            String err = cashout(p);
            if (err != null) CasinoText.error(p, err);
        } else {
            open(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b != null && signs.contains(key(b)) && (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK)) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.OFF_HAND) return;
            signClick(p);
            return;
        }
        // Schleichen + Klick irgendwo in der Nähe des Bildschirms steigt aus
        if (e.getHand() == EquipmentSlot.OFF_HAND || !p.isSneaking() || !live(p.getUniqueId())) return;
        CrashScreen s = screen();
        if (s == null || !s.center().getWorld().equals(p.getWorld()) || s.center().distanceSquared(p.getLocation()) > 30 * 30) return;
        if (e.getAction() == Action.PHYSICAL) return;
        e.setCancelled(true);
        cashout(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (signs.contains(key(e.getBlock()))) {
            e.setCancelled(true);
            if (e.getPlayer().hasPermission("kuby.admin")) {
                Msg.send(e.getPlayer(), "<gray>Crash-Schild: zuerst <white>/casino crash schild entfernen</white> (draufschauen).");
            }
        }
    }

    /** /cashout */
    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        String err = cashout(p);
        if (err != null) CasinoText.error(p, err);
        return true;
    }

    private void refreshMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof CrashMenu m) m.refresh(p);
        }
    }

    // =================================================================================================================
    // Info-Hologramm
    // =================================================================================================================

    private void buildInfo() {
        removeInfo();
        Location at = parseLoc(infoRaw);
        if (at == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        float yaw = at.getYaw();
        at.setYaw(0);
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(-yaw));
        int[] infoColors = {0xFFE23C, 0xFFCC34, 0xFFB02C, 0xFF9426, 0xFF7A26, 0xFFFFFF, 0xF2502A, 0xEE4430, 0xEA3832, 0xE62E32};
        spawnInfo(at.clone().add(0, 0.95, 0), q, CrashScreen.logo("CRASH INFO", infoColors), 0.7f);
        net.kyori.adventure.key.Key icons = net.kyori.adventure.key.Key.key("kuby", "k/i0");
        TextColor dim = TextColor.color(0x8E8E96);
        Component dot = Component.text(" · ", NamedTextColor.DARK_GRAY);
        Component body = Component.text()
                .append(Component.text("So funktioniert das Spiel", dim)).append(Component.newline())
                .append(Component.text(String.valueOf(de.kuby.kubyplugins.ui.KitData.SmallIcon.C_PINK.c)).font(icons).color(NamedTextColor.WHITE))
                .append(dot).append(Component.text("Setz deine ", LIGHT)).append(Component.text("Chips", YELLOW))
                .append(Component.text(" vor dem Start", LIGHT)).append(Component.newline())
                .append(Component.text("⬆", TextColor.color(0xC8CCD8))).append(dot).append(Component.text("Der ", LIGHT))
                .append(Component.text("Multiplikator", ORANGE)).append(Component.text(" steigt immer weiter", LIGHT)).append(Component.newline())
                .append(Component.text("✔", GREEN)).append(dot)
                .append(Component.text("Steig aus, und du bekommst Einsatz mal Multiplikator", LIGHT)).append(Component.newline())
                .append(Component.text("✘", RED)).append(dot).append(Component.text("Crasht es vorher, ist dein Einsatz weg", LIGHT))
                .append(Component.newline())
                .append(Component.text("✦ Wer zu lange wartet, verliert alles ✦", TextColor.color(0xF2C94C)))
                .build();
        spawnInfo(at, q, body, 0.55f);
    }

    private void spawnInfo(Location at, Quaternionf q, Component text, float scale) {
        at.getWorld().spawn(at, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setLineWidth(400);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(q), new Vector3f(scale, scale, scale), new Quaternionf()));
            d.getPersistentDataContainer().set(infoTag, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void removeInfo() {
        Location at = parseLoc(infoRaw);
        if (at == null) return;
        for (Entity e : at.getWorld().getNearbyEntities(at, 3, 3, 3)) {
            if (e.getPersistentDataContainer().has(infoTag, PersistentDataType.BYTE)) e.remove();
        }
    }

    // =================================================================================================================
    // Befehle
    // =================================================================================================================

    /** Punkt auf der Blockseite, auf die der Spieler schaut (etwas davor), mit Blickrichtung zum Spieler. */
    private static Location aimed(Player p, double out) {
        RayTraceResult hit = p.rayTraceBlocks(8);
        if (hit == null || hit.getHitBlockFace() == null) return null;
        Vector n = hit.getHitBlockFace().getDirection();
        Vector pos = hit.getHitPosition().add(n.clone().multiply(out));
        Location l = pos.toLocation(p.getWorld());
        l.setYaw((float) Math.toDegrees(Math.atan2(-n.getX(), n.getZ())));
        return l;
    }

    /** /casino crash [bildschirm [Breite] [Höhe] | titel | runden | info | schild [entfernen] | entfernen] */
    public void command(Player p, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        if (sub.isEmpty() || !p.hasPermission("kuby.admin")) {
            open(p);
            return;
        }
        switch (sub) {
            case "bildschirm" -> {
                double w = 6, h = 3;
                try {
                    if (args.length > 2) w = Math.max(3, Math.min(16, Double.parseDouble(args[2].replace(',', '.'))));
                    if (args.length > 3) h = Math.max(2, Math.min(9, Double.parseDouble(args[3].replace(',', '.'))));
                } catch (NumberFormatException ex) {
                    Msg.send(p, "<red>Nutzung: /casino crash bildschirm [Breite] [Höhe]");
                    return;
                }
                RayTraceResult hit = p.rayTraceBlocks(16);
                if (hit == null || hit.getHitBlockFace() == null || Math.abs(hit.getHitBlockFace().getDirection().getY()) > 0.5) {
                    Msg.send(p, "<red>Schau auf die Mitte der dunklen Bildfläche (eine senkrechte Blockseite).");
                    return;
                }
                Vector n = hit.getHitBlockFace().getDirection();
                Vector pos = hit.getHitPosition();
                Vector along = new Vector(n.getZ(), 0, -n.getX());
                double a = pos.getX() * along.getX() + pos.getZ() * along.getZ();
                pos.add(along.clone().multiply(Math.round(a * 2) / 2.0 - a));
                pos.setY(Math.round(pos.getY() * 2) / 2.0);
                pos.add(n.clone().multiply(0.01));
                float yaw = (float) Math.toDegrees(Math.atan2(-n.getX(), n.getZ()));
                if (screen != null) screen.removeAll();
                screen = null;
                screenRaw = p.getWorld().getName() + ";" + pos.getX() + ";" + pos.getY() + ";" + pos.getZ() + ";" + yaw + ";" + w + ";" + h;
                save();
                build();
                Msg.send(p, "<green>Crash-Bildschirm aufgebaut (" + fmtNum(w) + " x " + fmtNum(h) + "). Titel und „Letzte Runden“"
                        + " verschieben: /casino crash titel | runden (draufschauen). Schilder: /casino crash schild.");
            }
            case "titel", "runden" -> {
                if (screen() == null) {
                    Msg.send(p, "<red>Zuerst den Bildschirm aufbauen: /casino crash bildschirm");
                    return;
                }
                Location l = aimed(p, 0.06);
                if (l == null) {
                    Msg.send(p, "<red>Schau auf die Blockseite, an die der Text soll.");
                    return;
                }
                if (sub.equals("titel")) titleRaw = locString(l);
                else roundsRaw = locString(l);
                screen.removeAll();
                screen = null;
                save();
                build();
                Msg.send(p, "<green>" + (sub.equals("titel") ? "Titel" : "„Letzte Runden“") + " verschoben.");
            }
            case "info" -> {
                Location l = aimed(p, 0.08);
                if (l == null) {
                    l = p.getEyeLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
                    l.setYaw(p.getLocation().getYaw() + 180);
                }
                removeInfo();
                infoRaw = locString(l) + ";" + l.getYaw();
                save();
                buildInfo();
                Msg.send(p, "<green>Crash-Info aufgestellt.");
            }
            case "schild" -> {
                Block b = p.getTargetBlockExact(6);
                if (b == null || !(b.getState() instanceof Sign)) {
                    Msg.send(p, "<red>Schau auf ein Schild.");
                    return;
                }
                if (args.length > 2 && args[2].equalsIgnoreCase("entfernen")) {
                    Msg.send(p, signs.remove(key(b)) ? "<green>Schild ist kein Crash-Schild mehr." : "<red>Das ist kein Crash-Schild.");
                } else {
                    signs.add(key(b));
                    lastSignState = "";
                    updateSigns();
                    Msg.send(p, "<green>Crash-Schild hinzugefügt (" + signs.size() + "). Klick oder Schlag: setzen bzw. auszahlen.");
                }
                save();
            }
            case "entfernen" -> {
                if (screen != null) screen.removeAll();
                removeInfo();
                screen = null;
                screenRaw = null;
                titleRaw = null;
                roundsRaw = null;
                infoRaw = null;
                signs.clear();
                save();
                Msg.send(p, "<green>Crash-Bildschirm, Info und Schilder entfernt (laufende Einsätze bleiben bis Rundenende).");
            }
            case "npc" -> plugin.casinoNpc().command(p, new String[]{"npc", "crash"});
            default -> Msg.send(p, "<gray>Crash aufbauen: <white>/casino crash bildschirm [Breite] [Höhe]</white> (auf die Bildmitte"
                    + " schauen), <white>titel</white>, <white>runden</white>, <white>info</white>, <white>schild [entfernen]</white>,"
                    + " <white>npc</white>, <white>entfernen</white>.");
        }
    }

    private static String fmtNum(double v) {
        return String.format(Locale.GERMAN, "%.1f", v);
    }

}
