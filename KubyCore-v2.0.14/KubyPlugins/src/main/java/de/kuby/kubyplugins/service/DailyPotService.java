package de.kuby.kubyplugins.service;

import org.bukkit.entity.Player;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Tagespot: Lose kaufen, Ziehung täglich zur konfigurierten Uhrzeit (Berliner Zeit). */
public final class DailyPotService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Integer> tickets = new HashMap<>();
    private LocalDate lastDraw;

    public DailyPotService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dailypot.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            tickets.put(UUID.fromString(key), yml.getInt(key));
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 1200L, 1200L);
    }

    private long price() {
        return plugin.getConfig().getLong("dailypot.ticket-price", 200);
    }

    public synchronized long pot() {
        long total = 0;
        for (int n : tickets.values()) total += n;
        return total * price();
    }

    public synchronized int ticketsOf(UUID id) {
        return tickets.getOrDefault(id, 0);
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String buy(UUID id, int amount) {
        if (amount <= 0) return "Ungültige Anzahl.";
        int max = plugin.getConfig().getInt("dailypot.max-tickets", 50);
        if (ticketsOf(id) + amount > max) return "Maximal " + max + " Lose pro Tag.";
        long cost = price() * amount;
        if (!plugin.economy().withdraw(id, cost)) return "Nicht genug$ (" + Msg.coins(cost) + ").";
        tickets.merge(id, amount, Integer::sum);
        save();
        return null;
    }

    private void check() {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        int hour = plugin.getConfig().getInt("dailypot.draw-hour", 19);
        if (now.getHour() == hour && now.getMinute() == 0 && !now.toLocalDate().equals(lastDraw)) {
            lastDraw = now.toLocalDate();
            draw();
        }
    }

    public synchronized void draw() {
        int total = 0;
        for (int n : tickets.values()) total += n;
        if (total <= 0) return;

        int pick = ThreadLocalRandom.current().nextInt(total);
        UUID winner = null;
        for (Map.Entry<UUID, Integer> e : tickets.entrySet()) {
            pick -= e.getValue();
            if (pick < 0) {
                winner = e.getKey();
                break;
            }
        }
        if (winner == null) return;

        long pot = (long) total * price();
        long tax = pot * plugin.getConfig().getInt("dailypot.tax-percent", 5) / 100;
        long payout = pot - tax;
        plugin.economy().deposit(winner, payout);

        OfflinePlayer op = Bukkit.getOfflinePlayer(winner);
        String name = op.getName() == null ? "Unbekannt" : op.getName();
        Player won = Bukkit.getPlayer(winner);
        if (won != null) Sfx.play(plugin, won, Sfx.Kind.JACKPOT);
        tickets.clear();
        save();
        plugin.notifications().send("notify.dailypot", "<gold>Tagespot: <white>" + Msg.escape(name)
                + " <gray>gewinnt <gold>" + Msg.coins(payout) + "$<gray>!");
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        tickets.forEach((id, n) -> out.set(id.toString(), n));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("dailypot.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
