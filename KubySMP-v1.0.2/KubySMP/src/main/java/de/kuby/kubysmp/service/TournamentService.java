package de.kuby.kubysmp.service;

import de.kuby.kubyplugins.KubyPlugins;
import de.kuby.kubyplugins.util.Msg;
import de.kuby.kubyplugins.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * K.O.-Turnier. Jedes Match ist ein Duell in der Duellarena, wer verliert, scheidet aus.
 * Siege und der Titel bringen Trophäen. Matches verlieren keine Trophäen.
 * Starten und verwalten kann es nur ein Admin (Verwaltung im Turnier-Menü).
 */
public final class TournamentService {

    public enum State { CLOSED, REGISTRATION, RUNNING }

    private final KubyPlugins plugin;
    private State state = State.CLOSED;
    private final Set<UUID> entrants = new LinkedHashSet<>();
    private final List<UUID> alive = new ArrayList<>();
    private final List<UUID> nextRound = new ArrayList<>();
    private final Map<UUID, UUID> matchOf = new HashMap<>();
    private int round;
    private int pending;

    public TournamentService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public synchronized State state() { return state; }
    public synchronized Set<UUID> entrants() { return new LinkedHashSet<>(entrants); }
    public synchronized List<UUID> alive() { return new ArrayList<>(alive); }
    public synchronized int round() { return round; }

