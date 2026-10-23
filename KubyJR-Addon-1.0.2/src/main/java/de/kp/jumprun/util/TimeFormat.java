package de.kp.jumprun.util;

/**
 * Formatiert Millisekunden als mm:ss.SSS, z. B. 01:23.456
 */
public final class TimeFormat {

    private TimeFormat() {
    }

    public static String format(long millis) {
        long minutes = millis / 60_000;
        long seconds = (millis / 1_000) % 60;
        long ms = millis % 1_000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
}
