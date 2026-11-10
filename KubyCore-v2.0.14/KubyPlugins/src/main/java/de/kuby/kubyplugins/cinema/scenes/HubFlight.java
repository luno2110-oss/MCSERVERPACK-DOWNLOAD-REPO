package de.kuby.kubyplugins.cinema.scenes;

import de.kuby.kubyplugins.cinema.Ease;
import de.kuby.kubyplugins.cinema.Ev;
import de.kuby.kubyplugins.cinema.Frame;
import de.kuby.kubyplugins.cinema.Script;
import de.kuby.kubyplugins.cinema.V3;

import java.util.List;

/**
 * Willkommens-Flug über den Hub (Einführung beim ersten Join, mit Schleichen überspringbar).
 * <p>
 * Ohne Wegpunkte kreist die Kamera hoch über dem Spawn los und senkt sich in einer weiten Spirale bis kurz vor den
 * Spieler (gut für jeden Spawn). Mit Wegpunkten (vom Admin gesetzt: Ort und Blickrichtung) fliegt sie weich durch alle
 * Punkte (Catmull-Rom, 3 Sekunden je Abschnitt) und endet ebenfalls beim Spieler. Dazu Kino-Balken und die Titelkarte
 * „WILLKOMMEN“.
 */
public final class HubFlight implements Script {

    /** Wegpunkt: Ort der Kamera und Blickrichtung. */
    public record Point(V3 pos, float yaw, float pitch) {}

    static final int ORBIT_LEN = 200, SEG = 60, CARD_FROM = 28, CARD_TO = 118;

    private final V3 spawn;
    private final float spawnYaw;
    private final List<Point> path;
    private final String server;
    private final int len;

    /**
     * @param spawn    Füße am Hub-Spawn (dort endet die Fahrt)
     * @param spawnYaw Blickrichtung am Spawn
     * @param path     Wegpunkte (weniger als 2 = Rundflug)
     * @param server   Name für die Titelkarte
     */
    public HubFlight(V3 spawn, float spawnYaw, List<Point> path, String server) {
        this.spawn = spawn;
        this.spawnYaw = spawnYaw;
        this.path = path == null || path.size() < 2 ? List.of() : List.copyOf(path);
        this.server = server;
        this.len = this.path.isEmpty() ? ORBIT_LEN : Math.max(120, Math.min(480, (this.path.size()) * SEG + 40));
    }

    @Override
    public int length() {
        return len;
    }

    @Override
    public int skippableFrom() {
        return 10;
    }

    /** Ende der Fahrt: knapp hinter und über dem Spieler, Blick in seine Richtung. */
    private Point endPoint() {
        V3 fwd = V3.dir(spawnYaw, 0);
        V3 pos = spawn.add(fwd.mul(-2.6)).up(2.3);
        V3 target = spawn.add(fwd.mul(6)).up(1.2);
        return new Point(pos, pos.yawTo(target), pos.pitchTo(target));
    }

    @Override
    public void frame(int t, Frame f) {
        double u = t / (double) (len - 1);
        if (path.isEmpty()) orbit(u, f);
        else spline(t, f);
        f.bars = Math.min(Ease.smooth(t / 12.0), Ease.smooth((len - 1 - t) / 12.0));
        if (t >= CARD_FROM && t < CARD_TO) {
            f.card = "WILLKOMMEN";
            f.cardSub = "<gray>auf <gold><b>" + server + "</b></gold> <dark_gray>· <gray>Schleichen zum Überspringen";
        }
        if (t == 0) f.ev(new Ev.Sound("minecraft:block.beacon.activate", 0.5f, 1.4f));
        if (t == CARD_FROM) f.ev(new Ev.Sound("minecraft:ui.toast.challenge_complete", 0.45f, 1.2f));
        if (t == len - 14) f.ev(new Ev.Sound("minecraft:entity.player.levelup", 0.35f, 1.6f));
    }

    /** Spirale: hoch und weit hinter dem Spawn los, 280 Grad herum und tiefer, zum Schluss weich zum Endpunkt. */
    private void orbit(double u, Frame f) {
        double e = Ease.inOutSine(u);
        double start = spawnYaw + 180;
        double ang = Math.toRadians(start + 280 * e);
        double r = Ease.lerp(30, 7, Ease.smooth(u));
        double h = Ease.lerp(22, 3.5, Ease.smoother(u));
        V3 pos = spawn.add(-Math.sin(ang) * r, h, Math.cos(ang) * r);
        V3 look = spawn.up(Ease.lerp(0.5, 1.6, u));
        // die letzten 20 % gleiten in den Endpunkt hinter dem Spieler
        double k = Ease.smooth(Ease.seg(u, 0.8, 1.0));
        Point end = endPoint();
        f.cam = pos.lerp(end.pos(), k);
        float yaw = f.cam.yawTo(look), pitch = f.cam.pitchTo(look);
        f.camYaw = lerpAngle(yaw, end.yaw(), k);
        f.camPitch = (float) Ease.lerp(pitch, end.pitch(), k);
    }

    /** Weich durch alle Wegpunkte, danach zum Endpunkt hinter dem Spieler. */
    private void spline(int t, Frame f) {
        int n = path.size();
        Point last = endPoint();
        // Segmente: Wegpunkte 0..n-1, dann Endpunkt
        double s = Math.min(n, t / (double) SEG);
        int i = (int) Math.floor(s);
        double w = Ease.inOutSine(s - i);
        Point a = pt(i, last), b = pt(i + 1, last);
        V3 p0 = pt(i - 1, last).pos(), p3 = pt(i + 2, last).pos();
        f.cam = catmull(p0, a.pos(), b.pos(), p3, s - i);
        f.camYaw = lerpAngle(a.yaw(), b.yaw(), w);
        f.camPitch = (float) Ease.lerp(a.pitch(), b.pitch(), w);
    }

    private Point pt(int i, Point last) {
        if (i < 0) return path.get(0);
        if (i < path.size()) return path.get(i);
        return last;
    }

    static V3 catmull(V3 p0, V3 p1, V3 p2, V3 p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return p1.mul(2).add(p2.sub(p0).mul(t)).add(p0.mul(2).sub(p1.mul(5)).add(p2.mul(4)).sub(p3).mul(t2))
                .add(p0.mul(-1).add(p1.mul(3)).sub(p2.mul(3)).add(p3).mul(t3)).mul(0.5);
    }

    static float lerpAngle(float a, float b, double t) {
        double d = ((b - a) % 360 + 540) % 360 - 180;
        return (float) (a + d * t);
    }
}
