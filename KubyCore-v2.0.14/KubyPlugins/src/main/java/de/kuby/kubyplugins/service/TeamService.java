package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Teams pro Modus. Ein Team gehört zu einem Modus, gleiche Namen sind in verschiedenen Modi erlaubt.
 * Ein Spieler ist nur in dem Modus im Team, in dem es gegründet wurde.
 * Methoden geben bei Fehlern eine Meldung zurück, bei Erfolg null.
 */
public final class TeamService {

    public static final class Team {
        private final String mode;
        private final String name;
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();
        private String home;
        private long bank;

        private Team(String mode, String name, UUID leader) {
            this.mode = mode;
            this.name = name;
            this.leader = leader;
            this.members.add(leader);
        }

        public String mode() { return mode; }
        public String name() { return name; }
        public UUID leader() { return leader; }
        public Set<UUID> members() { return Collections.unmodifiableSet(members); }
        public long bank() { return bank; }
    }

    private final KubyPlugins plugin;
    private final File file;
    private final Map<String, Team> teams = new LinkedHashMap<>();       // modus|name
    private final Map<String, String> memberOf = new HashMap<>();        // modus|uuid -> modus|name
    private final Map<String, String> invites = new HashMap<>();         // modus|uuid -> modus|name
    private final Map<String, Set<String>> allies = new HashMap<>();     // modus|name -> modus|name

