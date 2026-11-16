package de.kuby.kubyplugins.casino;

import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Tisch-Stufen für Blackjack und Poker (Farbe des Filzes, Pflichteinsatz, Höchsteinsatz).
 *
 * <ul>
 *   <li>Grün: Pflichteinsatz 10, Höchsteinsatz 100.000 Chips</li>
 *   <li>Rot (High Roller): Pflichteinsatz 10.000, Höchsteinsatz 500.000 Chips</li>
 *   <li>Violett (Elite): Pflichteinsatz 50.000, Höchsteinsatz 1.000.000 Chips</li>
 * </ul>
 * Einstellbar unter casino.tische.&lt;gruen|rot|violett&gt;.min-einsatz / max-einsatz. Beim Poker ist der Pflichteinsatz
 * der Big Blind (Small Blind die Hälfte) und der Höchsteinsatz das größte Buy-in.
 */
public enum TableTier {
    GRUEN("gruen", "Blackjack-Tisch", "", 10, 100_000, new long[]{10, 50, 100, 1_000, 5_000, 10_000}),
    ROT("rot", "High-Roller-Tisch", "_hr", 10_000, 500_000, new long[]{1_000, 5_000, 10_000, 25_000, 50_000, 100_000}),
    VIOLETT("violett", "Elite-Tisch", "_violett_hr", 50_000, 1_000_000, new long[]{5_000, 10_000, 50_000, 100_000, 250_000, 500_000});

    public final String key;
    /** Name des Blackjack-Tisches (ohne Nummer). */
    public final String tableName;
    /** Anhang an den Spielnamen für Abrechnung und Serie ("_hr" = High-Roller-Serie bis x8). */
    public final String suffix;
    private final long defMin, defMax;
    /** Chips im Ständer am Blackjack-Platz. */
    public final long[] rack;

    TableTier(String key, String tableName, String suffix, long defMin, long defMax, long[] rack) {
        this.key = key;
        this.tableName = tableName;
        this.suffix = suffix;
        this.defMin = defMin;
        this.defMax = defMax;
        this.rack = rack;
    }

    public long minBet(Plugin plugin) {
        return plugin.getConfig().getLong("casino.tische." + key + ".min-einsatz", defMin);
    }

    public long maxBet(Plugin plugin) {
        return plugin.getConfig().getLong("casino.tische." + key + ".max-einsatz", defMax);
    }

    /** Ab Rot gelten High-Roller-Regeln (Serie bis x8). */
    public boolean highRoller() {
        return this != GRUEN;
    }

    public String feltBlackjack() {
        return switch (this) {
            case GRUEN -> "bj_felt";
            case ROT -> "bj_felt_red";
            case VIOLETT -> "bj_felt_violet";
        };
    }

    public String feltPoker() {
        return switch (this) {
            case GRUEN -> "pk_felt";
            case ROT -> "pk_felt_red";
            case VIOLETT -> "pk_felt_violet";
        };
    }

    /** Überschrift am Pokertisch (MiniMessage). */
    public String pokerTitle() {
        return switch (this) {
            case GRUEN -> "<gold><b>POKER</b>";
            case ROT -> "<#E04A5A><b>HIGH ROLLER</b>";
            case VIOLETT -> "<#B57CFF><b>ELITE POKER</b>";
        };
    }

    /** Stufe aus einem Wort im Befehl oder in der Tischzeile (rot, weinrot, highroller, violett, lila, elite …). */
    public static TableTier parse(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "gruen", "grün", "green", "normal" -> GRUEN;
            case "rot", "weinrot", "red", "highroller", "high-roller" -> ROT;
            case "violett", "lila", "violet", "purple", "elite" -> VIOLETT;
            default -> null;
        };
    }
}
