package de.kuby.kubyplugins.cinema;

/** Ereignisse eines Ticks (Ton, Partikel, Blitzbild, Himmel, Boden). Nur Daten, die Engine setzt sie um. */
public final class Ev {

    private Ev() {
    }

    /** Ton am Zuschauer (Stereo-Dateien klingen „im Kopf“). key z. B. "kuby:cine.impact" oder "minecraft:entity.warden.sonic_boom". */
    public record Sound(String key, float volume, float pitch) {}

    /** Ton an einer Stelle der Welt (mit Entfernung und Richtung). */
    public record SoundAt(String key, V3 pos, float volume, float pitch) {}

    /** Alle laufenden eigenen Töne stoppen (für die Stille vor dem Knall). */
    public record Silence() {}

    /**
     * Partikel; rgb &gt;= 0 bei DUST (Farbe), size = Größe. type = Bukkit-Name (FLASH, END_ROD, DUST, EXPLOSION_EMITTER …).
     */
    public record Particle(String type, V3 pos, int count, double sx, double sy, double sz, double speed, int rgb, float size) {
        public static Particle of(String type, V3 pos, int count, double spread, double speed) {
            return new Particle(type, pos, count, spread, spread, spread, speed, -1, 1);
        }

        public static Particle dust(V3 pos, int count, double spread, int rgb, float size) {
            return new Particle("DUST", pos, count, spread, spread, spread, 0, rgb, size);
        }
    }

    /** Blitzbild: Farbe über dem ganzen Bild (0xFFFFFF weiß, 0 schwarz), Ticks ein/halten/aus. */
    public record Flash(int rgb, int in, int stay, int out) {}

    /** Himmel nur für den Zuschauer: time &lt; 0 = lassen, weather -1 lassen, 0 klar, 1 Regen. */
    public record Sky(long time, int weather) {}

    /** Boden bricht auf (nur für den Zuschauer): Risse bis radius um center. */
    public record Crack(V3 center, double radius, long seed) {}

    /** Gefälschte Blöcke (Risse) wieder zurück. */
    public record Mend() {}

    /** Die geteilten Teile (z. B. der echte Stein) für den Zuschauer zeigen oder verstecken. */
    public record Shared(boolean visible) {}
}
