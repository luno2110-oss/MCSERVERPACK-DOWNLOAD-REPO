package de.kuby.kubysurvival.marry;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.service.ModeService;
import de.kuby.kubyplugins.util.Locs;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Teleports;
import de.kuby.kubysurvival.KubySurvival;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Heiraten in Survival: Antrag mit /marry propose, Ja-Wort beim Priester in der Kirche (beide müssen dort stehen,
 * die Hochzeit zahlt, wer gefragt hat), Herz vor dem Namen in Chat und Tab, /marry tp zum Partner. Bei der Scheidung
 * wird das Ehe-Konto halbiert, danach dürfen beide eine Zeit lang nicht neu heiraten. Gespeichert in marry.yml.
 */
public final class MarryService implements Listener {

    public static final String HEART = "<#FF7AC0>❤</#FF7AC0> ";
    private static final String PREFIX = "<#FF7AC0><b>HEIRATEN</b> <dark_gray>▶ <gray>";
    private static final long DAY = 24L * 60 * 60 * 1000;

    /** Offener Antrag von from an to. */
    public record Proposal(UUID from, UUID to, long time) {}

    private final KubySurvival addon;
    private final KubyPlugins core;
    private final SurvivalNpc npcs;
    private final File file;
    private final Map<UUID, UUID> partner = new HashMap<>();
    private final Map<UUID, Long> since = new HashMap<>();
    private final Map<UUID, Long> blockedUntil = new HashMap<>();
    /** Offene Anträge, Schlüssel = wer gefragt wurde. */
    private final Map<UUID, Proposal> incoming = new HashMap<>();
    /** Nachrichten für Spieler, die offline waren (z. B. Scheidung). */
    private final Map<UUID, List<String>> notices = new HashMap<>();
    private Location church;
    private BankService bank;
    private MarryMenus menus;

    public MarryService(KubySurvival addon, KubyPlugins core, SurvivalNpc npcs) {
        this.addon = addon;
        this.core = core;
        this.npcs = npcs;
        this.file = new File(addon.getDataFolder(), "marry.yml");
        load();
        core.registerNamePrefix(id -> partnerOf(id) != null ? HEART : "");
    }

    public void wire(BankService bank, MarryMenus menus) {
        this.bank = bank;
        this.menus = menus;
    }

    // =================================================================================================================
    // Einstellungen und Abfragen
    // =================================================================================================================

    public long cost() {
        return core.getConfig().getLong("marry.cost", 5_000);
    }

    private long proposalMillis() {
        return core.getConfig().getLong("marry.proposal-minutes", 10) * 60_000L;
    }

    private long cooldownDays() {
        return core.getConfig().getLong("marry.divorce-cooldown-days", 7);
    }

    private double priestRadius() {
        return core.getConfig().getDouble("marry.priest-radius", 10);
    }

    public synchronized UUID partnerOf(UUID id) {
        return partner.get(id);
    }

    public synchronized long since(UUID id) {
        return since.getOrDefault(id, 0L);
    }

    /** Bis wann der Spieler nach einer Scheidung nicht heiraten darf (0 = darf). */
    public synchronized long blockedUntil(UUID id) {
        Long until = blockedUntil.get(id);
        if (until == null) return 0;
        if (until <= System.currentTimeMillis()) {
            blockedUntil.remove(id);
            return 0;
        }
        return until;
    }

    public synchronized Proposal incomingOf(UUID to) {
        Proposal pr = incoming.get(to);
        if (pr != null && System.currentTimeMillis() - pr.time() > proposalMillis()) {
            incoming.remove(to);
            return null;
        }
        return pr;
    }

    public synchronized Proposal outgoingOf(UUID from) {
        for (Proposal pr : new ArrayList<>(incoming.values())) {
            if (pr.from().equals(from)) return incomingOf(pr.to());
        }
        return null;
    }

    /** Restzeit eines Antrags in Minuten (aufgerundet). */
    public long minutesLeft(Proposal pr) {
        long left = proposalMillis() - (System.currentTimeMillis() - pr.time());
        return Math.max(1, (left + 59_999) / 60_000);
    }

    public Location church() {
        return church == null ? null : church.clone();
    }

    public synchronized void setChurch(Location l) {
        church = l.clone();
        save();
    }

    private boolean survival(Player p) {
        return ModeService.SURVIVAL.equals(core.modes().modeOf(p.getUniqueId()));
    }

    static String name(UUID id) {
        return BankService.nameOf(id);
    }

    public static void send(Player p, String mini) {
        Msg.send(p, PREFIX + mini);
    }

