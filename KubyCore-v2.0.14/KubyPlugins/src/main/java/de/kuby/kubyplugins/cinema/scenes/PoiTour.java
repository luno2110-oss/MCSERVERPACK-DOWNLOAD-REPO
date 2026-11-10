package de.kuby.kubyplugins.cinema.scenes;

import de.kuby.kubyplugins.cinema.Ease;
import de.kuby.kubyplugins.cinema.Ev;
import de.kuby.kubyplugins.cinema.Frame;
import de.kuby.kubyplugins.cinema.Script;
import de.kuby.kubyplugins.cinema.V3;

import java.util.ArrayList;
import java.util.List;

/**
 * Rundgang der Einführung: erst ein Überblick über den Spawn mit Titelkarte (z. B. „WILLKOMMEN“ oder „SMP“), dann fliegt
 * die Kamera nacheinander zu den wichtigen Orten (Kisten, RTP-Zone, Kristall-Tausch, NPCs …). An jedem Ort schwebt sie
 * gut 3 Sekunden mit leichter Drehung um den Ort, dazu Name und kurzer Text als Titelkarte. Zwischen den Orten fliegt sie
 * in einem Bogen nach oben (weniger Wände im Weg), zum Schluss zurück hinter den Spieler (oder blendet am letzten Ort aus,
 * wenn der Spieler gar nicht am Spawn steht). Mit Schleichen überspringbar. Ohne Bukkit (Vorschau möglich).
 */
public final class PoiTour implements Script {

    /** Ein Ort: Kameraposition, Blickziel, Titel und Untertitel (MiniMessage). */
    public record Stop(V3 view, V3 target, String title, String sub) {}

    static final int INTRO = 70, HOLD = 66, END_HOLD = 18;

    private record Seg(int from, int to, V3 a, V3 b, V3 lookA, V3 lookB, Stop stop, boolean travel) {}

    private final List<Seg> segs = new ArrayList<>();
    private final String card, cardSub;
    private final int len;

    /**
     * @param overview Kamera für den Überblick am Anfang
     * @param overLook Blickziel des Überblicks (meist der Spawn)
     * @param card     große Titelkarte am Anfang (z. B. „WILLKOMMEN“)
     * @param cardSub  Untertitel dazu
     * @param stops    die Orte in Reihenfolge
     * @param endView  Kamera am Ende (hinter dem Spieler) oder null (am letzten Ort ausblenden)
     * @param endLook  Blickziel am Ende
     */
    public PoiTour(V3 overview, V3 overLook, String card, String cardSub, List<Stop> stops, V3 endView, V3 endLook) {
        this.card = card;
        this.cardSub = cardSub;
        int t = 0;
        segs.add(new Seg(t, t + INTRO, overview, overview.add(overLook.sub(overview).norm().mul(1.5)), overLook, overLook, null, false));
        t += INTRO;
        V3 at = segs.get(0).b(), look = overLook;
        for (Stop s : stops) {
            int travel = travelTicks(at.dist(s.view()));
            segs.add(new Seg(t, t + travel, at, s.view(), look, s.target(), null, true));
            t += travel;
            segs.add(new Seg(t, t + HOLD, s.view(), s.view(), s.target(), s.target(), s, false));
            t += HOLD;
            at = s.view();
            look = s.target();
        }
        if (endView != null) {
            int travel = travelTicks(at.dist(endView));
            segs.add(new Seg(t, t + travel, at, endView, look, endLook, null, true));
            t += travel;
            segs.add(new Seg(t, t + END_HOLD, endView, endView, endLook, endLook, null, false));
            t += END_HOLD;
        } else {
            segs.add(new Seg(t, t + END_HOLD, at, at, look, look, null, false));
            t += END_HOLD;
        }
        this.len = t;
    }

    /** Flugzeit: etwa 12 Blöcke pro Sekunde, mindestens 1,3 und höchstens 3,5 Sekunden. */
    static int travelTicks(double dist) {
        return (int) Math.max(26, Math.min(70, dist * 1.65));
    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public int skippableFrom() {
        return 10;
    }

    @Override
    public void frame(int t, Frame f) {
        Seg s = segs.get(segs.size() - 1);
        for (Seg x : segs) {
            if (t < x.to()) {
                s = x;
                break;
            }
        }
        double u = Ease.seg(t, s.from(), s.to());
        V3 pos, look;
        if (s.travel()) {
            double e = Ease.inOutSine(u);
            double arc = Math.min(9, s.a().dist(s.b()) * 0.22);
            pos = s.a().lerp(s.b(), e).up(arc * Math.sin(Math.PI * e));
            look = s.lookA().lerp(s.lookB(), Ease.smoother(Ease.seg(u, 0.1, 0.9)));
        } else if (s.stop() != null) {
            // am Ort: langsam ein Stück um das Ziel drehen und etwas näher kommen
            V3 rel = s.a().sub(s.lookA());
            double ang = Ease.lerp(-9, 9, Ease.inOutSine(u));
            pos = s.lookA().add(rel.rotY(ang).mul(Ease.lerp(1.0, 0.86, Ease.smooth(u))));
            look = s.lookA();
        } else {
            pos = s.a().lerp(s.b(), Ease.inOutSine(u));
            look = s.lookA();
        }
        f.cam = pos;
        f.look(look);
        f.bars = Math.min(Ease.smooth(t / 12.0), Ease.smooth((len - 1 - t) / 12.0));
        // Titelkarten
        Seg first = segs.get(0);
        if (t >= 8 && t < first.to() - 6 && card != null) {
            f.card = card;
            f.cardSub = cardSub;
        } else if (s.stop() != null && t >= s.from() + 4 && t < s.to() - 6) {
            f.card = s.stop().title();
            f.cardSub = s.stop().sub();
        }
        // Klänge: Abflug und Ankunft
        if (s.travel() && t == s.from()) f.ev(new Ev.Sound("minecraft:entity.breeze.wind_burst", 0.18f, 1.5f));
        if (s.stop() != null && t == s.from()) f.ev(new Ev.Sound("minecraft:block.amethyst_block.chime", 0.8f, 1.2f));
        if (t == 0) f.ev(new Ev.Sound("minecraft:block.beacon.activate", 0.45f, 1.4f));
    }
}
