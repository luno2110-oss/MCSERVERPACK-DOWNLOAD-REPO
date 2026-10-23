package de.kuby.kubysurvival.tokens;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tokens fürs Spielen: einer pro Stunde aktiv in Survival (wer sich 5 Minuten nicht bewegt, zählt nicht),
 * ein Tagesbonus beim ersten Spielen am Tag, Extra für viele Tage in Folge und Tokens fürs Voten.
 * Tokens kann man nicht kaufen und nicht tauschen. Der Kontostand liegt bei den Jobs (JobService.Worker.tokens).
 */
public final class TokenService implements Listener {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    static final class Data {
        int minutes;
        String lastDay = "";
        int streak;
        long earnedTotal;
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final File file;
    private final Map<UUID, Data> players = new HashMap<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private final Map<UUID, Integer> idle = new HashMap<>();

    public TokenService(KubySurvival addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "tokens.yml");
        load();
        addon.getServer().getScheduler().runTaskTimer(addon, this::minute, 1200L, 1200L);
        core.votes().registerHook(this::onVote);
    }

    synchronized Data data(UUID id) {
        return players.computeIfAbsent(id, k -> new Data());
    }

    public long balance(UUID id) {
        return addon.jobs().worker(id).tokens;
    }

    /** Tokens gutschreiben (mit Meldung, wenn online). */
    public synchronized void give(UUID id, long amount, String why) {
        if (amount <= 0) return;
        addon.jobs().addTokens(id, amount);
        data(id).earnedTotal += amount;
        Player p = addon.getServer().getPlayer(id);
        if (p != null) {
            Msg.send(p, "<light_purple>+" + amount + (amount == 1 ? " Token" : " Tokens") + " <gray>(" + why + ")");
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        }
    }

    public synchronized int minutesToNext(UUID id) {
        return 60 - data(id).minutes;
    }

    public synchronized int streak(UUID id) {
        Data d = data(id);
        String yesterday = LocalDate.now(ZONE).minusDays(1).toString();
        return LocalDate.now(ZONE).toString().equals(d.lastDay) || yesterday.equals(d.lastDay) ? d.streak : 0;
    }

    public synchronized long earnedTotal(UUID id) {
        return data(id).earnedTotal;
    }

    /** Streak-Belohnungen aus der Config: Tag → Extra-Tokens. */
    public TreeMap<Integer, Long> streakRewards() {
        TreeMap<Integer, Long> out = new TreeMap<>();
        for (String raw : core.getConfig().getStringList("tokens.streak-rewards")) {
            String[] p = raw.split(";");
            try {
                out.put(Integer.parseInt(p[0].trim()), Long.parseLong(p[1].trim()));
            } catch (RuntimeException ignored) {
            }
        }
        if (out.isEmpty()) {
            out.put(3, 1L);
            out.put(7, 2L);
            out.put(14, 3L);
            out.put(30, 5L);
        }
        return out;
    }

    private void minute() {
        for (Player p : addon.getServer().getOnlinePlayers()) {
            if (!ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()))) continue;
            UUID id = p.getUniqueId();
            Location now = p.getLocation();
            Location before = lastPos.put(id, now);
            boolean moved = before == null || before.getWorld() != now.getWorld() || before.distanceSquared(now) > 4
                    || Math.abs(before.getYaw() - now.getYaw()) > 10;
            int idleMin = moved ? 0 : idle.getOrDefault(id, 0) + 1;
            idle.put(id, idleMin);
            daily(p);
            if (idleMin >= 5) continue;
            synchronized (this) {
                Data d = data(id);
                d.minutes++;
                if (d.minutes >= 60) {
                    d.minutes = 0;
                    give(id, core.getConfig().getLong("tokens.per-hour", 1), "eine Stunde gespielt");
                }
            }
        }
    }

    /** Tagesbonus und Serie beim ersten Spielen am Tag. */
    private synchronized void daily(Player p) {
        Data d = data(p.getUniqueId());
        String today = LocalDate.now(ZONE).toString();
        if (today.equals(d.lastDay)) return;
        String yesterday = LocalDate.now(ZONE).minusDays(1).toString();
        d.streak = yesterday.equals(d.lastDay) ? d.streak + 1 : 1;
        d.lastDay = today;
        give(p.getUniqueId(), core.getConfig().getLong("tokens.daily-bonus", 1), "Tagesbonus, Tag " + d.streak + " in Folge");
        Long extra = streakRewards().get(d.streak);
        if (extra != null) give(p.getUniqueId(), extra, d.streak + " Tage in Folge");
    }

    private void onVote(UUID id) {
        give(id, core.getConfig().getLong("tokens.per-vote", 1), "Vote");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPos.remove(e.getPlayer().getUniqueId());
        idle.remove(e.getPlayer().getUniqueId());
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps == null) return;
        for (String k : ps.getKeys(false)) {
            try {
                Data d = new Data();
                d.minutes = ps.getInt(k + ".minutes");
                d.lastDay = ps.getString(k + ".last-day", "");
                d.streak = ps.getInt(k + ".streak");
                d.earnedTotal = ps.getLong(k + ".earned");
                players.put(UUID.fromString(k), d);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : players.entrySet()) {
            String b = "players." + e.getKey() + ".";
            out.set(b + "minutes", e.getValue().minutes);
            out.set(b + "last-day", e.getValue().lastDay);
            out.set(b + "streak", e.getValue().streak);
            out.set(b + "earned", e.getValue().earnedTotal);
        }
        try {
            addon.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException ex) {
            addon.getLogger().warning("tokens.yml: " + ex.getMessage());
        }
    }
}