    // =================================================================================================================
    // Antrag
    // =================================================================================================================

    public synchronized String propose(Player p, Player t) {
        UUID me = p.getUniqueId();
        if (!survival(p)) return "Heiraten gibt es nur in Survival.";
        if (t == null || !t.isOnline()) return "Dieser Spieler ist nicht online.";
        if (t.equals(p)) return "Dich selbst kannst du nicht heiraten.";
        if (!survival(t)) return t.getName() + " ist gerade nicht in Survival.";
        if (partnerOf(me) != null) return "Du bist schon verheiratet.";
        if (partnerOf(t.getUniqueId()) != null) return t.getName() + " ist schon verheiratet.";
        if (!core.playerSettings().get(t.getUniqueId(), "marry") && !core.friends().areFriends(me, t.getUniqueId())) {
            return t.getName() + " nimmt gerade keine Heiratsanträge an.";
        }
        if (blockedUntil(me) > 0) return "Nach deiner Scheidung darfst du erst ab " + date(blockedUntil(me)) + " wieder heiraten.";
        if (blockedUntil(t.getUniqueId()) > 0) return t.getName() + " darf gerade nicht heiraten (Scheidung ist noch nicht lange her).";
        Proposal mine = outgoingOf(me);
        if (mine != null) {
            return mine.to().equals(t.getUniqueId()) ? "Du hast " + t.getName() + " schon gefragt."
                    : "Du hast schon einen offenen Antrag an " + name(mine.to()) + ". /marry cancel zieht ihn zurück.";
        }
        Proposal back = incomingOf(me);
        if (back != null && back.from().equals(t.getUniqueId())) {
            return t.getName() + " hat dir schon einen Antrag gemacht. Geht zum Priester, dort sagst du Ja.";
        }
        if (incomingOf(t.getUniqueId()) != null) return t.getName() + " hat schon einen offenen Antrag.";
        if (core.economy().balance(me) < cost()) {
            return "Die Hochzeit kostet " + Msg.coins(cost()) + " Coins, die zahlst du als der, der fragt.";
        }
        incoming.put(t.getUniqueId(), new Proposal(me, t.getUniqueId(), System.currentTimeMillis()));
        long min = proposalMillis() / 60_000;
        send(p, "Dein Antrag an <white>" + t.getName() + "</white> ist raus. Geht zusammen zum Priester in der Kirche, dort sagt "
                + t.getName() + " Ja. Der Antrag gilt " + min + " Minuten. <click:run_command:'/marry church'><#FF7AC0>[Zur Kirche]</click>");
        Msg.send(t, HEART + "<white><b>" + p.getName() + "</b> <gray>macht dir einen Antrag!");
        send(t, "Geht beide zum Priester in der Kirche, dort sagst du mit Rechtsklick Ja. "
                + "<click:run_command:'/marry church'><#FF7AC0>[Zur Kirche]</click> <click:run_command:'/marry deny'><red>[Ablehnen]</click>");
        t.playSound(t, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        t.playSound(t, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        de.kuby.kubyplugins.util.Fx.particle(core, t.getLocation().add(0, 2.1, 0), Particle.HEART, 5, 0.4, 0.2, 0.4, 0);
        p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        return null;
    }

    public synchronized String deny(Player p) {
        Proposal pr = incomingOf(p.getUniqueId());
        if (pr == null) return "Du hast keinen offenen Antrag.";
        incoming.remove(p.getUniqueId());
        send(p, "Antrag von <white>" + name(pr.from()) + "</white> abgelehnt.");
        Player from = Bukkit.getPlayer(pr.from());
        if (from != null) send(from, "<white>" + p.getName() + "</white> hat deinen Antrag abgelehnt.");
        return null;
    }

    public synchronized String cancel(Player p) {
        Proposal pr = outgoingOf(p.getUniqueId());
        if (pr == null) return "Du hast keinen offenen Antrag gemacht.";
        incoming.remove(pr.to());
        send(p, "Antrag an <white>" + name(pr.to()) + "</white> zurückgezogen.");
        Player to = Bukkit.getPlayer(pr.to());
        if (to != null) send(to, "<white>" + p.getName() + "</white> hat den Antrag zurückgezogen.");
        return null;
    }

    // =================================================================================================================
    // Hochzeit beim Priester
    // =================================================================================================================

    /** Ja-Wort: wer gefragt wurde, steht mit dem anderen beim Priester. */
    public synchronized String accept(Player p) {
        UUID me = p.getUniqueId();
        if (!survival(p)) return "Heiraten gibt es nur in Survival.";
        Proposal pr = incomingOf(me);
        if (pr == null) {
            Proposal mine = outgoingOf(me);
            if (mine != null) return "Beim Priester sagt " + name(mine.to()) + " Ja. Wartet zusammen hier.";
            return "Du hast keinen offenen Antrag. Frag jemanden mit /marry propose <Spieler>.";
        }
        Player from = Bukkit.getPlayer(pr.from());
        if (from == null) return name(pr.from()) + " ist gerade nicht online.";
        if (!survival(from)) return from.getName() + " ist gerade nicht in Survival.";
        Entity priest = npcs.nearest(p.getLocation(), SurvivalNpc.Role.PRIESTER, priestRadius());
        if (priest == null) return "Geh zum Priester in der Kirche (/marry church), dort sagst du Ja.";
        Location pl = priest.getLocation();
        if (from.getWorld() != pl.getWorld() || from.getLocation().distanceSquared(pl) > priestRadius() * priestRadius()) {
            return from.getName() + " muss auch beim Priester stehen.";
        }
        if (partnerOf(me) != null || partnerOf(from.getUniqueId()) != null) {
            incoming.remove(me);
            return "Einer von euch ist schon verheiratet.";
        }
        if (!core.economy().withdraw(from.getUniqueId(), cost())) {
            send(from, "Für die Hochzeit fehlen dir Coins (" + Msg.coins(cost()) + ").");
            return from.getName() + " hat nicht genug Coins für die Hochzeit (" + Msg.coins(cost()) + ").";
        }
        incoming.remove(me);
        incoming.remove(from.getUniqueId());
        long now = System.currentTimeMillis();
        partner.put(me, from.getUniqueId());
        partner.put(from.getUniqueId(), me);
        since.put(me, now);
        since.put(from.getUniqueId(), now);
        save();
        ceremony(from, p, pl);
        return null;
    }

    private void ceremony(Player a, Player b, Location priest) {
        String names = "<white>" + a.getName() + " <#FF7AC0>❤</#FF7AC0> <white>" + b.getName();
        Title title = Title.title(Msg.parse("<#FF7AC0><b>Ihr seid verheiratet!"), Msg.parse(names),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(800)));
        for (Player p : List.of(a, b)) {
            p.showTitle(title);
            Msg.send(p, "<#FF7AC0><b>Priester</b> <dark_gray>▶ <white>Hiermit erkläre ich euch zu Ehepartnern. Alles Gute!");
            send(p, "Mit <white>/bank</white> eröffnet ihr ein Ehe-Konto, mit <white>/marry tp</white> springst du zu deinem Schatz.");
            p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
            core.refreshTabName(p);
        }
        Location top = priest.clone().add(0, 2.4, 0);
        for (int i = 0; i < 3; i++) {
            int k = i;
            addon.getServer().getScheduler().runTaskLater(addon, () -> {
                top.getWorld().playSound(top, Sound.BLOCK_BELL_USE, 1.2f, 1.0f - 0.1f * k);
                de.kuby.kubyplugins.util.Fx.particle(core, top, Particle.FIREWORK, 40, 0.6, 0.6, 0.6, 0.08);
            }, 10L + 16L * i);
        }
        int[] ticks = {0};
        addon.getServer().getScheduler().runTaskTimer(addon, task -> {
            for (Player p : List.of(a, b)) {
                if (p.isOnline()) de.kuby.kubyplugins.util.Fx.particle(core, p.getLocation().add(0, 1.2, 0), Particle.HEART, 2, 0.5, 0.6, 0.5, 0);
            }
            if (++ticks[0] >= 16) task.cancel();
        }, 0L, 5L);
        String msg = HEART + "<white>" + a.getName() + " <gray>und <white>" + b.getName()
                + " <gray>haben sich in der Kirche das Ja-Wort gegeben!";
        core.notifications().send("notify.wedding", Msg.parse(msg), o -> survival(o) || o.equals(a) || o.equals(b));
    }

    // =================================================================================================================
    // Scheidung
    // =================================================================================================================

    public synchronized String divorce(Player p) {
        UUID me = p.getUniqueId();
        UUID other = partnerOf(me);
        if (other == null) return "Du bist nicht verheiratet.";
        long total = bank == null ? 0 : bank.splitMarriage(me, other);
        partner.remove(me);
        partner.remove(other);
        since.remove(me);
        since.remove(other);
        long until = System.currentTimeMillis() + cooldownDays() * DAY;
        blockedUntil.put(me, until);
        blockedUntil.put(other, until);
        String split = total > 0 ? " Das Ehe-Konto (" + Msg.coins(total) + " Coins) wurde halbiert." : "";
        send(p, "Ihr seid geschieden." + split + " Neu heiraten dürft ihr ab " + date(until) + ".");
        Player o = Bukkit.getPlayer(other);
        String note = "<white>" + p.getName() + "</white> hat sich von dir scheiden lassen." + split
                + " Neu heiraten darfst du ab " + date(until) + ".";
        if (o != null) {
            send(o, note);
            core.refreshTabName(o);
        } else {
            notices.computeIfAbsent(other, k -> new ArrayList<>()).add(note);
        }
        core.refreshTabName(p);
        save();
        return null;
    }

    // =================================================================================================================
    // Teleports
    // =================================================================================================================

    public String tp(Player p) {
        if (!survival(p)) return "Das geht nur in Survival.";
        UUID other = partnerOf(p.getUniqueId());
        if (other == null) return "Du bist nicht verheiratet.";
        Player o = Bukkit.getPlayer(other);
        if (o == null) return name(other) + " ist gerade nicht online.";
        if (!survival(o)) return o.getName() + " ist gerade nicht in Survival.";
        p.closeDialog();
        Teleports.delayed(core, p, o.getLocation(), o.getName());
        return null;
    }

    public String toChurch(Player p) {
        if (!survival(p)) return "Die Kirche gibt es nur in Survival.";
        if (church == null) return "Die Kirche ist noch nicht eingerichtet.";
        p.closeDialog();
        Teleports.delayed(core, p, church(), "Kirche");
        return null;
    }

    public static String date(long millis) {
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                .format(java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("Europe/Berlin")));
    }

