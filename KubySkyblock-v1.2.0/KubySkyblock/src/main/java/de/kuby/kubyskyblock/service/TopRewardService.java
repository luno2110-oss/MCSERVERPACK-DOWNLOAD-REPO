package de.kuby.kubyskyblock.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyskyblock.KubySkyblock;
import de.kuby.kubyskyblock.service.IslandService.Island;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Preise für die besten Inseln: jede Woche (Standard Sonntag 20 Uhr) und jeden Monat (letzter Tag, 20 Uhr) bekommen die
 * Inseln auf Platz 1 bis 10 Coins und Juwelen (für jedes Mitglied) und einen Generator-Booster. Geprüft wird alle fünf
 * Minuten; war der Server zur Zeit der Auszahlung aus, wird sie beim nächsten Start nachgeholt. Daten: top-rewards.yml.
 */
public final class TopRewardService {

    public static final String WEEK = "woche", MONTH = "monat";

    /** Preis für die Plätze from bis to. */
    public record Prize(int from, int to, long coins, long jewels, double booster, int boosterMinutes) {
        public String label() {
            return from == to ? "Platz " + from : "Platz " + from + "–" + to;
        }

        public String text() {
            List<String> parts = new ArrayList<>();
            if (coins > 0) parts.add(Msg.coins(coins) + " $");
            if (jewels > 0) parts.add(Msg.coins(jewels) + " Juwelen");
            if (booster > 1 && boosterMinutes > 0) parts.add("Booster x" + GeneratorService.fmt(booster) + " " + de.kuby.kubyplugins.service.KitService.format(boosterMinutes * 60_000L));
            return parts.isEmpty() ? "–" : String.join(" · ", parts);
        }
    }

    /** Gewinner einer Auszahlung. */
    public record Winner(int rank, String name, String owner, long level) {}

    private final KubySkyblock addon;
    private final KubyPlugins core;
    private final File file;
    private long paidWeek, paidMonth;
    private final List<Winner> lastWeek = new ArrayList<>(), lastMonth = new ArrayList<>();

    public TopRewardService(KubySkyblock addon, KubyPlugins core) {
        this.addon = addon;
        this.core = core;
        this.file = new File(addon.getDataFolder(), "top-rewards.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        // Erster Start: nichts nachzahlen, erst ab dem nächsten Termin
        paidWeek = y.contains("paid-woche") ? y.getLong("paid-woche") : last(WEEK);
        paidMonth = y.contains("paid-monat") ? y.getLong("paid-monat") : last(MONTH);
        read(y.getStringList("last-woche"), lastWeek);
        read(y.getStringList("last-monat"), lastMonth);
        if (!y.contains("paid-woche")) save();
    }

    private static void read(List<String> raw, List<Winner> out) {
        for (String s : raw) {
            String[] p = s.split(";", 4);
            try {
                out.add(new Winner(Integer.parseInt(p[0]), p[3], p[1], Long.parseLong(p[2])));
            } catch (RuntimeException ignored) {
            }
        }
    }

    public boolean enabled() {
        return addon.getConfig().getBoolean("top-belohnungen.aktiv", true);
    }

    public List<Prize> prizes(String period) {
        List<Prize> out = new ArrayList<>();
        ConfigurationSection s = addon.getConfig().getConfigurationSection("top-belohnungen." + period + ".plaetze");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            try {
                String[] r = k.split("-");
                int from = Integer.parseInt(r[0].trim()), to = r.length > 1 ? Integer.parseInt(r[1].trim()) : from;
                out.add(new Prize(from, Math.max(from, to), c.getLong("coins"), c.getLong("juwelen"), c.getDouble("booster", 0),
                        c.getInt("booster-minuten", 0)));
            } catch (RuntimeException ex) {
                addon.getLogger().warning("top-belohnungen." + period + ": Platz " + k + " ungültig");
            }
        }
        return out;
    }

    public Prize prize(String period, int rank) {
        for (Prize p : prizes(period)) if (rank >= p.from() && rank <= p.to()) return p;
        return null;
    }

