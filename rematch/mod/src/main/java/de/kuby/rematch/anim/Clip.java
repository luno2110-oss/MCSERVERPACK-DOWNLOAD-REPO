package de.kuby.rematch.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Eine Animation (aus assets/kubyrematch/rematch/anim/*.json, erzeugt von tools/bbanim/export_anim.py). */
public final class Clip {
    public enum Loop { ONCE, LOOP, HOLD }

    /** Zusatzwerte: Priorität, Überblenden, ab wann abbrechbar, Tempo. */
    public record Meta(int priority, double blendIn, double blendOut, Easing easeIn, Easing easeOut,
                       double cancelAfter, double speed, double speedAt) {
        public static final Meta DEFAULT = new Meta(50, 0.12, 0.25, Easing.OUT_QUAD, Easing.IN_OUT_CUBIC, -1, 1.0, 0);
    }

    public record Event(double time, String name) { }

    static final int ROT = 0, POS = 1, SCL = 2;
    static final int LINEAR = 0, CATMULL = 1, STEP = 2;

    /** Keyframes eines Kanals: Zeiten, Werte, Art. */
    static final class Channel {
        final double[] t;
        final double[][] v;
        final int[] ip;

        Channel(double[] t, double[][] v, int[] ip) { this.t = t; this.v = v; this.ip = ip; }

        void sample(double time, double[] out) { sample(time, out, false); }

        void sample(double time, double[] out, boolean wrap) {
            int n = t.length;
            if (time <= t[0] || n == 1) { copy(v[0], out); return; }
            if (time >= t[n - 1]) { copy(v[n - 1], out); return; }
            int i = 0;
            while (i < n - 2 && time > t[i + 1]) i++;
            double a = (time - t[i]) / Math.max(1e-9, t[i + 1] - t[i]);
            double[] v0 = v[i], v1 = v[i + 1];
            if (ip[i] == STEP) { copy(v0, out); return; }
            if (ip[i] == CATMULL || ip[i + 1] == CATMULL) {
                // Schleife: erste und letzte Taste gleich -> Nachbarn über die Naht nehmen (keine Delle am Übergang)
                double[] vm = i > 0 ? v[i - 1] : (wrap && n >= 3 ? v[n - 2] : v0);
                double[] vp = i + 2 < n ? v[i + 2] : (wrap && n >= 3 ? v[1] : v1);
                double a2 = a * a, a3 = a2 * a;
                for (int k = 0; k < 3; k++)
                    out[k] = 0.5 * (2 * v0[k] + (-vm[k] + v1[k]) * a + (2 * vm[k] - 5 * v0[k] + 4 * v1[k] - vp[k]) * a2
                            + (-vm[k] + 3 * v0[k] - 3 * v1[k] + vp[k]) * a3);
                return;
            }
            for (int k = 0; k < 3; k++) out[k] = v0[k] + (v1[k] - v0[k]) * a;
        }

        private static void copy(double[] s, double[] d) { d[0] = s[0]; d[1] = s[1]; d[2] = s[2]; }
    }

    public final String name;
    public final double length;
    public final Loop loop;
    public final Meta meta;
    public final List<Event> events;
    /** [knochen][kanal] oder null */
    final Channel[][] channels;

    Clip(String name, double length, Loop loop, Meta meta, List<Event> events, Channel[][] channels) {
        this.name = name; this.length = length; this.loop = loop; this.meta = meta; this.events = events;
        this.channels = channels;
    }

    /** Schreibt die Werte zur Zeit t in die Pose (Knochen ohne Keyframes bleiben in Ruhe). */
    public void sample(double t, Pose out) {
        out.reset();
        for (int b = 0; b < channels.length; b++) {
            Channel[] c = channels[b];
            if (c == null) continue;
            boolean w = loop == Loop.LOOP;
            if (c[ROT] != null) c[ROT].sample(t, out.rot[b], w && seamless(c[ROT]));
            if (c[POS] != null) c[POS].sample(t, out.pos[b], w && seamless(c[POS]));
            if (c[SCL] != null) c[SCL].sample(t, out.scl[b], w && seamless(c[SCL]));
        }
    }

    private boolean seamless(Channel c) {
        int n = c.t.length;
        if (n < 3 || Math.abs(c.t[0]) > 1e-4 || Math.abs(c.t[n - 1] - length) > 1e-3) return false;
        for (int k = 0; k < 3; k++) if (Math.abs(c.v[0][k] - c.v[n - 1][k]) > 1e-3) return false;
        return true;
    }

    /** Ob der Clip Keyframes für diesen Knochen hat. */
    public boolean hasBone(int bone) {
        return bone >= 0 && bone < channels.length && channels[bone] != null;
    }

    /** Zeitpunkt der ersten Marke mit diesem Namen oder -1. */
    public double eventTime(String event) {
        for (Event e : events) if (e.name.equals(event)) return e.time;
        return -1;
    }

    public static Clip read(Reader r, Rig rig) {
        JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
        String name = o.get("name").getAsString();
        double length = o.get("length").getAsDouble();
        Loop loop = switch (o.has("loop") ? o.get("loop").getAsString() : "once") {
            case "loop" -> Loop.LOOP;
            case "hold" -> Loop.HOLD;
            default -> Loop.ONCE;
        };
        Meta meta = Meta.DEFAULT;
        if (o.has("meta")) {
            JsonObject m = o.getAsJsonObject("meta");
            Meta d = Meta.DEFAULT;
            meta = new Meta(num(m, "priority", d.priority()), num(m, "blend_in", d.blendIn()), num(m, "blend_out", d.blendOut()),
                    Easing.parse(str(m, "ease_in"), d.easeIn()), Easing.parse(str(m, "ease_out"), d.easeOut()),
                    num(m, "cancel_after", d.cancelAfter()), num(m, "speed", d.speed()), num(m, "speed_at", d.speedAt()));
        }
        List<Event> events = new ArrayList<>();
        if (o.has("events"))
            for (JsonElement e : o.getAsJsonArray("events")) {
                JsonArray a = e.getAsJsonArray();
                events.add(new Event(a.get(0).getAsDouble(), a.get(1).getAsString()));
            }
        events.sort((a, b) -> Double.compare(a.time, b.time));
        Channel[][] ch = new Channel[rig.size()][];
        for (Map.Entry<String, JsonElement> be : o.getAsJsonObject("bones").entrySet()) {
            int bi = rig.index(be.getKey());
            if (bi < 0) continue;
            ch[bi] = new Channel[3];
            JsonObject bo = be.getValue().getAsJsonObject();
            String[] keys = {"rotation", "position", "scale"};
            for (int c = 0; c < 3; c++) {
                if (!bo.has(keys[c])) continue;
                JsonArray ks = bo.getAsJsonArray(keys[c]);
                int n = ks.size();
                if (n == 0) continue;
                double[] t = new double[n];
                double[][] v = new double[n][3];
                int[] ip = new int[n];
                for (int i = 0; i < n; i++) {
                    JsonArray k = ks.get(i).getAsJsonArray();
                    t[i] = k.get(0).getAsDouble();
                    for (int x = 0; x < 3; x++) v[i][x] = k.get(1 + x).getAsDouble();
                    String s = k.size() > 4 ? k.get(4).getAsString() : "linear";
                    ip[i] = s.equals("catmullrom") ? CATMULL : s.equals("step") ? STEP : LINEAR;
                }
                ch[bi][c] = new Channel(t, v, ip);
            }
        }
        return new Clip(name, length, loop, meta, List.copyOf(events), ch);
    }

    private static double num(JsonObject o, String k, double d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : d;
    }

    private static int num(JsonObject o, String k, int d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : d;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
}
