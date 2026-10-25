package de.kuby.kubyplugins.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
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
        private final Map<String, String> homes = new LinkedHashMap<>();
        private long bank;
        private long xp;
        private ItemStack[] storage = new ItemStack[54];
        private Inventory storageInv;

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
        public long xp() { return xp; }
        public Map<String, String> homes() { return Collections.unmodifiableMap(homes); }
    }

    /** Halter des Team-Lagers (ein Inventar-Objekt pro Team, damit alle denselben Inhalt sehen). */
    public static final class StorageHolder implements InventoryHolder {
        public final Team team;

        StorageHolder(Team team) {
            this.team = team;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return team.storageInv;
        }
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
            String oldHome = yml.getString(key + ".home");
            if (oldHome != null) t.homes.put("home", oldHome);
            var hs = yml.getConfigurationSection(key + ".homes");
            if (hs != null) for (String h : hs.getKeys(false)) t.homes.put(h, hs.getString(h));
            t.bank = yml.getLong(key + ".bank");
            t.xp = yml.getLong(key + ".xp");
            var st = yml.getConfigurationSection(key + ".storage");
            if (st != null) {
                for (String slot : st.getKeys(false)) {
                    try {
                        int i = Integer.parseInt(slot);
                        if (i >= 0 && i < 54) t.storage[i] = st.getItemStack(slot);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
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

    // ---------- Team-Level ----------

    public static long xpFor(int level) {
        return 200L * level * (level - 1);
    }

    public int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("team.max-level", 10));
    }

    public int level(Team t) {
        int l = 1;
        while (l < maxLevel() && xpFor(l + 1) <= t.xp) l++;
        return l;
    }

    /** Plätze im Team: Grundwert plus je Level (team.members-per-level). */
    public int maxMembers(Team t) {
        return maxMembers() + (level(t) - 1) * plugin.getConfig().getInt("team.members-per-level", 1);
    }

    /** Team-Homes: 1, dazu eins je zwei Level (höchstens 5). */
    public int homeLimit(Team t) {
        return Math.min(5, 1 + (level(t) - 1) / 2);
    }

    /** Reihen im Team-Lager: 1, dazu eine je zwei Level (höchstens 6). */
    public int storageRows(Team t) {
        return Math.min(6, 1 + (level(t) - 1) / 2);
    }

    /** Team-XP für ein Mitglied (z. B. aktive Spielzeit). Aufstiege werden dem Team gemeldet. */
    public synchronized void addXp(UUID member, long amount) {
        Team t = of(member);
        if (t == null || amount <= 0) return;
        int before = level(t);
        t.xp += amount;
        int after = level(t);
        if (after > before) {
            for (UUID m : t.members) {
                Player p = Bukkit.getPlayer(m);
                if (p != null) Msg.send(p, "<gold><b>Team-Level " + after + "!</b> <gray>Jetzt " + maxMembers(t) + " Plätze, "
                        + homeLimit(t) + " Team-Homes, Lager mit " + storageRows(t) + " Reihen.");
            }
        }
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
        if (!plugin.playerSettings().get(target.getUniqueId(), "team.invites")
                && !plugin.friends().areFriends(inviter.getUniqueId(), target.getUniqueId())) {
            return target.getName() + " nimmt gerade keine Team-Anfragen an.";
        }
        if (t.members.size() >= maxMembers(t)) return "Dein Team ist voll.";
        invites.put(mkey(target.getUniqueId()), key(t));
        return null;
    }

    public String accept(UUID id) {
        if (of(id) != null) return "Du bist schon in einem Team.";
        String tk = invites.remove(mkey(id));
        if (tk == null) return "Keine offene Einladung.";
        Team t = teams.get(tk);
        if (t == null) return "Dieses Team existiert nicht mehr.";
        if (t.members.size() >= maxMembers(t)) return "Das Team ist inzwischen voll.";
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
            payOut(t, id);
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
        payOut(t, p.getUniqueId());
        for (UUID m : t.members) memberOf.remove(t.mode + "|" + m);
        teams.remove(key(t));
        save();
        return null;
    }

    /** Kasse und Lager eines aufgelösten Teams gehen an den, der es auflöst (bzw. als Letzter geht). */
    private void payOut(Team t, UUID to) {
        if (t.bank > 0) plugin.modes().depositIn(to, t.mode, t.bank);
        t.bank = 0;
        if (t.storageInv != null) {
            for (org.bukkit.entity.HumanEntity v : List.copyOf(t.storageInv.getViewers())) v.closeInventory();
            t.storage = t.storageInv.getContents();
        }
        for (ItemStack it : t.storage) if (it != null && !it.getType().isAir()) plugin.modes().abholboxIn(to, t.mode, it);
        t.storage = new ItemStack[54];
    }

    public String setHome(Player p) {
        return setHome(p, "home");
    }

    public String setHome(Player p, String rawName) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        String name = clean(rawName).toLowerCase();
        if (name.isEmpty()) name = "home";
        if (!t.homes.containsKey(name) && t.homes.size() >= homeLimit(t)) {
            return "Euer Team-Level erlaubt " + homeLimit(t) + " Team-Home(s). Lösch eins oder steigt im Level auf.";
        }
        String blocked = plugin.homes().blockedReason(p.getLocation());
        if (blocked != null) return blocked;
        t.homes.put(name, Locs.serialize(p.getLocation()));
        save();
        return null;
    }

    public String delHome(Player p, String rawName) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        if (t.homes.remove(clean(rawName).toLowerCase()) == null) return "Dieses Team-Home gibt es nicht.";
        save();
        return null;
    }

    public Location homeOf(UUID id) {
        Team t = of(id);
        if (t == null || t.homes.isEmpty()) return null;
        return homeOf(id, t.homes.containsKey("home") ? "home" : t.homes.keySet().iterator().next());
    }

    public Location homeOf(UUID id, String name) {
        Team t = of(id);
        if (t == null) return null;
        String raw = t.homes.get(clean(name).toLowerCase());
        return raw == null ? null : Locs.parse(raw);
    }

    /** Abheben aus der Kasse (nur Teamchef). */
    public String withdraw(Player p, long amount) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        if (!t.leader.equals(p.getUniqueId())) return "Nur der Teamchef kann abheben.";
        if (amount <= 0 || amount > t.bank) return "In der Kasse sind " + Msg.coins(t.bank) + "$.";
        t.bank -= amount;
        plugin.economy().deposit(p.getUniqueId(), amount);
        save();
        return null;
    }

    /** Mitglied entfernen (nur Teamchef). */
    public String kick(Player p, UUID target) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        if (!t.leader.equals(p.getUniqueId())) return "Nur der Teamchef kann Mitglieder entfernen.";
        if (target.equals(p.getUniqueId())) return "Du kannst dich nicht selbst entfernen.";
        if (!t.members.remove(target)) return "Dieser Spieler ist nicht in deinem Team.";
        memberOf.remove(t.mode + "|" + target);
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) {
            if (t.storageInv != null && t.storageInv.getViewers().contains(kicked)) kicked.closeInventory();
            Msg.send(kicked, "<red>Du wurdest aus dem Team " + t.name + " entfernt.");
        }
        save();
        return null;
    }

    /** Team-Lager öffnen (alle Mitglieder, nur im Modus des Teams). */
    public String openStorage(Player p) {
        Team t = of(p.getUniqueId());
        if (t == null) return "Du hast kein Team.";
        int size = storageRows(t) * 9;
        if (t.storageInv == null || (t.storageInv.getSize() != size && t.storageInv.getViewers().isEmpty())) {
            if (t.storageInv != null) t.storage = t.storageInv.getContents();
            t.storageInv = Bukkit.createInventory(new StorageHolder(t), size, Msg.parse("<dark_gray>Team-Lager " + t.name));
            ItemStack[] content = new ItemStack[size];
            List<ItemStack> overflow = new ArrayList<>();
            for (int i = 0; i < t.storage.length; i++) {
                if (t.storage[i] == null) continue;
                if (i < size) content[i] = t.storage[i];
                else overflow.add(t.storage[i]);
            }
            t.storageInv.setContents(content);
            for (ItemStack it : overflow) t.storageInv.addItem(it);
        }
        p.openInventory(t.storageInv);
        return null;
    }

    /** Lager-Inhalt übernehmen (beim Schließen). */
    public synchronized void storageClosed(Team t) {
        if (t.storageInv != null) t.storage = java.util.Arrays.copyOf(t.storageInv.getContents(), 54);
        save();
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
            for (Map.Entry<String, String> h : t.homes.entrySet()) out.set(k + ".homes." + h.getKey(), h.getValue());
            out.set(k + ".bank", t.bank);
            out.set(k + ".xp", t.xp);
            ItemStack[] content = t.storageInv != null ? t.storageInv.getContents() : t.storage;
            for (int i = 0; i < content.length; i++) {
                if (content[i] != null && !content[i].getType().isAir()) out.set(k + ".storage." + i, content[i]);
            }
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