    // =================================================================================================================
    // Figuren und Join
    // =================================================================================================================

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        SurvivalNpc.Role role = npcs.roleOf(e.getRightClicked());
        if (role == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!survival(p)) {
            send(p, "Das gibt es nur in Survival.");
            return;
        }
        if (role == SurvivalNpc.Role.BANKIER) {
            menus.bank(p);
            return;
        }
        if (incomingOf(p.getUniqueId()) != null) {
            String err = accept(p);
            if (err != null) send(p, "<red>" + Msg.escape(err));
            return;
        }
        Proposal mine = outgoingOf(p.getUniqueId());
        if (mine != null) {
            send(p, "Beim Priester sagt <white>" + name(mine.to()) + "</white> Ja. Wartet zusammen hier.");
            return;
        }
        menus.marry(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        List<String> list;
        synchronized (this) {
            list = notices.remove(p.getUniqueId());
            if (list != null) save();
        }
        addon.getServer().getScheduler().runTaskLater(addon, () -> {
            if (!p.isOnline()) return;
            if (list != null) for (String n : list) send(p, n);
            UUID other = partnerOf(p.getUniqueId());
            if (other != null) {
                Player o = Bukkit.getPlayer(other);
                if (o != null) Msg.send(o, HEART + "<white>" + p.getName() + " <gray>ist jetzt online.");
            }
        }, 40L);
    }

