package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kosmetik: Hüte (Modell auf dem Kopf, HatService), Spuren (folgen dem Spieler), Kill-Effekte (am Ort des Opfers)
 * und Teleport-Effekte. Jede Kosmetik hat eine Seltenheit und eine Bezahlart: Geld, XP, Juwelen, nur Battlepass
 * (NONE mit BP-Stufe) oder nur aus Kisten (CRATE, vergeben mit /cosmetics give).
 * Format pro Eintrag: ID;HAT|TRAIL|KILL|TELEPORT;PARTIKEL oder Hut-Modell;Anzeige;SELTENHEIT;WÄHRUNG;PREIS;BP-STUFE.
 * Liste je Modus unter cosmetics-modes.&lt;modus&gt;, sonst gilt cosmetics.
 */
public final class CosmeticService implements ModeData {

    public enum Rarity {
        COMMON("Gewöhnlich", "<gray>"),
        RARE("Selten", "<aqua>"),
        EPIC("Episch", "<light_purple>"),
        LEGENDARY("Legendär", "<gold>");

        public final String label;
        public final String color;

        Rarity(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    public enum Currency { MONEY, XP, JEWELS, NONE, CRATE }

    /** model: Hut-Modell (kuby:hat/&lt;model&gt;), sonst null; particle: null bei Hüten. */
    public record Item(String id, String kind, Particle particle, String label, Rarity rarity,
                       Currency currency, long price, int bpLevel, String model) {}

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private final Map<UUID, String> trail = new HashMap<>();
    private final Map<UUID, String> killFx = new HashMap<>();
    private final Map<UUID, String> teleportFx = new HashMap<>();
    private final Map<UUID, String> hat = new HashMap<>();
    private final Map<UUID, Location> last = new HashMap<>();

    public CosmeticService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cosmetics.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            UUID id = UUID.fromString(key);
            owned.put(id, new HashSet<>(yml.getStringList(key + ".owned")));
            String t = yml.getString(key + ".trail");
            String k = yml.getString(key + ".kill");
            String tp = yml.getString(key + ".teleport");
            String h = yml.getString(key + ".hat");
            if (h != null) hat.put(id, h);
            if (tp != null) teleportFx.put(id, tp);
            if (t != null) trail.put(id, t);
            if (k != null) killFx.put(id, k);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickTrails, 4L, 4L);
    }

    /** Katalog des Modus, in dem der Spieler gerade ist. */
    public List<Item> catalogue(UUID player) {
        return catalogue(plugin.modes().modeOf(player));
    }

    /** Katalog eines Modus (cosmetics-modes.&lt;modus&gt;, sonst cosmetics). */
    public List<Item> catalogue(String mode) {
        String path = "cosmetics-modes." + mode;
        return parse(plugin.getConfig().isList(path) ? plugin.getConfig().getStringList(path)
                : plugin.getConfig().getStringList("cosmetics"));
    }

    /** Alle Kosmetiken aller Modi (für Effekte, die nur die ID kennen). */
    public List<Item> catalogue() {
        List<Item> all = new ArrayList<>(parse(plugin.getConfig().getStringList("cosmetics")));
        var sec = plugin.getConfig().getConfigurationSection("cosmetics-modes");
        if (sec != null) for (String m : sec.getKeys(false)) all.addAll(parse(sec.getStringList(m)));
        return all;
    }

    /** Geparste Listen kurz zwischenspeichern (Hüte und Spuren fragen mehrmals pro Sekunde). */
    private final Map<List<String>, List<Item>> parsed = new HashMap<>();
    private long parsedAt;

    private synchronized List<Item> parse(List<String> raws) {
        long now = System.currentTimeMillis();
        if (now - parsedAt > 5000) {
            parsed.clear();
            parsedAt = now;
        }
        return parsed.computeIfAbsent(raws, this::parseNow);
    }

