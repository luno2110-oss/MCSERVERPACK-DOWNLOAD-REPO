package de.kuby.kubyplugins.cinema;

/**
 * Ein sichtbares Teil einer Szene (wird als Anzeige-Entity nur für den Zuschauer gezeigt).
 *
 * @param id        bleibt über die Ticks gleich (daran erkennt die Engine dasselbe Teil wieder)
 * @param kind      ITEM: Itemmodell (what = Modell-Schlüssel, z. B. "kuby:fx/ring_2"); BLOCK: Blockdaten
 *                  ("minecraft:blackstone", "@ground" = Boden unter dem Anker); TEXT: MiniMessage-Text
 * @param pos       Position der Entity
 * @param yaw       Ausrichtung der Entity (nur FIXED)
 * @param pitch     Ausrichtung der Entity (nur FIXED)
 * @param move      Versatz in der Transformation
 * @param rot       Drehung in der Transformation (Quaternion x, y, z, w)
 * @param scale     Größe
 * @param light     Helligkeit 0..15 (-1 = Welt-Licht)
 * @param billboard 0 fest, 1 zur Kamera, 2 nur senkrecht zur Kamera
 * @param color     ITEM: Färbung (dyed_color, 0 = keine); TEXT: Hintergrund ARGB
 * @param smooth    Ticks für die Bewegung zur neuen Lage (0 = springen)
 */
public record Prop(String id, Kind kind, String what, V3 pos, float yaw, float pitch, V3 move, double[] rot, V3 scale,
                   int light, int billboard, int color, int smooth) {

    public enum Kind { ITEM, BLOCK, TEXT }

    public static Prop item(String id, String model, V3 pos, double scale) {
        return new Prop(id, Kind.ITEM, model, pos, 0, 0, V3.ZERO, Ease.quatIdentity(), new V3(scale, scale, scale), 15, 0, 0, 3);
    }

    public static Prop block(String id, String data, V3 pos, double scale) {
        // Blockmodell geht von 0 bis 1: um die Mitte zentrieren
        return new Prop(id, Kind.BLOCK, data, pos, 0, 0, new V3(-scale / 2, -scale / 2, -scale / 2), Ease.quatIdentity(),
                new V3(scale, scale, scale), -1, 0, 0, 3);
    }

    public static Prop text(String id, String text, V3 pos, double scale, int bg) {
        return new Prop(id, Kind.TEXT, text, pos, 0, 0, V3.ZERO, Ease.quatIdentity(), new V3(scale, scale, scale), 15, 1, bg, 3);
    }

    public Prop at(V3 p) {
        return new Prop(id, kind, what, p, yaw, pitch, move, rot, scale, light, billboard, color, smooth);
    }

    public Prop facing(float yaw, float pitch) {
        return new Prop(id, kind, what, pos, yaw, pitch, move, rot, scale, light, billboard, color, smooth);
    }

    public Prop moved(V3 m) {
        return new Prop(id, kind, what, pos, yaw, pitch, m, rot, scale, light, billboard, color, smooth);
    }

    public Prop rotated(double[] q) {
        return new Prop(id, kind, what, pos, yaw, pitch, move, q, scale, light, billboard, color, smooth);
    }

    public Prop scaled(V3 s) {
        return new Prop(id, kind, what, pos, yaw, pitch, move, rot, s, light, billboard, color, smooth);
    }

    public Prop scaled(double s) {
        return scaled(new V3(s, s, s));
    }

    public Prop lit(int l) {
        return new Prop(id, kind, what, pos, yaw, pitch, move, rot, scale, l, billboard, color, smooth);
    }

    public Prop board(int b) {
        return new Prop(id, kind, what, pos, yaw, pitch, move, rot, scale, light, b, color, smooth);
    }

    public Prop tint(int c) {
        return new Prop(id, kind, what, pos, yaw, pitch, move, rot, scale, light, billboard, c, smooth);
    }

    public Prop snap(int ticks) {
        return new Prop(id, kind, what, pos, yaw, pitch, move, rot, scale, light, billboard, color, ticks);
    }

    public Prop model(String w) {
        return new Prop(id, kind, w, pos, yaw, pitch, move, rot, scale, light, billboard, color, smooth);
    }
}