    // =================================================================================================================
    // Speichern
    // =================================================================================================================

    private synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        church = Locs.parse(y.getString("kirche"));
        for (String line : y.getStringList("ehen")) {
            String[] p = line.split(";");
            if (p.length < 3) continue;
            try {
                UUID a = UUID.fromString(p[0]), b = UUID.fromString(p[1]);
                long t = Long.parseLong(p[2]);
                partner.put(a, b);
                partner.put(b, a);
                since.put(a, t);
                since.put(b, t);
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection s = y.getConfigurationSection("sperre");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    blockedUntil.put(UUID.fromString(k), s.getLong(k));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection n = y.getConfigurationSection("nachrichten");
        if (n != null) {
            for (String k : n.getKeys(false)) {
                try {
                    notices.put(UUID.fromString(k), new ArrayList<>(n.getStringList(k)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        if (church != null) y.set("kirche", Locs.serialize(church));
        List<String> ehen = new ArrayList<>();
        for (Map.Entry<UUID, UUID> e : partner.entrySet()) {
            if (e.getKey().toString().compareTo(e.getValue().toString()) < 0) {
                ehen.add(e.getKey() + ";" + e.getValue() + ";" + since.getOrDefault(e.getKey(), 0L));
            }
        }
        y.set("ehen", ehen);
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : blockedUntil.entrySet()) {
            if (e.getValue() > now) y.set("sperre." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, List<String>> e : notices.entrySet()) y.set("nachrichten." + e.getKey(), e.getValue());
        try {
            addon.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            addon.getLogger().warning("marry.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }
}
