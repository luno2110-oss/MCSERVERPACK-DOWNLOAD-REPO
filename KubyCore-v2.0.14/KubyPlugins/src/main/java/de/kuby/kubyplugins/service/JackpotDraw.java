package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wöchentliche Jackpot-Verlosung (Standard: Samstag 18 Uhr, Europe/Berlin). Dabei ist jeder, der seit der letzten
 * Ziehung im Casino gespielt hat. Der Gewinn wird vorgemerkt und erst gutgeschrieben, wenn der Gewinner online und
 * in Survival ist, weil das Geld pro Modus getrennt ist. War der Server zur Ziehung aus, wird beim Start nachgezogen.
 */
public final class JackpotDraw {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final String SURVIVAL = "survival";
    private static final long CHECK_TICKS = 20L * 60;

    private final KubyPlugins plugin;
    private final File file;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, Long> pending = new HashMap<>();
    private long lastDraw;
    private String lastWinner;
    private long lastAmount;

    public JackpotDraw(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jackpot-draw.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yml.getStringList("participants")) {
            try {
                participants.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection p = yml.getConfigurationSection("pending");
        if (p != null) {
            for (String key : p.getKeys(false)) {
                try {
                    pending.put(UUID.fromString(key), p.getLong(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        // Beim allerersten Start nicht sofort ziehen, sondern ab jetzt zählen.
        lastDraw = yml.getLong("last", System.currentTimeMillis());
        lastWinner = yml.getString("letzter-gewinner");
        lastAmount = yml.getLong("letzter-betrag", 0);
    }

    /** Startet die Prüfung jede Minute: fällige Ziehung und vorgemerkte Gewinne. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 200L, CHECK_TICKS);
    }

    /** Merkt den Spieler für die nächste Ziehung vor (bei jedem Einsatz). */
    public synchronized void join(UUID id) {
        if (participants.add(id)) save();
    }

    public synchronized int participantCount() {
        return participants.size();
    }

    public synchronized boolean isParticipant(UUID id) {
        return participants.contains(id);
    }

    public synchronized String lastWinner() {
        return lastWinner;
    }

    public synchronized long lastAmount() {
        return lastAmount;
    }

    /** Nächste Ziehung, z. B. "Samstag 18:00". */
    public String nextDrawLong() {
        ZonedDateTime next = nextDraw(ZonedDateTime.now(ZONE));
        return next.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.GERMAN)
                + " " + String.format("%02d:%02d", next.getHour(), next.getMinute());
    }

    /** Restzeit bis zur Ziehung, z. B. "22 Std. 46 Min." oder "2 Tage 3 Std.". */
    public String timeLeft() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        long min = Math.max(0, java.time.Duration.between(now, nextDraw(now)).toMinutes());
        long d = min / (60 * 24), h = (min / 60) % 24, m = min % 60;
        if (d > 0) return d + (d == 1 ? " Tag " : " Tage ") + h + " Std.";
        if (h > 0) return h + " Std. " + m + " Min.";
        return m + " Min.";
    }

    /** Nächste Ziehung, z. B. "Sa 19:00". */
    public String nextDrawLabel() {
        ZonedDateTime next = nextDraw(ZonedDateTime.now(ZONE));
        return next.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "")
                + " " + String.format("%02d:%02d", next.getHour(), next.getMinute());
    }

    /** Zieht sofort (Admin). */
    public synchronized void drawNow() {
        draw();
        lastDraw = System.currentTimeMillis();
        save();
    }

    private synchronized void tick() {
        payPending();
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime due = nextDraw(now).minusWeeks(1);
        if (lastDraw < due.toInstant().toEpochMilli()) {
            draw();
            lastDraw = now.toInstant().toEpochMilli();
            save();
        }
    }

    private void draw() {
        if (participants.isEmpty()) {
            Bukkit.broadcast(Msg.parse("<gold>Casino: <gray>Keine Teilnehmer bei der Jackpot-Ziehung. Der Jackpot wächst weiter auf <yellow>"
                    + Msg.coins(plugin.casino().jackpot()) + "$"));
            return;
        }
        List<UUID> list = new ArrayList<>(participants);
        UUID winner = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        participants.clear();
        long money = plugin.casino().claimJackpot();
        // Gewinn in Chips (wie im Clip), kommt sofort auf das Chip-Konto, auch offline
        plugin.casino().giveChips(winner, money);
        String name = Bukkit.getOfflinePlayer(winner).getName();
        lastWinner = name == null ? "Jemand" : name;
        lastAmount = money;
        Bukkit.broadcast(Msg.parse("<#F2B829><b>JACKPOT</b></#F2B829> <dark_gray>▶</dark_gray> <white>" + lastWinner
                + " <gray>knackt den Tresor: <yellow>" + Msg.coins(money) + " Chips <gray>(" + list.size() + " Teilnehmer)"));
        Player online = Bukkit.getPlayer(winner);
        if (online != null) Sfx.play(plugin, online, Sfx.Kind.JACKPOT);
        plugin.casinoLog().recordJackpot(winner, money);
        Location kasse = plugin.casino().kasse();
        CasinoFx.jackpot(plugin, winner, kasse == null ? null : kasse.clone().add(0, 3.0, 0), money);
        payPending();
    }

    /** Schreibt vorgemerkte Gewinne gut, sobald der Gewinner online und in Survival ist. */
    private void payPending() {
        if (pending.isEmpty()) return;
        boolean changed = false;
        for (UUID id : new ArrayList<>(pending.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !SURVIVAL.equals(plugin.modes().modeOf(id))) continue;
            long money = pending.remove(id);
            plugin.economy().deposit(id, money);
            Msg.send(p, "<gold><b>JACKPOT!</b> <yellow>+" + Msg.coins(money) + "$ <gray>wurden deinem Survival-Konto gutgeschrieben.");
            Sfx.play(plugin, p, Sfx.Kind.JACKPOT);
            changed = true;
        }
        if (changed) save();
    }

    private ZonedDateTime nextDraw(ZonedDateTime now) {
        DayOfWeek day;
        try {
            day = DayOfWeek.valueOf(plugin.getConfig().getString("casino.jackpot.draw-day", "SATURDAY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            day = DayOfWeek.SATURDAY;
        }
        LocalTime time;
        try {
            time = LocalTime.parse(plugin.getConfig().getString("casino.jackpot.draw-time", "18:00"));
        } catch (RuntimeException e) {
            time = LocalTime.of(18, 0);
        }
        ZonedDateTime candidate = now.with(TemporalAdjusters.nextOrSame(day)).with(time).withSecond(0).withNano(0);
        if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1);
        return candidate;
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        List<String> ids = new ArrayList<>();
        for (UUID id : participants) ids.add(id.toString());
        out.set("participants", ids);
        for (Map.Entry<UUID, Long> e : pending.entrySet()) out.set("pending." + e.getKey(), e.getValue());
        out.set("last", lastDraw);
        out.set("letzter-gewinner", lastWinner);
        out.set("letzter-betrag", lastAmount);
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("jackpot-draw.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
