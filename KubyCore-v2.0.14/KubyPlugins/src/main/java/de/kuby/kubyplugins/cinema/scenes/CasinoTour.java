package de.kuby.kubyplugins.cinema.scenes;

import de.kuby.kubyplugins.cinema.Ease;
import de.kuby.kubyplugins.cinema.Ev;
import de.kuby.kubyplugins.cinema.Frame;
import de.kuby.kubyplugins.cinema.Rig;
import de.kuby.kubyplugins.cinema.Script;
import de.kuby.kubyplugins.cinema.V3;

import java.util.List;

/**
 * Casino-Rundgang beim ersten Betreten (wie die Führung durchs Diamond Casino): Gastgeber Vincent (Gelenkfigur im
 * Smoking) führt den Spieler zu jeder Station, die im Casino wirklich steht. Mitten im Satz schneidet die Kamera auf das
 * Geschehen dort (Dealer, Walzen, Rad, Bildschirm) oder auf echte Spieler, die gerade spielen.
 *
 * <ol>
 *   <li>Kranfahrt über das Casino, Titelkarte „KUBY CASINO“, Vincent begrüßt den Spieler am Eingang</li>
 *   <li>je Station: Vincent und der Spieler gehen hin, Vincent zeigt mit der Hand darauf und erklärt (Untertitel),
 *       Schnitt auf die Szene (oder einen Spieler) während er weiterspricht, zurück zu den beiden</li>
 *   <li>Abschluss am Eingang: Verbeugung, Tipp zur Goldenen Zeit, „Viel Glück“</li>
 * </ol>
 * Jederzeit mit Schleichen überspringbar (ab Tick 30).
 */
public final class CasinoTour implements Script {

    /**
     * Eine Station: focus = Mitte des Geschehens, front = waagerechte Richtung von dort zum Betrachter, dist = Abstand
     * der Standpunkte, broll = Kamera und Ziel für den Schnitt (z. B. auf einen Spieler), lines = zwei Sätze.
     */
    public record Station(String name, V3 focus, V3 front, double dist, V3 brollCam, V3 brollLook, String[] lines, V3 twoCam) {
        public Station(String name, V3 focus, V3 front, double dist, V3 brollCam, V3 brollLook, String[] lines) {
            this(name, focus, front, dist, brollCam, brollLook, lines, null);
        }
    }

    /** Höchstlänge eines Untertitels (sichtbare Zeichen): längere Sätze laufen sonst über den Bildrand. */
    static final int SUB_MAX = 46;

