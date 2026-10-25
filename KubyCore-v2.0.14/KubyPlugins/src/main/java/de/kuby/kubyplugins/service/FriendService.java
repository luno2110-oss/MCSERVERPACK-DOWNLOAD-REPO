package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import de.kuby.kubyplugins.util.Teleports;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Freundschaften pro Modus. Freunde gelten nur im Modus, in dem sie geschlossen wurden.
 * Anfragen gehen nur zwischen Spielern im selben Modus und gelten 7 Tage (auch über Neustarts).
 * Wie viele Freunde in die Liste passen, hängt vom Rang ab (friends.limit, friends.ranks).
 */
public final class FriendService implements org.bukkit.event.Listener {

    private static final long REQUEST_DAYS_MS = 7L * 24 * 3600 * 1000;
    private static final String REQUESTS = "anfragen";

    private final KubyPlugins plugin;
    private final File file;
    private final Map<String, Set<UUID>> friends = new HashMap<>();   // Schlüssel: modus|uuid
    /** Schlüssel: modus|empfänger, Wert: Absender und Zeitpunkt. */
    private final Map<String, Map<UUID, Long>> requests = new HashMap<>();
    private final Map<UUID, Long> lastJump = new HashMap<>();

    public FriendService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "friends.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            if (key.equals(REQUESTS)) continue;
            String fk = key.contains("|") ? key : "smp|" + key;   // altes Format gehört zum SMP
            Set<UUID> set = new HashSet<>();
            for (String s : yml.getStringList(key)) {
                try {
                    set.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
            friends.put(fk, set);
        }
        ConfigurationSection rs = yml.getConfigurationSection(REQUESTS);
        if (rs != null) {
            long now = System.currentTimeMillis();
            for (String to : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(to);
                if (s == null) continue;
                Map<UUID, Long> m = new LinkedHashMap<>();
                for (String from : s.getKeys(false)) {
                    long at = s.getLong(from);
                    if (now - at > REQUEST_DAYS_MS) continue;
                    try {
                        m.put(UUID.fromString(from), at);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (!m.isEmpty()) requests.put(to.replace('_', '|'), m);
            }
        }
    }

    private String mode(UUID id) {
        return plugin.modes().modeOf(id);
    }

    private static String fk(String mode, UUID id) {
        return mode + "|" + id;
    }

    public synchronized boolean areFriends(UUID a, UUID b) {
        if (!mode(a).equals(mode(b))) return false;
        return friends.getOrDefault(fk(mode(a), a), Set.of()).contains(b);
    }

    public synchronized Set<UUID> friendsOf(UUID id) {
        return new HashSet<>(friends.getOrDefault(fk(mode(id), id), Set.of()));
    }

    /** Offene Anfragen an diesen Spieler (älteste zuerst). */
    public synchronized Set<UUID> requestsOf(UUID id) {
        Map<UUID, Long> m = requests.get(fk(mode(id), id));
        if (m == null) return new HashSet<>();
        m.values().removeIf(at -> System.currentTimeMillis() - at > REQUEST_DAYS_MS);
        return new java.util.LinkedHashSet<>(m.keySet());
    }

    /** Anfragen, die dieser Spieler im aktuellen Modus verschickt hat und die noch offen sind. */
    public synchronized Set<UUID> outgoingOf(UUID id) {
        String prefix = mode(id) + "|";
        Set<UUID> out = new java.util.LinkedHashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<UUID, Long>> e : requests.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            Long at = e.getValue().get(id);
            if (at != null && now - at <= REQUEST_DAYS_MS) {
                try {
                    out.add(UUID.fromString(e.getKey().substring(prefix.length())));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    /** Plätze in der Freundesliste: Grundwert oder der beste Rang (Permission kuby.rank.x oder group.x). */
    public int limit(Player p) {
        int limit = plugin.getConfig().getInt("friends.limit", 20);
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("friends.ranks");
        if (ranks != null) {
            for (String r : ranks.getKeys(false)) {
                if (p.hasPermission("kuby.rank." + r) || p.hasPermission("group." + r)) limit = Math.max(limit, ranks.getInt(r));
            }
        }
        return limit;
    }

    private boolean full(UUID id) {
        Player p = Bukkit.getPlayer(id);
        // Offline lässt sich der Rang nicht prüfen; dann zählt nur der Grundwert nicht als Grenze
        return p != null && friendsOf(id).size() >= limit(p);
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String request(UUID from, UUID to) {
        if (from.equals(to)) return "Du kannst dir selbst keine Anfrage schicken.";
        if (!mode(from).equals(mode(to))) return "Der Spieler ist gerade in einem anderen Modus.";
        if (areFriends(from, to)) return "Ihr seid schon befreundet.";
        if (full(from)) return "Deine Freundesliste ist voll. Mit einem höheren Rang passen mehr Freunde hinein.";
        if (full(to)) return "Die Freundesliste dieses Spielers ist voll.";
        // Hat der andere schon gefragt? Dann gilt das als Annehmen.
        Map<UUID, Long> mine = requests.get(fk(mode(from), from));
        if (mine != null && mine.containsKey(to)) return accept(from, to);
        if (!plugin.playerSettings().get(to, "friends")) return "Dieser Spieler nimmt gerade keine Freundesanfragen an.";
        Map<UUID, Long> pending = requests.computeIfAbsent(fk(mode(to), to), k -> new LinkedHashMap<>());
        if (pending.containsKey(from)) return "Du hast diesem Spieler schon eine Anfrage geschickt.";
        if (pending.size() >= 30) return "Dieser Spieler hat gerade zu viele offene Anfragen.";
        pending.put(from, System.currentTimeMillis());
        save();
        return null;
    }

    public synchronized String accept(UUID me, UUID from) {
        String m = mode(me);
        if (!m.equals(mode(from))) return "Der Spieler ist gerade in einem anderen Modus.";
        Map<UUID, Long> pending = requests.get(fk(m, me));
        if (pending == null || !pending.containsKey(from)) return "Keine Anfrage von diesem Spieler.";
        if (full(me)) return "Deine Freundesliste ist voll. Entferne zuerst jemanden.";
        if (full(from)) return "Die Freundesliste dieses Spielers ist voll.";
        pending.remove(from);
        Map<UUID, Long> theirs = requests.get(fk(m, from));
        if (theirs != null) theirs.remove(me);
        friends.computeIfAbsent(fk(m, me), k -> new HashSet<>()).add(from);
        friends.computeIfAbsent(fk(m, from), k -> new HashSet<>()).add(me);
        save();
        Player other = Bukkit.getPlayer(from);
        if (other != null) {
            Msg.send(other, "<green>" + Bukkit.getOfflinePlayer(me).getName() + " hat deine Freundesanfrage angenommen!");
            Sfx.play(plugin, other, Sfx.Kind.SUCCESS);
        }
        return null;
    }

    /** Anfrage ablehnen (me hat sie bekommen) oder zurückziehen (me hat sie geschickt). */
    public synchronized boolean deny(UUID me, UUID other) {
        String m = mode(me);
        boolean changed = false;
        Map<UUID, Long> in = requests.get(fk(m, me));
        if (in != null && in.remove(other) != null) changed = true;
        Map<UUID, Long> out = requests.get(fk(m, other));
        if (out != null && out.remove(me) != null) changed = true;
        if (changed) save();
        return changed;
    }

    public synchronized void remove(UUID a, UUID b) {
        String m = mode(a);
        friends.getOrDefault(fk(m, a), new HashSet<>()).remove(b);
        friends.getOrDefault(fk(m, b), new HashSet<>()).remove(a);
        save();
    }

    /** Wo ein Freund gerade ist: Modus und, wenn ein Addon es liefert, der Ort (Spawn, Farmwelt, Wildnis, ...). */
    public String whereIs(Player p) {
        String mode = mode(p.getUniqueId());
        String place = plugin.textValue("place", p.getUniqueId());
        return ModeService.label(mode) + (place.isEmpty() ? "" : " · " + place);
    }

    /** /friend jump: zum Freund springen (mit Wartezeit, nur gleicher Modus, nicht im Kampf). Null bei Erfolg. */
    public String jump(Player p, Player target) {
        if (target == null || !target.isOnline() || !p.canSee(target)) return "Dieser Freund ist gerade nicht online.";
        if (!areFriends(p.getUniqueId(), target.getUniqueId())) {
            return friendsOf(p.getUniqueId()).contains(target.getUniqueId())
                    ? target.getName() + " ist gerade in " + ModeService.label(mode(target.getUniqueId())) + "."
                    : "Ihr seid keine Freunde.";
        }
        if (!plugin.playerSettings().get(target.getUniqueId(), "jump")) return target.getName() + " lässt gerade niemanden nachspringen.";
        if (plugin.trades().inTrade(p.getUniqueId()) || plugin.pvp().isDueling(p.getUniqueId()) || plugin.pvp().inCombat(p.getUniqueId())) {
            return "Du bist gerade beschäftigt (Handel, Duell oder Kampf).";
        }
        if (plugin.pvp().isDueling(target.getUniqueId()) || plugin.pvp().inCombat(target.getUniqueId())) {
            return target.getName() + " ist gerade im Kampf.";
        }
        if (target.getGameMode() == GameMode.SPECTATOR) return target.getName() + " ist gerade nicht erreichbar.";
        long cd = plugin.getConfig().getLong("friends.jump-cooldown-seconds", 30) * 1000L;
        Long last;
        synchronized (this) {
            last = lastJump.get(p.getUniqueId());
        }
        long now = System.currentTimeMillis();
        if (last != null && now - last < cd && !p.hasPermission("kuby.admin")) {
            return "Warte noch " + ((cd - (now - last)) / 1000 + 1) + " Sekunden.";
        }
        synchronized (this) {
            lastJump.put(p.getUniqueId(), now);
        }
        Msg.send(target, "<gray>" + p.getName() + " springt zu dir nach.");
        Teleports.delayed(plugin, p, () -> {
            // Ziel erst beim Teleport bestimmen, der Freund kann weitergelaufen sein
            if (!target.isOnline() || !areFriends(p.getUniqueId(), target.getUniqueId())) return null;
            if (target.isFlying() || target.isGliding() || !target.isOnGround()) {
                Msg.send(p, "<red>" + target.getName() + " ist gerade in der Luft. Versuch es gleich noch einmal.");
                return null;
            }
            return target.getLocation();
        }, target.getName());
        return null;
    }

    /** Beim Einloggen: offene Anfragen zeigen und Freunden im selben Modus Bescheid geben. */
    @org.bukkit.event.EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            int n = requestsOf(p.getUniqueId()).size();
            if (n > 0) Msg.send(p, "<gray>Du hast <white>" + n + "</white> offene Freundesanfrage" + (n == 1 ? "" : "n") + ". "
                    + "<click:run_command:'/friends'><green>[Ansehen]</click>");
            for (UUID id : friendsOf(p.getUniqueId())) {
                Player f = Bukkit.getPlayer(id);
                if (f != null && f.canSee(p) && areFriends(id, p.getUniqueId()) && plugin.playerSettings().get(id, "chat.friends")) {
                    Msg.send(f, "<green>● <white>" + p.getName() + " <gray>ist jetzt online.");
                }
            }
        }, 60L);
    }

    /** Beim Ausloggen: Freunden Bescheid geben (Schalter "Freunde online/offline"). */
    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        Player p = e.getPlayer();
        for (UUID id : friendsOf(p.getUniqueId())) {
            Player f = Bukkit.getPlayer(id);
            if (f != null && !f.equals(p) && f.canSee(p) && areFriends(id, p.getUniqueId())
                    && plugin.playerSettings().get(id, "chat.friends")) {
                Msg.send(f, "<red>● <white>" + p.getName() + " <gray>ist jetzt offline.");
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        friends.forEach((key, set) -> {
            if (!set.isEmpty()) out.set(key, set.stream().map(UUID::toString).toList());
        });
        requests.forEach((key, m) -> m.forEach((from, at) -> out.set(REQUESTS + "." + key.replace('|', '_') + "." + from, at)));
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("friends.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
