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
 * Kosmetik: Spuren (folgen dem Spieler) und Kill-Effekte (am Ort des Opfers).
 * Jede Kosmetik hat eine Seltenheit und eine Bezahlart: Geld, XP, Juwelen oder nur Battlepass.
 * Format pro Eintrag: ID;SPUR|KILL;PARTIKEL;Anzeige;SELTENHEIT;WÄHRUNG;PREIS;BP-STUFE
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

    public enum Currency { MONEY, XP, JEWELS, NONE }

    public record Item(String id, String kind, Particle particle, String label, Rarity rarity,
                       Currency currency, long price, int bpLevel) {}

    private final KubyPlugins plugin;
    private final File file;
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private final Map<UUID, String> trail = new HashMap<>();
    private final Map<UUID, String> killFx = new HashMap<>();
    private final Map<UUID, String> teleportFx = new HashMap<>();
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
            if (tp != null) teleportFx.put(id, tp);
            if (t != null) trail.put(id, t);
            if (k != null) killFx.put(id, k);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickTrails, 4L, 4L);
    }

    public List<Item> catalogue() {
        List<Item> list = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("cosmetics")) {
            String[] p = raw.split(";");
            if (p.length < 6) continue;
            try {
                Particle particle = Particle.valueOf(p[2].trim().toUpperCase(Locale.ROOT));
                String kind = p[1].trim().toUpperCase(Locale.ROOT);
                String label = p[3].trim();
                if (p.length >= 8) {
                    list.add(new Item(p[0].trim(), kind, particle, label,
                            Rarity.valueOf(p[4].trim().toUpperCase(Locale.ROOT)),
                            Currency.valueOf(p[5].trim().toUpperCase(Locale.ROOT)),
                            Long.parseLong(p[6].trim()), Integer.parseInt(p[7].trim())));
                } else {
                    // Altes Format: Coins als Bezahlart, Seltenheit gewöhnlich
                    list.add(new Item(p[0].trim(), kind, particle, label, Rarity.COMMON, Currency.MONEY,
                            Long.parseLong(p[4].trim()), Integer.parseInt(p[5].trim())));
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ungültige Kosmetik in der config: " + raw);
            }
        }
        return list;
    }

    public Item find(String id) {
        for (Item i : catalogue()) {
            if (i.id().equals(id)) return i;
        }
        return null;
    }

    /** Gratis-Items (ohne Preis und ohne BP-Stufe) gehören jedem. */
    public synchronized boolean owns(UUID id, Item it) {
        if (it.bpLevel() == 0 && (it.currency() == Currency.NONE || it.price() <= 0)) return true;
        return owned.getOrDefault(id, Set.of()).contains(it.id());
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String buy(Player p, Item it) {
        UUID id = p.getUniqueId();
        if (owns(id, it)) return "Diese Kosmetik gehört dir schon.";
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
        return teleportFx.get(id);
    }

    /** Anlegen oder ablegen. */
    public synchronized void toggle(UUID id, Item it) {
        Map<UUID, String> map = it.kind().equals("TRAIL") ? trail : it.kind().equals("KILL") ? killFx : teleportFx;
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
                p.getWorld().spawnParticle(it.particle(), now.clone().add(0, 0.2, 0), 3, 0.2, 0.1, 0.2, 0.0);
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
        at.getWorld().spawnParticle(it.particle(), at.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        owned.forEach((id, set) -> out.set(id + ".owned", new ArrayList<>(set)));
        trail.forEach((id, t) -> out.set(id + ".trail", t));
        killFx.forEach((id, k) -> out.set(id + ".kill", k));
        teleportFx.forEach((id, t) -> out.set(id + ".teleport", t));
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
        return m;
    }

    @Override
    public synchronized void importData(UUID id, Map<String, Object> m) {
        owned.remove(id);
        trail.remove(id);
        killFx.remove(id);
        teleportFx.remove(id);
        if (m != null) {
            if (m.get("owned") instanceof List<?> l) {
                Set<String> s = new java.util.HashSet<>();
                for (Object x : l) s.add(String.valueOf(x));
                owned.put(id, s);
            }
            if (m.get("trail") != null) trail.put(id, m.get("trail").toString());
            if (m.get("kill") != null) killFx.put(id, m.get("kill").toString());
            if (m.get("tp") != null) teleportFx.put(id, m.get("tp").toString());
        }
        save();
    }
}
