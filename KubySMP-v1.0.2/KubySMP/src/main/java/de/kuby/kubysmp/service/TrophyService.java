package de.kuby.kubysmp.service;

import de.kuby.kubyplugins.service.League;
import de.kuby.kubyplugins.service.StatsService;

import de.kuby.kubyplugins.KubyPlugins;

import java.util.UUID;

/**
 * Trophäen bei Kills: Der Gewinner bekommt, was der Verlierer verliert (Nullsumme, mit 0 Trophäen gibt es nichts).
 * Betrag = Basis + Schritt * (Liga des Opfers - Liga des Killers), begrenzt auf min/max.
 */
public final class TrophyService {

    public record Result(int gained, int lost) {}

    private final KubyPlugins plugin;

    public TrophyService(KubyPlugins plugin) {
        this.plugin = plugin;
    }

    public synchronized Result transfer(UUID winner, UUID loser) {
        StatsService.Stats w = plugin.stats().get(winner);
        StatsService.Stats l = plugin.stats().get(loser);
        int winnerLeague = League.of(w.trophies).ordinal();
        int loserLeague = League.of(l.trophies).ordinal();

        int base = plugin.getConfig().getInt("trophies.base", 20);
        int step = plugin.getConfig().getInt("trophies.league-step", 5);
        int min = plugin.getConfig().getInt("trophies.min", 5);
        int max = plugin.getConfig().getInt("trophies.max", 60);
        int amount = Math.max(min, Math.min(max, base + step * (loserLeague - winnerLeague)));

        // Nullsumme: der Gewinner bekommt genau, was der Verlierer abgibt (früher bekam er immer mindestens trophies.min,
        // auch von einem Opfer mit 0 Trophäen: unendliches Farmen mit einem Zweitkonto)
        int lost = Math.min(amount, Math.max(0, l.trophies));
        l.trophies -= lost;
        w.trophies += lost;
        return new Result(lost, lost);
    }
}