    private ZonedDateTime at(String period, ZonedDateTime base) {
        int hour = Math.max(0, Math.min(23, addon.getConfig().getInt("top-belohnungen." + period + ".stunde", 20)));
        if (period.equals(WEEK)) {
            DayOfWeek d;
            try {
                d = DayOfWeek.valueOf(addon.getConfig().getString("top-belohnungen.woche.tag", "SUNDAY").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                d = DayOfWeek.SUNDAY;
            }
            return base.with(TemporalAdjusters.nextOrSame(d)).withHour(hour).withMinute(0).withSecond(0).withNano(0);
        }
        return base.with(TemporalAdjusters.lastDayOfMonth()).withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    /** Nächste Auszahlung (ms). */
    public long next(String period) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime t = at(period, now);
        if (!t.isAfter(now)) t = at(period, period.equals(WEEK) ? now.plusDays(1) : now.plusMonths(1).withDayOfMonth(1));
        return t.toInstant().toEpochMilli();
    }

    /** Letzte Auszahlungszeit, die schon vorbei ist (ms). */
    public long last(String period) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime t = at(period, period.equals(WEEK) ? now.minusDays(6) : now);
        if (t.isAfter(now)) t = period.equals(WEEK) ? t.minusWeeks(1) : at(period, now.minusMonths(1).withDayOfMonth(1));
        return t.toInstant().toEpochMilli();
    }

    public List<Winner> winners(String period) {
        return period.equals(WEEK) ? lastWeek : lastMonth;
    }

    /** Alle fünf Minuten: fällige Auszahlungen ausführen. */
    public void tick() {
        if (!enabled()) return;
        long w = last(WEEK), m = last(MONTH);
        if (paidWeek < w) {
            paidWeek = w;
            payout(WEEK);
        }
        if (paidMonth < m) {
            paidMonth = m;
            payout(MONTH);
        }
    }

    /** Preise jetzt auszahlen (auch für Admins zum Testen). */
    public void payout(String period) {
        IslandService is = addon.islands();
        List<Prize> ps = prizes(period);
        int places = 0;
        for (Prize p : ps) places = Math.max(places, p.to());
        List<Island> top = is.top(places);
        List<Winner> win = winners(period);
        win.clear();
        String title = period.equals(WEEK) ? "Wochen" : "Monats";
        for (int k = 0; k < top.size(); k++) {
            Island i = top.get(k);
            int rank = k + 1;
            Prize p = prize(period, rank);
            if (p == null || i.points <= 0) continue;
            win.add(new Winner(rank, i.name, IslandService.nameOf(i.owner), is.levelOf(i)));
            for (UUID u : i.everyone()) {
                if (p.coins() > 0) core.modes().depositIn(u, ModeService.SKYBLOCK, p.coins());
                if (p.jewels() > 0) core.jewels().add(u, p.jewels());
                Player o = Bukkit.getPlayer(u);
                if (o != null) {
                    Msg.send(o, "<gold><b>" + title + "-Preis!</b> <white>" + Msg.escape(i.name) + " <gold>ist auf Platz " + rank + ": <white>" + p.text()
                            + (p.coins() > 0 || p.jewels() > 0 ? " <gray>(für jedes Mitglied)" : ""));
                    Sfx.play(core, o, Sfx.Kind.SUCCESS_BIG);
                }
            }
            if (p.booster() > 1 && p.boosterMinutes() > 0 && addon.generators() != null) addon.generators().addBoost(i.slot, p.booster(), p.boosterMinutes());
        }
        if (!win.isEmpty()) {
            StringBuilder b = new StringBuilder("<gold><b>Top-Inseln · " + title + "-Preise</b>");
            for (Winner wn : win.subList(0, Math.min(3, win.size()))) b.append(" <gray>#").append(wn.rank()).append(" <white>").append(Msg.escape(wn.name()));
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (ModeService.SKYBLOCK.equals(core.modes().modeOf(o.getUniqueId()))) Msg.send(o, b.toString());
            }
        }
        addon.getLogger().info(title + "-Preise der Top-Inseln ausgezahlt (" + win.size() + " Inseln).");
        save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("paid-woche", paidWeek);
        y.set("paid-monat", paidMonth);
        y.set("last-woche", lastWeek.stream().map(w -> w.rank() + ";" + w.owner() + ";" + w.level() + ";" + w.name()).toList());
        y.set("last-monat", lastMonth.stream().map(w -> w.rank() + ";" + w.owner() + ";" + w.level() + ";" + w.name()).toList());
        DataFiles.save(addon, y, file);
    }
}
