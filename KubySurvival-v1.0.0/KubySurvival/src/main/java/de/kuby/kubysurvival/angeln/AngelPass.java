package de.kuby.kubysurvival.angeln;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.ui.KitData;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Angelpass: 550 Juwelen, 75 Stufen, kein Gratis-Pfad. Stufen steigen mit Angel-Punkten aus Fängen (nur mit Pass) oder
 * lassen sich kaufen (jede Stufe 25 Juwelen, gleichbleibend). Belohnungen: Geld, Angel-Punkte, Angelboost-
 * Minuten und drei besondere Angeln (Stufe 25 Goldfieber, 50 Sturm, 75 Kosmos).
 * <p>
 * Dauerhafte Vorteile mit Pass: +150 % Gewinn beim Händler, doppelt so schnelles Angeln, alle Coin- und Token-Preise
 * beim Angeln halbiert (Juwelen nicht), Glück verdoppelt (mindestens +100 %).
 */
public final class AngelPass {

    public static final int MAX = 75;

    public enum Kind { MONEY, POINTS, BOOST, ROD }

    /** Belohnung einer Stufe. */
    public record Reward(int level, Kind kind, long amount, Rod rod) {
        public String label() {
            return switch (kind) {
                case MONEY -> AngelText.money(amount);
                case POINTS -> AngelText.num(amount) + " Angel-Punkte";
                case BOOST -> "Angelboost " + amount + " Min.";
                case ROD -> rod.label;
            };
        }

        public KitData.Icon icon() {
            return switch (kind) {
                case MONEY -> KitData.Icon.GOLD_INGOT;
                case POINTS -> KitData.Icon.EXPERIENCE;
                case BOOST -> KitData.Icon.CLOCK;
                case ROD -> rod.icon();
            };
        }
    }

    private static final class State {
        boolean owned;
        long xp;
        final Set<Integer> claimed = new HashSet<>();
    }

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final AngelService service;
    private final File file;
    private final Map<UUID, State> states = new HashMap<>();
    private boolean dirty;

    AngelPass(KubySurvival addon, KubyPlugins core, AngelService service) {
        this.addon = addon;
        this.core = core;
        this.service = service;
        this.file = new File(addon.getDataFolder(), "angelpass.yml");
        load();
    }

    private long cfg(String key, long def) {
        return core.getConfig().getLong("angeln.angelpass." + key, def);
    }

    public long price() {
        return cfg("preis-juwelen", 550);
    }

    public long perLevel() {
        return Math.max(1, cfg("punkte-pro-stufe", 2000));
    }

    /** Preis einer Stufe in Juwelen: immer gleich (Standard 25), wird mit dem Level nicht teurer. */
    public long levelPrice(int currentLevel) {
        return Math.max(1, cfg("stufe-preis", cfg("stufe-start", 25)));
    }

    /** Belohnung der Stufe (1 bis 75). */
    public static Reward reward(int level) {
        if (level == 25) return new Reward(level, Kind.ROD, 1, Rod.GOLDFIEBER);
        if (level == 50) return new Reward(level, Kind.ROD, 1, Rod.STURM);
        if (level == 75) return new Reward(level, Kind.ROD, 1, Rod.KOSMOS);
        if (level % 5 == 0) return new Reward(level, Kind.BOOST, Math.min(30, level), null);
        if (level % 2 == 1) return new Reward(level, Kind.MONEY, 5000L * level, null);
        return new Reward(level, Kind.POINTS, 150L * level, null);
    }

    public static List<Reward> all() {
        List<Reward> l = new ArrayList<>();
        for (int i = 1; i <= MAX; i++) l.add(reward(i));
        return l;
    }

    private State state(UUID id) {
        return states.computeIfAbsent(id, k -> new State());
    }

    public synchronized boolean owned(UUID id) {
        State s = states.get(id);
        return s != null && s.owned;
    }

    public synchronized long xp(UUID id) {
        return state(id).xp;
    }

    public synchronized int level(UUID id) {
        return (int) Math.min(MAX, state(id).xp / perLevel());
    }

    public synchronized boolean claimed(UUID id, int level) {
        return state(id).claimed.contains(level);
    }

    public synchronized int claimable(UUID id) {
        if (!owned(id)) return 0;
        int n = 0;
        for (int i = 1; i <= level(id); i++) if (!state(id).claimed.contains(i)) n++;
        return n;
    }

    /** Angel-Punkte eines Fangs zählen für den Pass (nur mit Pass). */
    synchronized void addXp(UUID id, long points) {
        State s = states.get(id);
        if (s == null || !s.owned || points <= 0) return;
        s.xp = Math.min((long) MAX * perLevel(), s.xp + points);
        dirty = true;
    }

    /** Kauft den Pass; null bei Erfolg, sonst Fehlermeldung. */
    public synchronized String buy(Player p) {
        UUID id = p.getUniqueId();
        if (owned(id)) return "Den Angelpass hast du schon.";
        if (!core.jewels().take(id, price())) return "Dafür fehlen dir Juwelen (" + AngelText.num(price()) + ").";
        state(id).owned = true;
        dirty = true;
        save();
        return null;
    }

    /** Kauft die nächste Stufe für Juwelen. */
    public synchronized String buyLevel(Player p) {
        UUID id = p.getUniqueId();
        if (!owned(id)) return "Stufen gibt es nur mit dem Angelpass.";
        int lv = level(id);
        if (lv >= MAX) return "Du hast schon alle " + MAX + " Stufen.";
        long cost = levelPrice(lv);
        if (!core.jewels().take(id, cost)) return "Dafür fehlen dir Juwelen (" + AngelText.num(cost) + ").";
        state(id).xp = (long) (lv + 1) * perLevel();
        dirty = true;
        save();
        return null;
    }

    /** Holt alle erreichten Belohnungen ab; gibt die Texte zurück (leer = nichts abzuholen). */
    public synchronized List<String> claimAll(Player p) {
        UUID id = p.getUniqueId();
        List<String> out = new ArrayList<>();
        if (!owned(id)) return out;
        State s = state(id);
        for (int i = 1; i <= level(id); i++) {
            if (s.claimed.contains(i)) continue;
            Reward r = reward(i);
            give(p, r);
            s.claimed.add(i);
            out.add(r.label());
        }
        if (!out.isEmpty()) {
            dirty = true;
            save();
        }
        return out;
    }

    private void give(Player p, Reward r) {
        UUID id = p.getUniqueId();
        switch (r.kind()) {
            case MONEY -> service.pay(id, r.amount());
            case POINTS -> service.addPoints(id, r.amount());
            case BOOST -> service.addBoostMinutes(id, r.amount());
            case ROD -> service.grantRod(p, r.rod());
        }
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            ConfigurationSection sec = y.getConfigurationSection(k);
            if (sec == null) continue;
            try {
                State s = new State();
                s.owned = sec.getBoolean("pass");
                s.xp = sec.getLong("punkte");
                for (int i : sec.getIntegerList("abgeholt")) s.claimed.add(i);
                states.put(UUID.fromString(k), s);
            } catch (IllegalArgumentException ignored) {
                // kaputter Eintrag
            }
        }
    }

    public synchronized void saveIfDirty() {
        if (dirty) save();
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        states.forEach((id, s) -> {
            if (!s.owned && s.xp == 0) return;
            y.set(id + ".pass", s.owned);
            y.set(id + ".punkte", s.xp);
            y.set(id + ".abgeholt", new ArrayList<>(s.claimed));
        });
        try {
            addon.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            addon.getLogger().warning("angelpass.yml: " + e.getMessage());
        }
    }
}
