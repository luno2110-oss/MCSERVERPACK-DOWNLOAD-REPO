package de.kuby.kubyplugins.casino;

import org.bukkit.Location;

import java.util.List;
import java.util.Locale;

/**
 * Aufgestellte Casino-Teile für den Casino-Admin (v2.07, Wunsch des Besitzers: „die verschiedenen farbigen Tische holen,
 * individuell einstellen und auch verschieben mit Knöpfen x, y, z“). Jeder Dienst (Blackjack, Roulette, Poker, Slots,
 * Plinko, Rennen, Crash, Glücksrad, Figuren, Hologramme) listet seine Teile und kann sie verschieben, drehen, in der
 * Größe ändern und entfernen. Rückgabe null = erledigt, sonst der Grund (z. B. „am Tisch läuft eine Runde“).
 */
public interface Placeable {

    /**
     * Ein aufgestelltes Teil.
     *
     * @param id    Schlüssel im Dienst
     * @param name  Anzeige („Blackjack-Tisch 2 · Rot“)
     * @param at    Bezugspunkt (Boden-Mitte, Bildschirm-Mitte …)
     * @param yaw   Blickrichtung
     * @param size  Größe (Radius, Breite, Maßstab) oder NaN, wenn nicht änderbar
     * @param info  kurzer Zustand („frei“, „Runde läuft“ …)
     * @param busy  true, solange es gerade nicht bewegt werden darf
     */
    record Piece(String id, String name, Location at, float yaw, double size, String info, boolean busy) {}

    List<Piece> pieces();

    /** Um dx, dy, dz verschieben und um dyaw Grad drehen. */
    String move(String id, double dx, double dy, double dz, float dyaw);

    /** Größe um delta ändern (Grenzen des Teils); Standard: geht nicht. */
    default String resize(String id, double delta) {
        return "Die Größe lässt sich hier nicht ändern.";
    }

    /** Ganz entfernen (auch aus der Datei). */
    String remove(String id);

    /** Schrittweite der Größe (für die Knöpfe), 0 = keine. */
    default double sizeStep() {
        return 0;
    }

    /**
     * Gespeicherte Aufstellung „welt;x;y;z;yaw;…“ verschieben und drehen. extra = Startindizes weiterer
     * Koordinaten-Tripel im selben Text (z. B. der Hocker eines Slot-Automaten), die mitwandern.
     */
    static String shift(String raw, double dx, double dy, double dz, float dyaw, int... extra) {
        if (raw == null) return null;
        String[] p = raw.split(";", -1);
        if (p.length < 4) return raw;
        try {
            p[1] = num(Double.parseDouble(p[1]) + dx);
            p[2] = num(Double.parseDouble(p[2]) + dy);
            p[3] = num(Double.parseDouble(p[3]) + dz);
            if (p.length > 4 && dyaw != 0) p[4] = num(norm(Float.parseFloat(p[4]) + dyaw));
            for (int i : extra) {
                if (i + 2 >= p.length) continue;
                p[i] = num(Double.parseDouble(p[i]) + dx);
                p[i + 1] = num(Double.parseDouble(p[i + 1]) + dy);
                p[i + 2] = num(Double.parseDouble(p[i + 2]) + dz);
            }
        } catch (NumberFormatException e) {
            return raw;
        }
        return String.join(";", p);
    }

    /** Feld index (z. B. 5 = Größe) auf einen neuen Zahlenwert setzen. */
    static String field(String raw, int index, double value) {
        if (raw == null) return null;
        String[] p = raw.split(";", -1);
        if (index >= p.length) return raw;
        p[index] = num(value);
        return String.join(";", p);
    }

    static double fieldValue(String raw, int index, double def) {
        if (raw == null) return def;
        String[] p = raw.split(";", -1);
        if (index >= p.length) return def;
        try {
            return Double.parseDouble(p[index]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Winkel in -180..180. */
    static float norm(float yaw) {
        float y = yaw % 360f;
        if (y > 180f) y -= 360f;
        if (y <= -180f) y += 360f;
        return y;
    }

    /** Zahl ohne unnötige Nachkommastellen, Punkt als Trenner (wie beim Speichern üblich). */
    static String num(double v) {
        double r = Math.round(v * 10000.0) / 10000.0;
        if (r == Math.rint(r)) return String.format(Locale.ROOT, "%.1f", r);
        return String.format(Locale.ROOT, "%s", r);
    }

    /** Koordinaten kurz: „12,5 64 -3“. */
    static String where(Location l) {
        if (l == null) return "–";
        return short1(l.getX()) + " " + short1(l.getY()) + " " + short1(l.getZ());
    }

    static String short1(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.GERMANY, "%.1f", r);
    }
}
