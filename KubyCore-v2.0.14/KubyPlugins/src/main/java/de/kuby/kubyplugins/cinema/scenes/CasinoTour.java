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

    /** Stationen mit Sprachaufnahme im Pack (casino/tour-voice.txt, von tools/add_tour_voice.py geschrieben). */
    public static final java.util.Set<String> VOICES = new java.util.HashSet<>();

    private static Ev.Sound voice(String id) {
        return VOICES.contains(id) ? new Ev.Sound("kuby:tour." + id, 1.0f, 1.0f) : null;
    }

    static final int INTRO = 210, PER = 260, OUTRO = 160;
    /** Zeitmaß der Stationen: alle Zeitpunkte unten sind für 160 Ticks geschrieben und werden auf PER gestreckt. */
    private static int st(int t160) {
        return (int) Math.round(t160 * PER / 160.0);
    }
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

    // =================================================================================================================
    // Körpersprache (v1.98): nichts dreht sich ruckartig, wer spricht, gestikuliert, alle atmen
    // =================================================================================================================

    /** Pose: Arm zeigt auf target (Richtung und Höhe), Kopf schaut mit. */
    private static float[] point(float[] base, V3 feet, double yaw, V3 target, double amount) {
        V3 shoulder = feet.up(1.4);
        double rel = Ease.lerpAngle(0, V3.yawOf(target.sub(shoulder)) - yaw, 1);
        float r = (float) Math.max(-80, Math.min(80, -rel));
        V3 d = target.sub(shoulder);
        double elev = Math.toDegrees(Math.atan2(d.y(), Math.max(0.3, Math.hypot(d.x(), d.z()))));
        float a = (float) amount;
        float rsx = (float) Math.max(-165, Math.min(-25, -88 - elev));
        return Rig.set(base, Rig.RSX, -5 + (rsx + 5) * a, Rig.RSY, r * a, Rig.REX, -12 + 6 * a, Rig.HY, r * 0.5f * a,
                Rig.HP, (float) (base[Rig.HP] + (-elev * 0.6 - base[Rig.HP]) * a));
    }

    /** Atmen (4 s) und langsames Verlagern des Gewichts – auch wer still steht, lebt. */
    private static float[] alive(float[] p, int t) {
        return Rig.set(p, Rig.TP, (float) (p[Rig.TP] + 1.3 * Math.sin(t * Math.PI * 2 / 80)),
                Rig.TR, (float) (p[Rig.TR] + 1.6 * Math.sin(t * Math.PI * 2 / 170)),
                Rig.HY, (float) (p[Rig.HY] + 2.5 * Math.sin(t * Math.PI * 2 / 230)));
    }

    /**
     * Sprechen: Kopf nickt leicht im Satzrhythmus, die freie linke Hand öffnet sich in Abständen zur Geste
     * (Handfläche nach außen), dazwischen ruht sie. amount 0..1 blendet ein und aus.
     */
    private static float[] talk(float[] p, int t, double amount) {
        if (amount <= 0) return p;
        double beat = (t % 46) / 46.0;
        double g = beat < 0.55 ? Math.sin(beat / 0.55 * Math.PI) : 0;
        double a = amount;
        return Rig.set(p,
                Rig.HP, (float) (p[Rig.HP] + a * 3.2 * Math.sin(t * 0.42)),
                Rig.HY, (float) (p[Rig.HY] + a * 4 * Math.sin(t * 0.11)),
                Rig.LSX, (float) (p[Rig.LSX] + a * g * -32),
                Rig.LSZ, (float) (p[Rig.LSZ] + a * g * 16),
                Rig.LEX, (float) (p[Rig.LEX] + a * g * -46),
                Rig.TY, (float) (p[Rig.TY] + a * g * 3));
    }

    /** Gehen weich ein- und ausblenden (erster und letzter Schritt halb), w = Fortschritt der Strecke 0..1. */
    private static float[] stride(int t, double w) {
        if (w <= 0 || w >= 1) return Rig.stand();
        double k = Math.min(1, Math.min(w, 1 - w) * 7);
        return Rig.mix(Rig.stand(), Rig.walk(t / 18.0), k);
    }

    private static double turn(double from, double to, double k) {
        return Ease.lerpAngle(from, to, Ease.smooth(Ease.clamp01(k)));
    }

    // =================================================================================================================
    // Wege und Übergänge
    // =================================================================================================================

    /** Ticks am Ende eines Abschnitts, in denen beide schon losgehen und die Kamera abfährt. */
    static final int TAIL = 26;

    private V3 hostRest(int i) {
        if (i < 0 || i >= stations.size()) return start.add(startDir.mul(1.6));
        Station s = stations.get(i);
        V3 f = s.front().flat();
        return s.focus().add(f.mul(s.dist())).add(f.right().mul(0.9));
    }

    private V3 meRest(int i) {
        if (i < 0 || i >= stations.size()) return start;
        Station s = stations.get(i);
        V3 f = s.front().flat();
        return s.focus().add(f.mul(s.dist())).add(f.right().mul(-0.4));
    }

    /** Kamera am Ende von Abschnitt i (-1 = Begrüßung). */
    private V3[] restCam(int i) {
        if (i >= 0) return twoShot(i);
        V3 right = startDir.right();
        return new V3[]{start.up(1.7).add(startDir.mul(-1.4)).add(right.mul(0.9)), hostRest(-1).up(1.45)};
    }

    /** Ziel der Fahrt zu Abschnitt i (i = Anzahl Stationen: Abschluss am Eingang). */
    private V3[] arriveCam(int i) {
        if (i < stations.size()) return twoShot(i);
        V3 right = startDir.right();
        return new V3[]{start.up(1.6).add(startDir.mul(-1.3)).add(right.mul(1.2)), hostRest(i).up(1.3)};
    }

    private int glide(int i) {
        return i < stations.size() ? st(42) : 40;
    }

    /**
     * Fahrt von Abschnitt i-1 zu i, G = 0..1 über die letzten TAIL Ticks davor und die ersten glide(i) Ticks danach.
     * Der Blick dreht schneller als die Kamera fährt: die beiden, die gerade losgehen, sind schon am Bildrand, wenn sie
     * (hoch oben in der Fahrt, für einen Moment unsichtbar) zur nächsten Station springen.
     */
    private void travel(Frame fr, int i, double G) {
        V3[] a = restCam(i - 1), b = arriveCam(i);
        double cg = Ease.inOutSine(G), lg = Ease.inOutSine(Math.min(1, G * 1.6));
        fr.cam = arc(a[0], b[0], cg);
        fr.look(a[1].lerp(b[1], lg));
    }

    private static boolean hiddenIn(double G) {
        return G > 0.25 && G < 0.4;
    }

    /** Losgehen am Ende von Abschnitt i: beide drehen sich zur nächsten Station und gehen 0,9 Blöcke. */
    private V3[] leave(int i, int k, V3 host, V3 me) {
        V3 dir = hostRest(i + 1).sub(hostRest(i)).flat();
        dir = dir.len() < 0.1 ? startDir : dir.norm();
        double w = Ease.smooth(k / (double) TAIL);
        return new V3[]{host.add(dir.mul(0.9 * w)), me.add(dir.mul(0.9 * Ease.smooth(Math.max(0, (k - 4) / (double) (TAIL - 4))))), dir};
    }

    @Override
    public void frame(int t, Frame fr) {
        if (t < INTRO) {
            intro(t, fr, startDir.right());
        } else if (t < INTRO + PER * stations.size()) {
            int i = (t - INTRO) / PER;
            station(t - INTRO - i * PER, i, fr);
        } else {
            outro(t - INTRO - PER * stations.size(), fr, startDir.right());
        }
        fr.cut = false;
        fr.bars = t < 10 ? Ease.smooth(t / 10.0) : t > length() - 12 ? Ease.smooth((length() - t) / 12.0) : 1;
        if (fr.grade == null) fr.grade = "soft";
    }

    private void intro(int t, Frame fr, V3 right) {
        // Vincent kommt auf den Spieler zu, verbeugt sich und begrüßt ihn (mit Gesten), dann geht es los
        V3 hostEnd = hostRest(-1);
        V3 hostStart = hostEnd.add(startDir.mul(2.2)).add(right.mul(0.6));
        double w = seg(t, 90, 140);
        V3 host = hostStart.lerp(hostEnd, Ease.inOutSine(w));
        double hyaw = V3.yawOf(start.sub(host));
        float[] hp = stride(t, w);
        if (t >= 146 && t < 176) {
            double b = Math.sin(seg(t, 146, 176) * Math.PI);
            hp = Rig.set(hp, Rig.TP, (float) (18 * b), Rig.HP, (float) (10 * b), Rig.RSX, -40, Rig.RSZ, 30, Rig.REX, -88);
        }
        hp = alive(hp, t);
        if (t >= 176) hp = talk(hp, t, seg(t, 176, 186) * (1 - seg(t, INTRO - TAIL, INTRO - TAIL + 6)));
        V3 me = start;
        double myaw = V3.yawOf(startDir);
        double headYaw = t > 120 ? turn(myaw, V3.yawOf(host.sub(me)), seg(t, 120, 132)) : myaw;
        boolean hidden = false;
        if (t >= INTRO - TAIL) {
            int k = t - (INTRO - TAIL);
            V3[] l = leave(-1, k, host, me);
            host = l[0];
            me = l[1];
            double go = V3.yawOf(l[2]);
            hyaw = turn(hyaw, go, k / 8.0);
            myaw = turn(myaw, go, (k - 4) / 8.0);
            headYaw = myaw;
            hp = Rig.mix(hp, Rig.walk(t / 18.0), Math.min(1, k / 6.0));
            double G = k / (double) (TAIL + glide(0));
            hidden = hiddenIn(G);
        }
        if (!hidden) {
            Rig.props(fr, "host", "dealer", host.up(w > 0 && w < 1 ? Rig.bob(t / 18.0) : 0), hyaw, hp, 2);
            fr.actor = me;
            fr.actorYaw = (float) myaw;
            fr.headYaw = (float) headYaw;
        }
        if (t < 90) {
            // Kranfahrt über das Casino, senkt sich zum Eingang (langsam, ohne Schnitt in die Begrüßung)
            double u = Ease.inOutSine(t / 90.0);
            V3 high = overview.up(9).add(startDir.mul(-6));
            V3 low = start.up(2.6).add(startDir.mul(-3.2)).add(right.mul(1.4));
            fr.cam = high.lerp(low, u);
            fr.look(overview.lerp(start.add(startDir.mul(2)), u).up(1));
            fr.card = t > 10 && t < 80 ? "KUBY CASINO" : null;
        } else if (t < INTRO - TAIL) {
            V3 low = start.up(2.6).add(startDir.mul(-3.2)).add(right.mul(1.4));
            V3[] rest = restCam(-1);
            double g = Ease.inOutSine(seg(t, 90, 125));
            fr.cam = low.lerp(rest[0], g);
            fr.look(start.add(startDir.mul(2)).up(1).lerp(host.up(1.45), g));
        } else {
            travel(fr, 0, (t - (INTRO - TAIL)) / (double) (TAIL + glide(0)));
        }
        if (t == 130 && voice("intro") != null) fr.ev(voice("intro"));
        String sub = subAt(t, 130, INTRO, chunks("Willkommen im Kuby Casino!", "Ich bin Vincent – darf ich Sie herumführen?"));
        if (sub != null) fr.cardSub = WHO + sub;
        if (t == 0) fr.ev(new Ev.Sound("minecraft:block.note_block.chime", 0.8f, 1.0f));
        // leise Lounge-Musik unter dem ganzen Rundgang (endet mit der Szene)
        if (t == 8) fr.ev(new Ev.Sound("minecraft:music_disc.cat", 0.22f, 1.0f));
        if (t == 90) fr.ev(new Ev.Sound("minecraft:block.note_block.bell", 0.6f, 1.2f));
        if (w > 0 && w < 1 && t % 9 == 0) fr.ev(new Ev.SoundAt("minecraft:block.wood.step", host, 0.35f, 1.0f));
    }

    /** Kamera der Zweier-Einstellung einer Station (Stelle vorab in der Welt geprüft: nicht in einer Wand). */
    private V3[] twoShot(int i) {
        Station s = stations.get(i);
        V3 f = s.front().flat();
        V3 r = f.right();
        V3 spot = s.focus().add(f.mul(s.dist()));
        V3 host = spot.add(r.mul(0.9)), me = spot.add(r.mul(-0.4));
        V3 cam = s.twoCam() != null ? s.twoCam() : spot.add(f.mul(2.6)).add(r.mul(2.2)).up(1.75);
        return new V3[]{cam, host.lerp(me, 0.4).up(1.45).lerp(s.focus().up(1.0), 0.25)};
    }

    private static V3 arc(V3 a, V3 b, double t) {
        // leichter Bogen nach oben, damit die Fahrt über Tische und Automaten geht statt hindurch
        double lift = Math.min(3.0, a.dist(b) * 0.25) * Math.sin(t * Math.PI);
        return a.lerp(b, t).up(lift);
    }

    private void station(int u, int i, Frame fr) {
        Station s = stations.get(i);
        V3 f = s.front().flat();
        V3 hostSpot = hostRest(i);
        V3 playerSpot = meRest(i);
        // ankommen: beide gehen die letzten Schritte heran (von der offenen Seite vor der Station)
        double w = seg(u, 0, st(34));
        double wm = seg(u, st(4), st(38));
        V3 back = f.mul(2.4);
        V3 host = hostSpot.add(back).lerp(hostSpot, Ease.inOutSine(w));
        V3 me = playerSpot.add(back).lerp(playerSpot, Ease.inOutSine(wm));
        V3 aim = s.brollLook() != null ? s.brollLook() : s.focus().up(1.0);
        double faceStation = V3.yawOf(s.focus().sub(host));
        double faceMe = V3.yawOf(me.sub(host));
        double walkYaw = V3.yawOf(back.mul(-1));
        // Blick: beim Gehen in Laufrichtung, dann zur Station, beim Erklären halb zum Spieler (weich gedreht)
        double hyaw = w < 1 ? turn(walkYaw, faceStation, seg(u, st(26), st(36)))
                : turn(faceStation, faceMe + 25, seg(u, st(38), st(48)));
        float[] hp = stride(u, w);
        hp = alive(hp, u + i * 37);
        double speak = seg(u, st(38), st(44)) * (1 - seg(u, st(150), st(158)));
        double pointAmt = 0;
        if (u >= st(40) && u < st(120)) {
            pointAmt = Math.sin(Math.min(1, seg(u, st(40), st(58))) * Math.PI / 2) * (u > st(100) ? 1 - seg(u, st(100), st(120)) : 1);
            hp = point(hp, host, hyaw, aim, pointAmt);
        }
        hp = talk(hp, u, speak * (1 - pointAmt * 0.6));
        double myaw = wm < 1 ? turn(walkYaw, V3.yawOf(s.focus().sub(me)), seg(u, st(30), st(40))) : V3.yawOf(s.focus().sub(me));
        // der Spieler schaut Vincent an, wenn er zu reden beginnt, dann dorthin, wohin er zeigt
        double headYaw = turn(myaw, V3.yawOf(host.sub(me)), seg(u, st(38), st(44)) * (1 - seg(u, st(64), st(72))));
        boolean walking = w > 0 && w < 1;
        boolean hidden = false;
        if (u < glide(i)) {
            hidden = hiddenIn((TAIL + u) / (double) (TAIL + glide(i)));
        }
        if (u >= PER - TAIL) {
            int k = u - (PER - TAIL);
            V3[] l = leave(i, k, host, me);
            host = l[0];
            me = l[1];
            double go = V3.yawOf(l[2]);
            hyaw = turn(hyaw, go, k / 8.0);
            myaw = turn(myaw, go, (k - 4) / 8.0);
            headYaw = myaw;
            hp = Rig.mix(hp, Rig.walk(u / 18.0), Math.min(1, k / 6.0));
            walking = true;
            hidden = hiddenIn(k / (double) (TAIL + glide(i + 1)));
        }
        if (!hidden) {
            Rig.props(fr, "host", "dealer", host.up(walking ? Rig.bob(u / 18.0) : 0), hyaw, hp, 2);
            fr.actor = me;
            fr.actorYaw = (float) myaw;
            fr.headYaw = (float) headYaw;
        }
        // Kamera: keine harten Schnitte (im echten Client ruckelte jeder Perspektivwechsel), sondern Fahrten
        V3[] two = twoShot(i);
        V3 bLook = s.brollLook();
        // langsames Heranfahren im Zwischenschnitt; bei Nahaufnahmen (unter 2 Blöcken) weniger, sonst füllt ein Gesicht das Bild
        double push = s.brollCam().dist(bLook) < 2.0 ? 0.08 : 0.18;
        V3 bCam = s.brollCam().lerp(s.brollCam().lerp(bLook, push), Ease.inOutSine(seg(u, st(78), st(118))));
        int glideIn = glide(i), toB0 = st(62), toB1 = st(82), back0 = st(118), back1 = st(140);
        if (u < glideIn) {
            travel(fr, i, (TAIL + u) / (double) (TAIL + glideIn));
        } else if (u >= PER - TAIL) {
            travel(fr, i + 1, (u - (PER - TAIL)) / (double) (TAIL + glide(i + 1)));
        } else if (u < toB0 || u >= back1) {
            fr.cam = two[0];
            fr.look(two[1]);
        } else if (u < toB1) {
            double g = Ease.inOutSine(seg(u, toB0, toB1));
            fr.cam = two[0].lerp(bCam, g);
            fr.look(two[1].lerp(bLook, g));
        } else if (u < back0) {
            fr.cam = bCam;
            fr.look(bLook);
        } else {
            double g = Ease.inOutSine(seg(u, back0, back1));
            fr.cam = bCam.lerp(two[0], g);
            fr.look(bLook.lerp(two[1], g));
        }
        // Text: ruhiger verteilt (langsamere Untertitel)
        String sub = subAt(u, st(38), st(156), chunks(s.lines()));
        if (sub != null) fr.cardSub = WHO + sub;
        if (u == 4 && s.name() != null) fr.ev(new Ev.Sound("minecraft:block.note_block.pling", 0.4f, 1.5f));
        // Vincents Stimme (ElevenLabs, im Pack unter kuby:tour.<station>); Untertitel laufen im selben Fenster mit
        if (u == st(38) && s.name() != null && voice(s.name()) != null) fr.ev(voice(s.name()));
        if (walking && !hidden && u % 9 == 0) fr.ev(new Ev.SoundAt("minecraft:block.wood.step", host, 0.35f, 1.0f));
        if (u >= st(40) && u < st(100)) fr.card = null;
    }

    private void outro(int u, Frame fr, V3 right) {
        // beide kommen zum Eingang zurück, Vincent verabschiedet sich mit Geste und Verbeugung
        V3 hostEnd = hostRest(stations.size());
        V3 hostIn = hostEnd.add(startDir.mul(1.8)).add(right.mul(0.8));
        double w = seg(u, 0, 40);
        V3 host = hostIn.lerp(hostEnd, Ease.inOutSine(w));
        V3 me = start.add(startDir.mul(1.4)).add(right.mul(-0.6)).lerp(start, Ease.inOutSine(seg(u, 4, 42)));
        double faceMe = V3.yawOf(me.sub(host));
        double hyaw = w < 1 ? turn(V3.yawOf(hostEnd.sub(hostIn)), faceMe, seg(u, 30, 40)) : faceMe;
        float[] hp = alive(stride(u, w), u);
        hp = talk(hp, u, seg(u, 40, 48) * (1 - seg(u, 84, 90)));
        if (u >= 90 && u < 120) {
            double b = Math.sin(seg(u, 90, 120) * Math.PI);
            hp = Rig.set(hp, Rig.TP, (float) (22 * b), Rig.HP, (float) (12 * b), Rig.RSX, -40, Rig.RSZ, 30, Rig.REX, -88);
        }
        double myaw = V3.yawOf(startDir);
        boolean hidden = hiddenIn((TAIL + u) / (double) (TAIL + glide(stations.size())));
        if (!hidden) {
            Rig.props(fr, "host", "dealer", host.up(w > 0 && w < 1 ? Rig.bob(u / 18.0) : 0), hyaw, hp, 2);
            fr.actor = me;
            fr.actorYaw = (float) myaw;
            fr.headYaw = (float) turn(myaw, V3.yawOf(host.sub(me)), seg(u, 36, 46));
        }
        double d = Ease.outCubic(seg(u, 40, OUTRO));
        V3 a = arriveCam(stations.size())[0];
        V3 cam = a.lerp(start.up(2.4).add(startDir.mul(-2.6)).add(right.mul(2.0)), d);
        if (!stations.isEmpty() && u < 40) {
            travel(fr, stations.size(), (TAIL + u) / (double) (TAIL + 40));
        } else {
            fr.cam = cam;
            fr.look(host.up(1.3));
        }
        if (u == 20 && voice("outro") != null) fr.ev(voice("outro"));
        if (u < 90) {
            String sub = subAt(u, 20, 90, chunks("Kleiner Tipp: Von 17 bis 22 Uhr ist Goldene Zeit –", "da zählen Gewinne doppelt."));
            if (sub != null) fr.cardSub = WHO + sub;
        } else if (u < 150) {
            fr.card = "VIEL ERFOLG";
            fr.cardSub = "<gray>Rundgang wiederholen: <white>/casino rundgang";
        }
        if (u == 92) fr.ev(new Ev.Sound("minecraft:ui.toast.challenge_complete", 0.6f, 1.3f));
        fr.grade = u > 90 ? "gold" : "soft";
    }
}