    /** Laufende Matches als Paare, jedes Paar nur einmal. */
    public synchronized List<UUID[]> matches() {
        List<UUID[]> list = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Map.Entry<UUID, UUID> e : matchOf.entrySet()) {
            if (seen.contains(e.getKey())) continue;
            seen.add(e.getKey());
            seen.add(e.getValue());
            list.add(new UUID[]{e.getKey(), e.getValue()});
        }
        return list;
    }

    public synchronized boolean isMatchPair(UUID a, UUID b) {
        return b.equals(matchOf.get(a));
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String open() {
        if (state != State.CLOSED) return "Es läuft bereits ein Turnier.";
        state = State.REGISTRATION;
        entrants.clear();
        announce("<gold>Die Turnier-Anmeldung ist offen! <gray>Mit <white>/turnier join <gray>beitreten.");
        return null;
    }

    public synchronized String join(Player p) {
        if (state != State.REGISTRATION) return "Die Anmeldung ist nicht offen.";
        if (entrants.size() >= plugin.getConfig().getInt("tournament.max-players", 16)) return "Das Turnier ist voll.";
        if (plugin.pvp().isDueling(p.getUniqueId())) return "Du bist gerade im Duell.";
        if (!entrants.add(p.getUniqueId())) return "Du bist schon angemeldet.";
        Msg.send(p, "<green>Du bist im Turnier angemeldet (" + entrants.size() + " Teilnehmer).");
        Sfx.play(plugin, p, Sfx.Kind.SUCCESS);
        return null;
    }

    public synchronized String leave(UUID id) {
        if (state != State.REGISTRATION) return "Du kannst dich gerade nicht abmelden.";
        if (!entrants.remove(id)) return "Du bist nicht angemeldet.";
        return null;
    }

    /** Admin: Teilnehmer aus der Anmeldung entfernen. */
    public synchronized String kick(UUID id) {
        if (state != State.REGISTRATION) return "Teilnehmer können nur während der Anmeldung entfernt werden.";
        if (!entrants.remove(id)) return "Dieser Spieler ist nicht angemeldet.";
        return null;
    }

    /** Gibt null bei Erfolg zurück, sonst eine Fehlermeldung. */
    public synchronized String start() {
        if (state != State.REGISTRATION) return "Keine Anmeldung offen.";
        if (entrants.size() < 2) return "Mindestens 2 Teilnehmer nötig.";
        state = State.RUNNING;
        round = 1;
        alive.clear();
        alive.addAll(entrants);
        Collections.shuffle(alive);
        announce("<gold>Das Turnier startet mit <white>" + alive.size() + " <gold>Spielern!");
        startRound();
        return null;
    }

    public synchronized String cancel() {
        if (state == State.CLOSED) return "Kein Turnier aktiv.";
        reset();
        announce("<red>Das Turnier wurde abgebrochen.");
        return null;
    }

    /** Wird nach dem Ende jedes Duells aufgerufen. Ignoriert alles, was kein Turnier-Match war. */
    public synchronized void onDuelEnd(UUID winner, UUID loser) {
        if (state != State.RUNNING) return;
        if (!loser.equals(matchOf.get(winner))) return;
        matchOf.remove(winner);
        matchOf.remove(loser);
        nextRound.add(winner);

        int perWin = plugin.getConfig().getInt("tournament.trophies-per-win", 50);
        plugin.stats().get(winner).trophies += perWin;
        Player w = Bukkit.getPlayer(winner);
        if (w != null) Msg.send(w, "<green>Du kommst weiter! <gray>+" + perWin + " Trophäen");

        pending--;
        if (pending <= 0) advance();
    }

    public synchronized void onQuit(UUID id) {
        if (state == State.REGISTRATION) entrants.remove(id);
    }

    private void startRound() {
        nextRound.clear();
        matchOf.clear();
        pending = 0;
        announce("<gold>Runde " + round + " <gray>mit " + alive.size() + " Spielern");

        List<UUID> list = new ArrayList<>(alive);
        UUID bye = list.size() % 2 == 1 ? list.remove(list.size() - 1) : null;
        for (int i = 0; i < list.size(); i += 2) {
            UUID a = list.get(i);
            UUID b = list.get(i + 1);
            Player pa = Bukkit.getPlayer(a);
            Player pb = Bukkit.getPlayer(b);
            if (pa == null && pb == null) continue;
            if (pa == null) { nextRound.add(b); continue; }
            if (pb == null) { nextRound.add(a); continue; }

            String err = plugin.pvp().startTournamentMatch(pa, pb);
            if (err != null) {
                nextRound.add(a);
                Msg.send(pa, "<red>Match konnte nicht starten: " + err);
                Msg.send(pb, "<red>Match konnte nicht starten: " + err);
                continue;
            }
            matchOf.put(a, b);
            matchOf.put(b, a);
            pending++;
        }
        if (bye != null) {
            nextRound.add(bye);
            Player pb = Bukkit.getPlayer(bye);
            if (pb != null) Msg.send(pb, "<gold>Freilos: du kommst direkt weiter!");
        }
        if (pending == 0) advance();
    }

    private void advance() {
        alive.clear();
        alive.addAll(nextRound);
        if (alive.isEmpty()) {
            finish(null);
        } else if (alive.size() == 1) {
            finish(alive.get(0));
        } else {
            round++;
            startRound();
        }
    }

    private void finish(UUID champion) {
        if (champion != null) {
            long reward = plugin.getConfig().getLong("tournament.reward", 5000);
            int bonus = plugin.getConfig().getInt("tournament.trophies-champion", 200);
            plugin.economy().deposit(champion, reward);
            plugin.stats().get(champion).trophies += bonus;
            String name = Bukkit.getOfflinePlayer(champion).getName();
            announce("<gold>★ Turniersieger: <white>" + (name == null ? "Unbekannt" : name)
                    + " <gray>gewinnt <gold>" + Msg.coins(reward) + "$ <gray>und <gold>" + bonus + " Trophäen<gray>!");
            Player p = Bukkit.getPlayer(champion);
            if (p != null) Sfx.play(plugin, p, Sfx.Kind.EPIC);
        } else {
            announce("<gray>Das Turnier endete ohne Sieger.");
        }
        reset();
    }

    private void reset() {
        state = State.CLOSED;
        entrants.clear();
        alive.clear();
        nextRound.clear();
        matchOf.clear();
        pending = 0;
        round = 0;
    }

    public void announce(String mini) {
        plugin.notifications().send("notify.duel", Msg.parse(mini));
    }
}
