package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crash am Automaten. Im Ruhezustand steht "BEREIT". Rechtsklick steigt ein (Einsatz aus den Chips), nach einer Wartezeit
 * steigt der Multiplikator. Rechtsklick während der Runde steigt aus und zahlt Einsatz mal Multiplikator.
 * Der Crash-Punkt steht beim Start der Runde fest (1 % Hausvorteil). Wer beim Crash noch drin ist, verliert den Einsatz,
 * auch wenn er ausgeloggt ist. Beim Herunterfahren bekommen alle, die noch drin sind, ihren Einsatz zurück.
 */
public final class CrashService {

    private enum State { IDLE, BETTING, RUNNING, CRASHED }

    private static final String SURVIVAL = "survival";
    private static final long TICK = 2L;
    private static final int RESULT_SHOWN_TICKS = 100;

    private final KubyPlugins plugin;
    private final File file;
    private final NamespacedKey tag;
    private final Map<UUID, Long> bets = new LinkedHashMap<>();
    private final Set<UUID> cashed = new HashSet<>();
    private String automat;
    private TextDisplay display;
    private BukkitTask task;
    private State state = State.IDLE;
    private int ticks;
    private double crashAt;
    private double current = 1.0;

    public CrashService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crash.yml");
        this.tag = new NamespacedKey(plugin, "kp_crash");
        automat = YamlConfiguration.loadConfiguration(file).getString("automat");
    }

    /** Entfernt Reste der letzten Sitzung und zeigt den Automaten im Ruhezustand. */
    public synchronized void respawn() {
        Location m = machine();
        if (m == null || m.getWorld() == null) return;
        for (TextDisplay d : m.getWorld().getNearbyEntitiesByType(TextDisplay.class, m, 12)) {
            if (d.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) d.remove();
        }
        display = null;
        update();
    }

    public synchronized void setMachine(Location block) {
        automat = block.getWorld().getName() + ";" + block.getBlockX() + ";" + block.getBlockY() + ";" + block.getBlockZ();
        save();
        if (display != null) display.remove();
        display = null;
        update();
    }

    public boolean hasMachine() {
        return machine() != null;
    }

    public boolean isMachine(Location l) {
        Location m = machine();
        return m != null && l.getWorld() != null && l.getWorld().equals(m.getWorld())
                && l.getBlockX() == m.getBlockX() && l.getBlockY() == m.getBlockY() && l.getBlockZ() == m.getBlockZ();
    }

    /** Rechtsklick auf den Automaten: vor der Runde einsteigen, während der Runde aussteigen. */
    public synchronized String click(Player p) {
        return switch (state) {
            case IDLE, BETTING -> join(p);
            case RUNNING -> cashOut(p);
            case CRASHED -> "Die nächste Runde startet gleich.";
        };
    }

    /** Gibt allen, die noch drin sind, beim Herunterfahren den Einsatz zurück. */
    public synchronized void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, Long> e : bets.entrySet()) {
            if (!cashed.contains(e.getKey())) plugin.casino().giveChips(e.getKey(), e.getValue());
        }
        bets.clear();
        cashed.clear();
        state = State.IDLE;
    }

    private String join(Player p) {
        UUID id = p.getUniqueId();
        String closed = plugin.casino().closedMessage();
        if (closed != null) return closed;
        if (!SURVIVAL.equals(plugin.modes().modeOf(id))) return "Crash gibt es nur in Survival.";
        if (bets.containsKey(id)) return "Du bist schon dabei. Warte auf den Start.";
        long bet = plugin.getConfig().getLong("casino.crash.bet", 10);
        if (!plugin.casino().takeChips(id, bet)) {
            return "Du brauchst " + bet + " Chips für eine Runde. Tausch sie am Kassentisch.";
        }
        long feed = Math.round(bet * plugin.casino().price()
                * plugin.getConfig().getDouble("casino.jackpot-share", 0.01));
        if (feed > 0) plugin.casino().addJackpot(feed);
        bets.put(id, bet);
        if (state == State.IDLE) {
            state = State.BETTING;
            ticks = 0;
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK, TICK);
        }
        Msg.send(p, "<green>Du bist dabei mit " + bet + " Chips. <gray>Rechtsklick während der Runde zum Aussteigen.");
        update();
        return null;
    }

    private String cashOut(Player p) {
        UUID id = p.getUniqueId();
        Long bet = bets.get(id);
        if (bet == null) return "Du bist in dieser Runde nicht dabei. Warte auf die nächste.";
        if (cashed.contains(id)) return "Du bist schon ausgestiegen.";
        long payout = (long) Math.floor(bet * current);
        cashed.add(id);
        plugin.casino().giveChips(id, payout);
        Msg.send(p, "<green>Ausgestiegen bei " + format(current) + ": +" + (payout - bet) + " Chips"
                + "  <gray>Chips: " + plugin.casino().chips(id));
        CasinoFx.Result fx = payout == bet ? CasinoFx.Result.PUSH
                : current >= 5 ? CasinoFx.Result.BIG_WIN : CasinoFx.Result.WIN;
        CasinoFx.show(plugin, id, fxAt(), fx, payout - bet);
        plugin.casinoLog().record("Crash", id, bet, payout);
        return null;
    }

    private synchronized void tick() {
        ticks += (int) TICK;
        switch (state) {
            case BETTING -> {
                long wait = plugin.getConfig().getLong("casino.crash.wait-seconds", 10) * 20;
                if (ticks >= wait) {
                    startRun();
                } else if (ticks % 20 == 0) {
                    update();
                }
            }
            case RUNNING -> {
                double rate = plugin.getConfig().getDouble("casino.crash.rate", 0.07);
                double next = Math.exp(rate * ticks / 20.0);
                if (next >= crashAt) {
                    crash();
                } else {
                    current = next;
                    update();
                }
            }
            case CRASHED -> {
                if (ticks >= RESULT_SHOWN_TICKS) {
                    state = State.IDLE;
                    if (task != null) task.cancel();
                    task = null;
                    update();
                }
            }
            default -> {
            }
        }
    }

    private void startRun() {
        state = State.RUNNING;
        ticks = 0;
        current = 1.0;
        crashAt = rollCrash();
        update();
    }

    /** Crash-Punkt mit 1 % Hausvorteil: P(Crash erst ab m) = 0,99 / m. Gedeckelt durch max-multiplier. */
    private double rollCrash() {
        double cap = plugin.getConfig().getDouble("casino.crash.max-multiplier", 50);
        double u = ThreadLocalRandom.current().nextDouble();
        double c = Math.floor(0.99 / (1 - u) * 100) / 100;
        return Math.max(1.0, Math.min(cap, c));
    }

    private void crash() {
        state = State.CRASHED;
        ticks = 0;
        current = crashAt;
        for (Map.Entry<UUID, Long> e : bets.entrySet()) {
            if (cashed.contains(e.getKey())) continue;
            Player online = Bukkit.getPlayer(e.getKey());
            if (online != null) {
                Msg.send(online, "<red>Crash bei " + format(crashAt) + ". -" + e.getValue() + " Chips");
            }
            CasinoFx.show(plugin, e.getKey(), fxAt(), CasinoFx.Result.LOSE, -e.getValue());
            plugin.casinoLog().record("Crash", e.getKey(), e.getValue(), 0);
        }
        bets.clear();
        cashed.clear();
        update();
    }

    private Location fxAt() {
        Location m = machine();
        return m == null ? null : m.clone().add(0, 3.4, 0);
    }

    private String format(double m) {
        return String.format(Locale.ROOT, "%.2fx", m);
    }

    /** Aktualisiert den Text am Automaten passend zum Zustand. */
    private void update() {
        String body = switch (state) {
            case IDLE -> "<green><b>BEREIT</b>\n<gray>Rechtsklick zum Einsteigen";
            case BETTING -> {
                long wait = plugin.getConfig().getLong("casino.crash.wait-seconds", 10);
                long left = Math.max(0, wait - ticks / 20);
                yield "<green><b>BEREIT</b>\n<gray>Start in " + left + " s · " + bets.size() + " Spieler";
            }
            case RUNNING -> "<white><b>" + format(current) + "</b>\n<gray>Rechtsklick zum Aussteigen";
            case CRASHED -> "<red><b>CRASH bei " + format(crashAt) + "</b>";
        };
        setText("<gold><b>CRASH</b>\n" + body);
    }

    private void setText(String mini) {
        Location m = machine();
        if (m == null || m.getWorld() == null) return;
        if (!m.getWorld().isChunkLoaded(m.getBlockX() >> 4, m.getBlockZ() >> 4)) return;
        if (display == null || !display.isValid()) {
            display = m.getWorld().spawn(m.clone().add(0, 2.2, 0), TextDisplay.class, e -> {
                e.setBillboard(Display.Billboard.VERTICAL);
                e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
            });
        }
        display.text(Msg.parse(mini));
    }

    private Location machine() {
        if (automat == null) return null;
        String[] p = automat.split(";");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Integer.parseInt(p[1]) + 0.5, Integer.parseInt(p[2]), Integer.parseInt(p[3]) + 0.5);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void save() {
        YamlConfiguration out = new YamlConfiguration();
        if (automat != null) out.set("automat", automat);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("crash.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