    /** Teilt Sätze an Wortgrenzen in Untertitel von höchstens SUB_MAX Zeichen. */
    static java.util.List<String> chunks(String... lines) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String line : lines) {
            StringBuilder cur = new StringBuilder();
            for (String w : line.split(" ")) {
                if (cur.length() > 0 && cur.length() + 1 + w.length() > SUB_MAX) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                if (cur.length() > 0) cur.append(' ');
                cur.append(w);
            }
            if (cur.length() > 0) out.add(cur.toString());
        }
        return out;
    }

    /** Untertitel für Tick u im Fenster [from, to): die Häppchen nach ihrer Länge über das Fenster verteilt. */
    static String subAt(int u, int from, int to, java.util.List<String> parts) {
        if (u < from || u >= to || parts.isEmpty()) return null;
        int total = 0;
        for (String s : parts) total += s.length() + 8;
        double at = (u - from) / (double) (to - from) * total;
        for (String s : parts) {
            at -= s.length() + 8;
            if (at < 0) return s;
        }
        return parts.get(parts.size() - 1);
    }

    private static final String WHO = "<#F2C94C>Vincent: <white>";

    static final int INTRO = 150, PER = 160, OUTRO = 110;
    private final V3 start, startDir;
    private final List<Station> stations;
    private final V3 overview;

    /**
     * @param start    wo der Spieler steht (Füße)
     * @param startDir Blickrichtung des Spielers (waagerecht)
     * @param stations Stationen in Reihenfolge
     */
    public CasinoTour(V3 start, V3 startDir, List<Station> stations) {
        this.start = start;
        this.startDir = startDir.flat().len() < 0.1 ? new V3(0, 0, 1) : startDir.flat();
        this.stations = stations;
        V3 sum = start;
        for (Station s : stations) sum = sum.add(s.focus());
        this.overview = sum.mul(1.0 / (stations.size() + 1));
    }

    @Override
    public int length() {
        return INTRO + PER * stations.size() + OUTRO;
    }

    @Override
    public int skippableFrom() {
        return 30;
    }

    private static double seg(int t, int a, int b) {
        return Ease.clamp01((t - a) / (double) (b - a));
    }

    /** Pose: Arm zeigt in Richtung target (vom Körper aus), Kopf schaut mit. */
    private static float[] point(float[] base, V3 from, double yaw, V3 target, double amount) {
        double want = V3.yawOf(target.sub(from));
        double rel = want - yaw;
        while (rel > 180) rel -= 360;
        while (rel < -180) rel += 360;
        float r = (float) Math.max(-80, Math.min(80, -rel));
        float a = (float) amount;
        return Rig.set(base, Rig.RSX, -5 - 70 * a, Rig.RSY, r * a, Rig.REX, -12 + 4 * a, Rig.HY, r * 0.5f * a);
    }

    @Override
    public void frame(int t, Frame fr) {
        V3 right = startDir.right();
        if (t < INTRO) {
            intro(t, fr, right);
        } else if (t < INTRO + PER * stations.size()) {
            int i = (t - INTRO) / PER;
            station(t - INTRO - i * PER, i, fr);
        } else {
            outro(t - INTRO - PER * stations.size(), fr, right);
        }
        fr.bars = t < 10 ? Ease.smooth(t / 10.0) : t > length() - 12 ? Ease.smooth((length() - t) / 12.0) : 1;
        if (fr.grade == null) fr.grade = "soft";
    }

    private void intro(int t, Frame fr, V3 right) {
        // Vincent kommt auf den Spieler zu und begrüßt ihn
        V3 hostEnd = start.add(startDir.mul(1.6));
        V3 hostStart = hostEnd.add(startDir.mul(2.2)).add(right.mul(0.6));
        double w = seg(t, 60, 100);
        V3 host = hostStart.lerp(hostEnd, Ease.inOutSine(w));
        double hyaw = V3.yawOf(start.sub(host));
        float[] hp = w > 0 && w < 1 ? Rig.walk(t / 18.0) : Rig.stand();
        if (t >= 104 && t < 130) {
            double b = Math.sin(seg(t, 104, 130) * Math.PI);
            hp = Rig.set(hp, Rig.TP, (float) (18 * b), Rig.HP, (float) (10 * b), Rig.RSX, -40, Rig.RSZ, 30, Rig.REX, -88);
        }
        Rig.props(fr, "host", "dealer", host, hyaw, hp, 2);
        fr.actor = start;
        fr.actorYaw = V3.yawOf(startDir);
        fr.headYaw = fr.actorYaw;
        if (t < 60) {
            // Kranfahrt über das Casino, senkt sich zum Eingang
            double u = Ease.inOutSine(t / 60.0);
            V3 high = overview.up(9).add(startDir.mul(-6));
            V3 low = start.up(2.6).add(startDir.mul(-3.2)).add(right.mul(1.4));
            fr.cam = high.lerp(low, u);
            fr.look(overview.lerp(start.add(startDir.mul(2)), u).up(1));
            fr.card = t > 8 && t < 56 ? "KUBY CASINO" : null;
        } else {
            fr.cam = start.up(1.7).add(startDir.mul(-1.4)).add(right.mul(0.9));
            fr.look(host.up(1.45));
            fr.cut = t == 60;
            String sub = subAt(t, 96, INTRO, chunks("Willkommen im Kuby Casino!", "Ich bin Vincent – darf ich Sie herumführen?"));
            if (sub != null) fr.cardSub = WHO + sub;
        }
        if (t == 0) fr.ev(new Ev.Sound("minecraft:block.note_block.chime", 0.8f, 1.0f));
        // leise Lounge-Musik unter dem ganzen Rundgang (endet mit der Szene)
        if (t == 8) fr.ev(new Ev.Sound("minecraft:music_disc.cat", 0.22f, 1.0f));
        if (t == 60) fr.ev(new Ev.Sound("minecraft:block.note_block.bell", 0.6f, 1.2f));
        if (w > 0 && w < 1 && t % 7 == 0) fr.ev(new Ev.Sound("minecraft:block.wood.step", 0.3f, 1.0f));
    }

    private void station(int u, int i, Frame fr) {
        Station s = stations.get(i);
        V3 f = s.front().flat();
        V3 r = f.right();
        V3 spot = s.focus().add(f.mul(s.dist()));
        V3 hostSpot = spot.add(r.mul(0.9));
        V3 playerSpot = spot.add(r.mul(-0.4));
        // ankommen: beide gehen die letzten Schritte heran
        double w = seg(u, 0, 34);
        V3 back = f.mul(2.4);
        V3 host = hostSpot.add(back).lerp(hostSpot, Ease.inOutSine(w));
        V3 me = playerSpot.add(back).lerp(playerSpot, Ease.inOutSine(seg(u, 4, 38)));
        boolean walking = w > 0 && w < 1;
        double faceStation = V3.yawOf(s.focus().sub(host));
        double faceMe = V3.yawOf(me.sub(host));
        double hyaw = u < 40 ? faceStation : faceMe + 25;
        float[] hp = walking ? Rig.walk(u / 18.0) : Rig.stand();
        if (u >= 40 && u < 120) {
            double a = Math.sin(Math.min(1, seg(u, 40, 58)) * Math.PI / 2) * (u > 100 ? 1 - seg(u, 100, 120) : 1);
            hp = point(hp, host, hyaw, s.focus(), a);
        }
        Rig.props(fr, "host", "dealer", host.up(walking ? Rig.bob(u / 18.0) : 0), hyaw, hp, 2);
        fr.actor = me;
        fr.actorYaw = V3.yawOf(s.focus().sub(me));
        fr.headYaw = u > 40 && u < 70 ? (float) faceMe : fr.actorYaw;
        // Kamera
        if (u < 66 || u >= 124) {
            // Zweier: schräg von vorn auf Vincent und den Spieler, die Station im Hintergrund (Stelle vorab in der Welt
            // geprüft: nicht in einer Wand)
            V3 cam = s.twoCam() != null ? s.twoCam() : spot.add(f.mul(2.6)).add(r.mul(2.2)).up(1.75);
            fr.cam = cam;
            fr.look(host.lerp(me, 0.4).up(1.45).lerp(s.focus().up(1.0), 0.25));
            fr.cut = u == 0 || u == 124;
        } else {
            // Schnitt auf das Geschehen (Dealer, Walzen, Rad, Bildschirm) oder einen Spieler, Vincent spricht weiter
            double d = seg(u, 66, 124);
            fr.cam = s.brollCam().lerp(s.brollCam().lerp(s.brollLook(), 0.18), Ease.inOutSine(d));
            fr.look(s.brollLook());
            fr.cut = u == 66;
        }
        // Text
        String sub = subAt(u, 38, 154, chunks(s.lines()));
        if (sub != null) fr.cardSub = WHO + sub;
        if (u == 4 && s.name() != null) fr.ev(new Ev.Sound("minecraft:block.note_block.pling", 0.4f, 1.5f));
        if (walking && u % 7 == 0) fr.ev(new Ev.Sound("minecraft:block.wood.step", 0.3f, 1.0f));
        if (u >= 40 && u < 100) fr.card = null;
    }

    private void outro(int u, Frame fr, V3 right) {
        V3 host = start.add(startDir.mul(1.6));
        double hyaw = V3.yawOf(start.sub(host));
        float[] hp = Rig.stand();
        if (u >= 50 && u < 80) {
            double b = Math.sin(seg(u, 50, 80) * Math.PI);
            hp = Rig.set(hp, Rig.TP, (float) (22 * b), Rig.HP, (float) (12 * b), Rig.RSX, -40, Rig.RSZ, 30, Rig.REX, -88);
        }
        Rig.props(fr, "host", "dealer", host, hyaw, hp, 2);
        fr.actor = start;
        fr.actorYaw = V3.yawOf(startDir);
        fr.headYaw = fr.actorYaw;
        double d = Ease.outCubic(seg(u, 0, OUTRO));
        fr.cam = start.up(1.6).add(startDir.mul(-1.3)).add(right.mul(1.2)).lerp(start.up(2.4).add(startDir.mul(-2.6)).add(right.mul(2.0)), d);
        fr.look(host.up(1.3));
        fr.cut = u == 0;
        if (u < 50) {
            String sub = subAt(u, 0, 50, chunks("Kleiner Tipp: Von 17 bis 22 Uhr ist Goldene Zeit –", "da zählen Gewinne doppelt."));
            if (sub != null) fr.cardSub = WHO + sub;
        } else if (u < 96) {
            fr.card = "VIEL ERFOLG";
            fr.cardSub = "<gray>Rundgang wiederholen: <white>/casino rundgang";
        }
        if (u == 52) fr.ev(new Ev.Sound("minecraft:ui.toast.challenge_complete", 0.6f, 1.3f));
        fr.grade = u > 50 ? "gold" : "soft";
    }
}
