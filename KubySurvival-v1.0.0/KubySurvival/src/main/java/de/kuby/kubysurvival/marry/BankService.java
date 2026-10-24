package de.kuby.kubysurvival.marry;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubysurvival.KubySurvival;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bank in Survival: Ehe-Konto (für verheiratete Paare, überall zu eröffnen) und Konten mit Freunden (nur an der Bank
 * am Spawn zu eröffnen und zu verwalten). Ein- und Auszahlen geht überall bis bank.limit-away Coins je Buchung, an
 * der Bank (beim Bankier) ohne Grenze. Alle Mitglieder dürfen ein- und auszahlen, der Verlauf zeigt, wer was gebucht
 * hat. Gespeichert in bank.yml.
 */
public final class BankService {

    public enum Type { EHE, FREUNDE }

    /** Buchung: positiv = eingezahlt, negativ = abgehoben. */
    public record Entry(long time, UUID who, long amount) {}

    public static final class Account {
        public final String id;
        public final Type type;
        public String name;
        public UUID owner;
        public final List<UUID> members = new ArrayList<>();
        public long balance;
        public final Deque<Entry> log = new ArrayDeque<>();

        Account(String id, Type type, String name, UUID owner) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.owner = owner;
        }

        public boolean has(UUID id) {
            return members.contains(id);
        }
    }

    private static final Pattern NAME = Pattern.compile("[\\p{L}0-9 &'._\\-]{3,20}");
    private static final int LOG_SIZE = 30;

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final SurvivalNpc npcs;
    private final File file;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    /** Wo der Bankier steht (für "Zur Bank"). */
    private Location bankPoint;
    private long nextId = 1;

    public BankService(KubySurvival addon, KubyPlugins core, SurvivalNpc npcs) {
        this.addon = addon;
        this.core = core;
        this.npcs = npcs;
        this.file = new File(addon.getDataFolder(), "bank.yml");
        load();
    }

    // =================================================================================================================
    // Einstellungen
    // =================================================================================================================

    public long limitAway() {
        return core.getConfig().getLong("bank.limit-away", 10_000);
    }

    public int maxFriendAccounts() {
        return core.getConfig().getInt("bank.max-friend-accounts", 3);
    }

    public int maxMembers() {
        return core.getConfig().getInt("bank.max-members", 5);
    }

    private double bankRadius() {
        return core.getConfig().getDouble("bank.radius", 8);
    }

    // =================================================================================================================
    // Abfragen
    // =================================================================================================================

    public synchronized List<Account> of(UUID id) {
        List<Account> out = new ArrayList<>();
        Account ehe = marriage(id);
        if (ehe != null) out.add(ehe);
        for (Account a : accounts.values()) if (a.type == Type.FREUNDE && a.has(id)) out.add(a);
        return out;
    }

    public synchronized Account get(String id) {
        return id == null ? null : accounts.get(id);
    }

    public synchronized Account marriage(UUID id) {
        for (Account a : accounts.values()) if (a.type == Type.EHE && a.has(id)) return a;
        return null;
    }

    private int friendAccountsOf(UUID id) {
        int n = 0;
        for (Account a : accounts.values()) if (a.type == Type.FREUNDE && a.has(id)) n++;
        return n;
    }

    /** Steht der Spieler beim Bankier? */
    public boolean atBank(Player p) {
        return npcs.nearest(p.getLocation(), SurvivalNpc.Role.BANKIER, bankRadius()) != null;
    }

    public Location bankPoint() {
        return bankPoint == null ? null : bankPoint.clone();
    }

    public synchronized void setBankPoint(Location l) {
        bankPoint = l.clone();
        save();
    }

    public static String nameOf(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() == null ? "?" : op.getName();
    }

    private boolean survival(Player p) {
        return ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()));
    }

    // =================================================================================================================
    // Konten anlegen und verwalten
    // =================================================================================================================

    /** Ehe-Konto für das Paar eröffnen (partner = Ehepartner von p). Null bei Erfolg, sonst Fehlermeldung. */
    public synchronized String openMarriage(Player p, UUID partner) {
        if (!survival(p)) return "Die Bank gibt es nur in Survival.";
        if (partner == null) return "Ein Ehe-Konto könnt ihr erst nach der Hochzeit eröffnen.";
        if (marriage(p.getUniqueId()) != null) return "Ihr habt schon ein Ehe-Konto.";
        String id = "ehe-" + (nextId++);
        Account a = new Account(id, Type.EHE, "Ehe-Konto", p.getUniqueId());
        a.members.add(p.getUniqueId());
        a.members.add(partner);
        accounts.put(id, a);
        save();
        return null;
    }

    /** Konto mit Freunden eröffnen (nur an der Bank). */
    public synchronized String create(Player p, String name) {
        if (!survival(p)) return "Die Bank gibt es nur in Survival.";
        if (!atBank(p)) return "Konten mit Freunden eröffnest du an der Bank am Spawn.";
        String n = name == null ? "" : name.trim();
        if (!NAME.matcher(n).matches()) return "Der Name braucht 3 bis 20 Zeichen (Buchstaben, Zahlen, Leerzeichen).";
        if (friendAccountsOf(p.getUniqueId()) >= maxFriendAccounts()) {
            return "Du bist schon in " + maxFriendAccounts() + " Konten mit Freunden. Mehr gehen nicht.";
        }
        for (Account a : accounts.values()) {
            if (a.type == Type.FREUNDE && a.has(p.getUniqueId()) && a.name.equalsIgnoreCase(n)) return "So heißt schon eins deiner Konten.";
        }
        String id = "konto-" + (nextId++);
        Account a = new Account(id, Type.FREUNDE, n, p.getUniqueId());
        a.members.add(p.getUniqueId());
        accounts.put(id, a);
        save();
        return null;
    }

    /** Freund ins Konto aufnehmen (nur Besitzer, nur an der Bank, nur Freunde). */
    public synchronized String addMember(Player p, Account a, UUID target) {
        if (a == null || a.type != Type.FREUNDE) return "Dieses Konto gibt es nicht mehr.";
        if (!a.owner.equals(p.getUniqueId())) return "Nur wer das Konto eröffnet hat, nimmt neue Mitglieder auf.";
        if (!atBank(p)) return "Mitglieder nimmst du an der Bank am Spawn auf.";
        if (a.has(target)) return nameOf(target) + " ist schon dabei.";
        if (!core.friends().areFriends(p.getUniqueId(), target)) return "Aufnehmen kannst du nur Freunde (/friend add).";
        if (a.members.size() >= maxMembers()) return "Ein Konto hat höchstens " + maxMembers() + " Mitglieder.";
        if (friendAccountsOf(target) >= maxFriendAccounts()) return nameOf(target) + " ist schon in " + maxFriendAccounts() + " Konten.";
        a.members.add(target);
        save();
        Player t = Bukkit.getPlayer(target);
        if (t != null) {
            Msg.send(t, "<gold><b>BANK</b> <dark_gray>▶ <white>" + Msg.escape(p.getName()) + " <gray>hat dich ins Konto <white>"
                    + Msg.escape(a.name) + " <gray>aufgenommen. <click:run_command:'/bank'><yellow>[Bank öffnen]</click>");
        }
        return null;
    }

    public synchronized String removeMember(Player p, Account a, UUID target) {
        if (a == null || a.type != Type.FREUNDE) return "Dieses Konto gibt es nicht mehr.";
        if (!a.owner.equals(p.getUniqueId())) return "Nur wer das Konto eröffnet hat, entfernt Mitglieder.";
        if (!atBank(p)) return "Mitglieder entfernst du an der Bank am Spawn.";
        if (target.equals(a.owner)) return "Du kannst dich nicht selbst entfernen. Löse das Konto auf.";
        if (!a.members.remove(target)) return nameOf(target) + " ist nicht dabei.";
        save();
        Player t = Bukkit.getPlayer(target);
        if (t != null) Msg.send(t, "<gold><b>BANK</b> <dark_gray>▶ <gray>Du bist nicht mehr im Konto <white>" + Msg.escape(a.name) + "<gray>.");
        return null;
    }

    /** Mitglied verlässt ein Konto mit Freunden (das Geld bleibt im Konto). */
    public synchronized String leave(Player p, Account a) {
        if (a == null || a.type != Type.FREUNDE) return "Dieses Konto gibt es nicht mehr.";
        if (a.owner.equals(p.getUniqueId())) return "Als Besitzer löst du das Konto an der Bank auf.";
        if (!a.members.remove(p.getUniqueId())) return "Du bist nicht in diesem Konto.";
        save();
        return null;
    }

    /** Besitzer löst ein Konto mit Freunden auf: das Guthaben wird gleichmäßig verteilt (Rest an den Besitzer). */
    public synchronized String close(Player p, Account a) {
        if (a == null || a.type != Type.FREUNDE) return "Dieses Konto gibt es nicht mehr.";
        if (!a.owner.equals(p.getUniqueId())) return "Nur wer das Konto eröffnet hat, löst es auf.";
        if (!atBank(p)) return "Konten löst du an der Bank am Spawn auf.";
        payOut(a, a.owner, "Das Konto " + a.name + " wurde aufgelöst");
        accounts.remove(a.id);
        save();
        return null;
    }

    /** Scheidung: Ehe-Konto halbieren, jeder bekommt seinen Teil (auch offline). */
    public synchronized long splitMarriage(UUID a, UUID b) {
        Account acc = marriage(a);
        if (acc == null) return 0;
        long total = acc.balance;
        payOut(acc, b, "Das Ehe-Konto wurde bei der Scheidung geteilt");
        accounts.remove(acc.id);
        save();
        return total;
    }

    /** Verteilt das Guthaben gleichmäßig auf alle Mitglieder (Rest an rest), gutgeschrieben im Survival-Konto. */
    private void payOut(Account a, UUID rest, String why) {
        if (a.balance <= 0 || a.members.isEmpty()) return;
        long share = a.balance / a.members.size();
        long remainder = a.balance - share * a.members.size();
        for (UUID m : a.members) {
            long amount = share + (m.equals(rest) ? remainder : 0);
            if (amount <= 0) continue;
            core.modes().depositIn(m, ModeService.SURVIVAL, amount);
            Player online = Bukkit.getPlayer(m);
            if (online != null) {
                Msg.send(online, "<gold><b>BANK</b> <dark_gray>▶ <gray>" + Msg.escape(why) + ". Dein Anteil: <gold>"
                        + Msg.coins(amount) + " Coins");
            }
        }
        a.balance = 0;
    }

    // =================================================================================================================
    // Buchungen
    // =================================================================================================================

    public synchronized String deposit(Player p, Account a, long amount) {
        String err = check(p, a, amount);
        if (err != null) return err;
        if (a.balance > Long.MAX_VALUE / 4 - amount) return "Mehr passt nicht auf dieses Konto.";
        if (!core.economy().withdraw(p.getUniqueId(), amount)) return "So viele Coins hast du nicht.";
        a.balance += amount;
        book(p, a, amount);
        return null;
    }

    public synchronized String withdraw(Player p, Account a, long amount) {
        String err = check(p, a, amount);
        if (err != null) return err;
        if (amount > a.balance) return "So viel ist nicht auf dem Konto.";
        a.balance -= amount;
        core.economy().deposit(p.getUniqueId(), amount);
        book(p, a, -amount);
        return null;
    }

    private String check(Player p, Account a, long amount) {
        if (!survival(p)) return "Die Bank gibt es nur in Survival.";
        if (a == null || !accounts.containsKey(a.id)) return "Dieses Konto gibt es nicht mehr.";
        if (!a.has(p.getUniqueId())) return "Du bist nicht in diesem Konto.";
        if (amount <= 0) return "Der Betrag muss größer als 0 sein.";
        if (amount > limitAway() && !atBank(p)) {
            return "Unterwegs gehen höchstens " + Msg.coins(limitAway()) + " Coins je Buchung. Mehr an der Bank am Spawn.";
        }
        return null;
    }

    private void book(Player p, Account a, long amount) {
        a.log.addFirst(new Entry(System.currentTimeMillis(), p.getUniqueId(), amount));
        while (a.log.size() > LOG_SIZE) a.log.removeLast();
        save();
        String what = amount >= 0 ? "eingezahlt" : "abgehoben";
        for (UUID m : a.members) {
            if (m.equals(p.getUniqueId())) continue;
            Player o = Bukkit.getPlayer(m);
            if (o != null) {
                Msg.send(o, "<gold><b>BANK</b> <dark_gray>▶ <white>" + Msg.escape(p.getName()) + " <gray>hat <gold>"
                        + Msg.coins(Math.abs(amount)) + " Coins <gray>" + what + " (" + Msg.escape(a.name) + ", Stand <gold>"
                        + Msg.coins(a.balance) + "<gray>).");
            }
        }
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getLong("next-id", 1);
        bankPoint = Locs.parse(y.getString("bank-ort"));
        ConfigurationSection s = y.getConfigurationSection("konten");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) continue;
            try {
                Account a = new Account(id, Type.valueOf(c.getString("typ", "FREUNDE")), c.getString("name", "Konto"),
                        UUID.fromString(c.getString("besitzer", "")));
                for (String m : c.getStringList("mitglieder")) a.members.add(UUID.fromString(m));
                a.balance = Math.max(0, c.getLong("stand"));
                for (String e : c.getStringList("verlauf")) {
                    String[] p = e.split(";");
                    if (p.length == 3) a.log.addLast(new Entry(Long.parseLong(p[0]), UUID.fromString(p[1]), Long.parseLong(p[2])));
                }
                accounts.put(id, a);
            } catch (IllegalArgumentException ex) {
                addon.getLogger().warning("bank.yml: Konto " + id + " übersprungen (" + ex.getMessage() + ")");
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        if (bankPoint != null) y.set("bank-ort", Locs.serialize(bankPoint));
        for (Account a : accounts.values()) {
            String k = "konten." + a.id + ".";
            y.set(k + "typ", a.type.name());
            y.set(k + "name", a.name);
            y.set(k + "besitzer", a.owner.toString());
            List<String> m = new ArrayList<>();
            for (UUID u : a.members) m.add(u.toString());
            y.set(k + "mitglieder", m);
            y.set(k + "stand", a.balance);
            List<String> log = new ArrayList<>();
            for (Entry e : a.log) log.add(e.time() + ";" + e.who() + ";" + e.amount());
            y.set(k + "verlauf", log);
        }
        try {
            addon.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            addon.getLogger().warning("bank.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }
}
