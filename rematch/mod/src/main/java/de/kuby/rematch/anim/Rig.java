package de.kuby.rematch.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Knochen des Spieler-Gerüsts (aus rig.json, erzeugt von tools/bbanim/rig_spieler.py). Eltern stehen vor Kindern. */
public final class Rig {
    public final String[] names;
    public final int[] parent;
    public final double[][] pivot;
    private final Map<String, Integer> index = new HashMap<>();

    public Rig(List<String> names, List<String> parents, List<double[]> pivots) {
        int n = names.size();
        this.names = names.toArray(new String[0]);
        this.parent = new int[n];
        this.pivot = pivots.toArray(new double[0][]);
        for (int i = 0; i < n; i++) index.put(this.names[i], i);
        for (int i = 0; i < n; i++) {
            String p = parents.get(i);
            Integer pi = p == null ? null : index.get(p);
            if (p != null && (pi == null || pi >= i)) throw new IllegalArgumentException("Eltern von " + names.get(i) + " fehlen oder stehen dahinter: " + p);
            parent[i] = pi == null ? -1 : pi;
        }
    }

    public int size() { return names.length; }

    public int index(String name) {
        Integer i = index.get(name);
        if (i == null && "left_leg2".equals(name)) i = index.get("left_lag2"); // Tippfehler im Gerüst abfangen
        return i == null ? -1 : i;
    }

    public static Rig read(Reader r) {
        JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
        List<String> names = new ArrayList<>(), parents = new ArrayList<>();
        List<double[]> pivots = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("bones")) {
            JsonObject b = e.getAsJsonObject();
            names.add(b.get("name").getAsString());
            parents.add(b.get("parent").isJsonNull() ? null : b.get("parent").getAsString());
            JsonArray p = b.getAsJsonArray("pivot");
            pivots.add(new double[]{p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()});
        }
        return new Rig(names, parents, pivots);
    }

    /**
     * Gelenkkette: Weltmatrix je Knochen (Blockbench-Raum, bildet Ruhepunkte auf bewegte Punkte ab).
     * W = W_eltern * T(pivot + pos) * R * S * T(-pivot)
     */
    public Mat4[] solve(Pose p) {
        Mat4[] w = new Mat4[size()];
        for (int i = 0; i < size(); i++) {
            double[] pv = pivot[i];
            Mat4 local = Mat4.translation(pv[0] + p.pos[i][0], pv[1] + p.pos[i][1], pv[2] + p.pos[i][2])
                    .mul(Mat4.rotZYXdeg(p.rot[i][0], p.rot[i][1], p.rot[i][2]))
                    .mul(Mat4.scale(p.scl[i][0], p.scl[i][1], p.scl[i][2]))
                    .mul(Mat4.translation(-pv[0], -pv[1], -pv[2]));
            w[i] = parent[i] < 0 ? local : w[parent[i]].mul(local);
        }
        return w;
    }
}