    public TeamService(KubyPlugins plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            String tk = key.contains("|") ? key : "smp|" + key;          // altes Format gehört zum SMP
            String mode = tk.substring(0, tk.indexOf('|'));
            String name = tk.substring(tk.indexOf('|') + 1);
            String leaderRaw = yml.getString(key + ".leader");
            if (leaderRaw == null) continue;
            Team t = new Team(mode, name, UUID.fromString(leaderRaw));
            t.members.clear();
            for (String m : yml.getStringList(key + ".members")) t.members.add(UUID.fromString(m));
            t.home = yml.getString(key + ".home");
            t.bank = yml.getLong(key + ".bank");
            teams.put(tk, t);
            for (String a : yml.getStringList(key + ".allies")) {
                allies.computeIfAbsent(tk, k -> new HashSet<>()).add(a.contains("|") ? a : "smp|" + a);
            }
            for (UUID m : t.members) memberOf.put(mode + "|" + m, tk);
        }
    }

    private String mode(UUID id) {
        return plugin.modes().modeOf(id);
    }

    private String mkey(UUID id) {
        return mode(id) + "|" + id;
    }

    private static String key(Team t) {
        return t.mode + "|" + t.name;
    }

    public Team of(UUID id) {
        String tk = memberOf.get(mkey(id));
        return tk == null ? null : teams.get(tk);
    }

    public int maxMembers() {
        return plugin.getConfig().getInt("team.max-members", 5);
    }

    public String create(Player p, String rawName) {
        UUID id = p.getUniqueId();
        if (of(id) != null) return "Du bist schon in einem Team.";
        String name = clean(rawName);
        if (name.length() < 3) return "Teamname: mindestens 3 Zeichen (Buchstaben, Zahlen, _).";
        String mode = mode(id);
        if (teams.containsKey(mode + "|" + name)) return "Dieser Teamname ist in diesem Modus vergeben.";
        long cost = plugin.getConfig().getLong("team.create-cost", 1000);
        if (!plugin.economy().withdraw(id, cost)) {
            return "Gründen kostet " + Msg.coins(cost) + "$.";
        }
        Team t = new Team(mode, name, id);
        teams.put(key(t), t);
        memberOf.put(mkey(id), key(t));
        save();
        return null;
    }

    public String invite(Player inviter, Player target) {
        Team t = of(inviter.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        if (!t.leader.equals(inviter.getUniqueId())) return "Nur der Teamchef kann einladen.";
        if (target.equals(inviter)) return "Du kannst dich nicht selbst einladen.";
        if (!mode(target.getUniqueId()).equals(t.mode)) return "Dieser Spieler ist gerade in einem anderen Modus.";
        if (of(target.getUniqueId()) != null) return "Dieser Spieler ist schon in einem Team.";
        if (t.members.size() >= maxMembers()) return "Dein Team ist voll.";
        invites.put(mkey(target.getUniqueId()), key(t));
        return null;
    }

    public String accept(UUID id) {
        if (of(id) != null) return "Du bist schon in einem Team.";
        String tk = invites.remove(mkey(id));
        if (tk == null) return "Keine offene Einladung.";
        Team t = teams.get(tk);
        if (t == null) return "Dieses Team existiert nicht mehr.";
        if (t.members.size() >= maxMembers()) return "Das Team ist inzwischen voll.";
        t.members.add(id);
        memberOf.put(mkey(id), tk);
        save();
        return null;
    }

    public String leave(UUID id) {
        Team t = of(id);
        if (t == null) return "Du hast kein Team.";
        t.members.remove(id);
        memberOf.remove(mkey(id));
        if (t.members.isEmpty()) {
            teams.remove(key(t));
        } else if (t.leader.equals(id)) {
            t.leader = t.members.iterator().next();
        }
        save();
        return null;
    }

    public String disband(Player p) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        if (!t.leader.equals(p.getUniqueId())) return "Nur der Teamchef kann das Team auflösen.";
        for (UUID m : t.members) memberOf.remove(t.mode + "|" + m);
        teams.remove(key(t));
        save();
        return null;
    }

    public String setHome(Player p) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        t.home = Locs.serialize(p.getLocation());
        save();
        return null;
    }

    public Location homeOf(UUID id) {
        Team t = of(id);
        if (t == null || t.home == null) return null;
        return Locs.parse(t.home);
    }

    public String deposit(Player p, long amount) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        if (amount <= 0) return "Ungültiger Betrag.";
        if (!plugin.economy().withdraw(p.getUniqueId(), amount)) return "Nicht genug$.";
        t.bank += amount;
        save();
        return null;
    }

    /** Verbündet das eigene Team mit einem anderen im selben Modus. Nur der Teamchef kann das. */
    public synchronized String ally(Player p, String otherName) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        if (!t.leader.equals(p.getUniqueId())) return "Nur der Teamchef kann Allianzen schließen.";
        Team other = teams.get(t.mode + "|" + clean(otherName));
        if (other == null) return "Dieses Team gibt es nicht.";
        if (other == t) return "Du kannst nicht mit dir selbst verbündet sein.";
        allies.computeIfAbsent(key(t), k -> new HashSet<>()).add(key(other));
        allies.computeIfAbsent(key(other), k -> new HashSet<>()).add(key(t));
        save();
        return null;
    }

    public synchronized boolean areAllied(Team a, Team b) {
        if (a == null || b == null) return false;
        return allies.getOrDefault(key(a), Set.of()).contains(key(b));
    }

    public synchronized List<Team> all() {
        return new ArrayList<>(teams.values());
    }

    /** Admin: Team ohne Rückfrage auflösen. */
    public synchronized String adminDisband(Team t) {
        if (teams.remove(key(t)) == null) return "Team nicht gefunden.";
        for (UUID m : t.members) memberOf.remove(t.mode + "|" + m);
        save();
        return null;
    }

    public synchronized void save() {
        YamlConfiguration out = new YamlConfiguration();
        for (Team t : teams.values()) {
            String k = key(t);
            out.set(k + ".leader", t.leader.toString());
            out.set(k + ".members", t.members.stream().map(UUID::toString).toList());
            if (t.home != null) out.set(k + ".home", t.home);
            out.set(k + ".bank", t.bank);
            out.set(k + ".allies", List.copyOf(allies.getOrDefault(k, Set.of())));
        }
        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("teams.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private static String clean(String name) {
        String clean = name.replaceAll("[^A-Za-z0-9_]", "");
        return clean.substring(0, Math.min(16, clean.length()));
    }
}