    private List<Item> parseNow(List<String> raws) {
        List<Item> list = new ArrayList<>();
        for (String raw : raws) {
            String[] p = raw.split(";");
            if (p.length < 6) continue;
            try {
                String kind = p[1].trim().toUpperCase(Locale.ROOT);
                boolean isHat = kind.equals("HAT");
                Particle particle = isHat ? null : Particle.valueOf(p[2].trim().toUpperCase(Locale.ROOT));
                String model = isHat ? p[2].trim().toLowerCase(Locale.ROOT) : null;
                String label = p[3].trim();
                if (p.length >= 8) {
                    list.add(new Item(p[0].trim(), kind, particle, label,
                            Rarity.valueOf(p[4].trim().toUpperCase(Locale.ROOT)),
                            Currency.valueOf(p[5].trim().toUpperCase(Locale.ROOT)),
                            Long.parseLong(p[6].trim()), Integer.parseInt(p[7].trim()), model));
                } else {
                    // Altes Format: Coins als Bezahlart, Seltenheit gewöhnlich
                    list.add(new Item(p[0].trim(), kind, particle, label, Rarity.COMMON, Currency.MONEY,
                            Long.parseLong(p[4].trim()), Integer.parseInt(p[5].trim()), model));
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ungültige Kosmetik in der config: " + raw);
            }
        }
        return list;
    }

    public Item find(String id) {
        if (id == null) return null;
        for (Item i : catalogue()) {
            if (i.id().equals(id)) return i;
        }
        return null;
    }

    /** Freischalten ohne Bezahlen (Kisten, Events, /cosmetics give). */
    public synchronized void grant(UUID id, Item it) {
        owned.computeIfAbsent(id, k -> new HashSet<>()).add(it.id());
        save();
    }

    /** Gratis-Items (ohne Preis und ohne BP-Stufe) gehören jedem. */
    public synchronized boolean owns(UUID id, Item it) {
        if (it.currency() != Currency.CRATE && it.bpLevel() == 0 && (it.currency() == Currency.NONE || it.price() <= 0)) return true;
        return owned.getOrDefault(id, Set.of()).contains(it.id());
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String buy(Player p, Item it) {
        UUID id = p.getUniqueId();
        if (owns(id, it)) return "Diese Kosmetik gehört dir schon.";
        if (it.currency() == Currency.CRATE) return "Das gibt es nur aus Kisten.";
        if (plugin.battlepass().level(id) < it.bpLevel()) return "Benötigt Battlepass-Stufe " + it.bpLevel() + ".";
        if (it.price() > 0) {
            switch (it.currency()) {
                case MONEY -> {
                    if (!plugin.economy().withdraw(id, it.price())) return "Nicht genug $ (" + Msg.coins(it.price()) + "$).";
                }
                case JEWELS -> {
                    if (!plugin.jewels().take(id, it.price())) return "Du brauchst " + it.price() + " Juwelen.";
                }
                case XP -> {
                    var st = plugin.stats().get(id);
                    if (st.xp < it.price()) return "Dir fehlen noch " + Msg.coins(it.price() - st.xp) + " XP.";
                    st.xp -= it.price();
                    plugin.stats().save();
                }
                default -> { }
            }
        }
        owned.computeIfAbsent(id, k -> new HashSet<>()).add(it.id());
        save();
        return null;
    }

    public synchronized String activeId(UUID id, String kind) {
        if (kind.equals("TRAIL")) return trail.get(id);
        if (kind.equals("KILL")) return killFx.get(id);
        if (kind.equals("HAT")) return hat.get(id);
        return teleportFx.get(id);
    }

    /** Anlegen oder ablegen. */
    public synchronized void toggle(UUID id, Item it) {
        Map<UUID, String> map = switch (it.kind()) {
            case "TRAIL" -> trail;
            case "KILL" -> killFx;
            case "HAT" -> hat;
            default -> teleportFx;
        };
        if (it.id().equals(map.get(id))) map.remove(id);
        else map.put(id, it.id());
        save();
    }

    private void tickTrails() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            String tid = trail.get(p.getUniqueId());
            if (tid == null) continue;
            Item it = find(tid);
            if (it == null) continue;
            Location now = p.getLocation();
            Location prev = last.get(p.getUniqueId());
            if (prev == null || !prev.getWorld().equals(now.getWorld()) || prev.distanceSquared(now) > 0.01) {
                de.kuby.kubyplugins.util.Fx.particle(plugin, now.clone().add(0, 0.2, 0), it.particle(), 3, 0.2, 0.1, 0.2, 0.0);
                last.put(p.getUniqueId(), now.clone());
            }
        }
    }

    /** Kill-Effekt des Killers am Ort des Opfers. */
    public void onKill(Player killer, Location at) {
        String kid = killFx.get(killer.getUniqueId());
        if (kid == null) return;
        Item it = find(kid);
        if (it == null) return;
        de.kuby.kubyplugins.util.Fx.particle(plugin, at.clone().add(0, 1, 0), it.particle(), 30, 0.4, 0.6, 0.4, 0.05);
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        owned.forEach((id, set) -> out.set(id + ".owned", new ArrayList<>(set)));
        trail.forEach((id, t) -> out.set(id + ".trail", t));
        killFx.forEach((id, k) -> out.set(id + ".kill", k));
        teleportFx.forEach((id, t) -> out.set(id + ".teleport", t));
        hat.forEach((id, h) -> out.set(id + ".hat", h));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("cosmetics.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    @Override
    public String key() { return "cosmetic"; }

    @Override
    public synchronized Map<String, Object> export(UUID id) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        Set<String> o = owned.get(id);
        if (o != null) m.put("owned", new java.util.ArrayList<>(o));
        if (trail.containsKey(id)) m.put("trail", trail.get(id));
        if (killFx.containsKey(id)) m.put("kill", killFx.get(id));
        if (teleportFx.containsKey(id)) m.put("tp", teleportFx.get(id));
        if (hat.containsKey(id)) m.put("hat", hat.get(id));
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        owned.remove(id);
        trail.remove(id);
        killFx.remove(id);
        teleportFx.remove(id);
        hat.remove(id);
        if (m != null) {
            if (m.get("owned") instanceof List<?> l) {
                Set<String> s = new java.util.HashSet<>();
                for (Object x : l) s.add(String.valueOf(x));
                owned.put(id, s);
            }
            if (m.get("trail") != null) trail.put(id, m.get("trail").toString());
            if (m.get("kill") != null) killFx.put(id, m.get("kill").toString());
            if (m.get("tp") != null) teleportFx.put(id, m.get("tp").toString());
            if (m.get("hat") != null) hat.put(id, m.get("hat").toString());
        }
        save();
    }
}
