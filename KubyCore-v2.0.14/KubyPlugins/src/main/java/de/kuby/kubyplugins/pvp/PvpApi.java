package de.kuby.kubyplugins.pvp;

import java.util.UUID;

/**
 * Schnittstelle für PvP-Zustände. Der Kern fragt nur diese Schnittstelle ab, nicht die PvP-Klassen.
 * Beim Auslagern wird sie von KubySMP umgesetzt und beim Kern registriert.
 */
public interface PvpApi {

    /** Im Kampf (Combat-Tag). */
    boolean inCombat(UUID id);

    /** In einem Duell. */
    boolean isDueling(UUID id);

    /** Setzt den Kampf-Tag (z. B. bei Duo-RTP). */
    void tagCombat(UUID id);

    /** Startet ein Turnier-Duell zwischen zwei Spielern. Gibt null bei Erfolg zurück, sonst eine Meldung. */
    String startTournamentMatch(org.bukkit.entity.Player a, org.bukkit.entity.Player b);

    /** Kopfgeld eines Spielers auszahlen (Admin). Gibt den Betrag zurück, 0 ohne Kopfgeld. */
    long claimBounty(UUID id);

    /** Ohne KubySMP: keine PvP-Zustände, nichts wird ausgelöst. */
    PvpApi NONE = new PvpApi() {
        @Override public boolean inCombat(UUID id) { return false; }
        @Override public boolean isDueling(UUID id) { return false; }
        @Override public void tagCombat(UUID id) { }
        @Override public String startTournamentMatch(org.bukkit.entity.Player a, org.bukkit.entity.Player b) { return "PvP ist nicht verfügbar (KubySMP fehlt)."; }
        @Override public long claimBounty(UUID id) { return 0; }
    };
}
