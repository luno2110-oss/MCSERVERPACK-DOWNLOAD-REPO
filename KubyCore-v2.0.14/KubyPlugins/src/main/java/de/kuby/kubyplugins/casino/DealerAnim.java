package de.kuby.kubyplugins.casino;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Dealer als Gelenkfigur (v1.70): Kopf, Oberkörper, Beine, Ober- und Unterarme sind einzelne Item-Anzeigen mit dem
 * Smoking-Skin (Modelle kuby:casino/rig_dealer_*, gebaut von tools/build_skins.py). Jedes Gelenk folgt seiner Zielpose
 * über eine kritisch gedämpfte Feder (kein Überschwingen, kein Ruck), der Client blendet zwischen den Schritten weich über.
 *
 * <p>Ziel: menschlich, nicht steif, nicht wabbelig. Darum
 * <ul>
 *   <li>Grundhaltung aufrecht mit leicht herausgestreckter Brust, Atmung (4,2 s) und langsame Gewichtsverlagerung in
 *       unregelmäßigen Abständen; der Blick springt in kleinen Sakkaden statt starr zu bleiben</li>
 *   <li>Kopf schneller als Oberkörper, Unterarm leicht verzögert zum Oberarm (Folgebewegung)</li>
 *   <li>Gesten als Keyframes mit weichen Übergängen (smoothstep), Leerlauf-Gesten zufällig, nie zweimal dieselbe</li>
 * </ul>
 *
 * <p>Gesten: Austeilen (Stapel in der linken Hand, rechte schickt die Karte), Hand über den Tisch („Ihre Karten,
 * meine Herrschaften“), Mischen, Chips schieben und einsammeln, Applaus, Kopfschütteln, Nicken, Verbeugung, auf den
 * Tisch klopfen; Leerlauf: Krawatte richten, Sakko-Ärmel nachziehen, Manschette richten, auf den Tisch stützen, durch
 * den Raum schauen. Wartet ein Spieler auf seinen Zug, schaut der Dealer ihn an und hält die Hände gefaltet.
 */
public final class DealerAnim {

    // Gelenke (Grad): Oberkörper Neigung/Drehung/Seite, Kopf, rechte und linke Schulter (x vor/zurück, y Schwenk,
    // z abspreizen), Ellbogen, Hüft-Seitenneigung der Beine
    static final int TP = 0, TY = 1, TR = 2, HP = 3, HY = 4, HR = 5,
            RSX = 6, RSY = 7, RSZ = 8, REX = 9, LSX = 10, LSY = 11, LSZ = 12, LEX = 13, LG = 14, N = 15;
    /** Federfrequenz je Gelenk (rad/s): Kopf schnell, Rumpf ruhig, Unterarm etwas weicher als Oberarm. */
    private static final float[] OMEGA = {7, 7, 6, 15, 15, 12, 11, 11, 11, 10, 11, 11, 11, 10, 5};
    private static final float SCALE = 0.9375f;
    private static final String[] PARTS = {"head", "torso", "rleg", "lleg", "rarm_up", "rarm_lo", "larm_up", "larm_lo"};

    public enum Stance { UPRIGHT, WAIT, LEAN }

    private final Plugin plugin;
    private final Location root;
    private final float yaw;
    private final ItemDisplay[] parts = new ItemDisplay[PARTS.length];
    private Interaction hit;
    private BukkitTask task;
    private final Random rnd = new Random();

    private final float[] cur = new float[N];
    private final float[] vel = new float[N];
    private long tick;

    private Stance stance = Stance.UPRIGHT;
    private Location look;
    private long lookUntil;
    private float sacY, sacP;
    private long nextSaccade;
    private float sway, swayTarget;
    private long nextSway;

    private Clip clip;
    private long clipStart;
    private long busyUntil;
    private String lastIdle = "";
    private long nextIdle = 100;

    public DealerAnim(Plugin plugin, World w, Location feet, float yaw, Consumer<Entity> mark, Consumer<Interaction> hitSetup) {
        this.plugin = plugin;
        this.root = feet.clone();
        this.root.setYaw(0);
        this.root.setPitch(0);
        this.yaw = yaw;
        base(cur);
        for (int i = 0; i < PARTS.length; i++) {
            final String part = PARTS[i];
            parts[i] = w.spawn(root, ItemDisplay.class, d -> {
                d.setItemStack(BlackjackTable.model("rig_dealer_" + part));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTeleportDuration(0);
                d.setInterpolationDuration(3);
                d.setViewRange(0.8f);
                d.setBrightness(null);
                d.setShadowRadius(part.equals("torso") ? 0.35f : 0f);
                d.setShadowStrength(0.6f);
                if (mark != null) mark.accept(d);
            });
        }
        hit = w.spawn(root, Interaction.class, e -> {
            e.setInteractionWidth(0.7f);
            e.setInteractionHeight(1.9f);
            if (mark != null) mark.accept(e);
            if (hitSetup != null) hitSetup.accept(e);
        });
        apply(0);
        try {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::step, 1L, 1L);
        } catch (Throwable ignored) {
            // Vorschau ohne Scheduler: Pose bleibt stehen
        }
    }

    /** Klickfläche (für Markierungen der Tische). */
    public Interaction hitbox() {
        return hit;
    }

    public Location location() {
        Location l = root.clone();
        l.setYaw(yaw);
        return l;
    }

    private boolean ok() {
        for (ItemDisplay d : parts) if (d == null || !d.isValid()) return false;
        return true;
    }

    public void remove() {
        if (task != null) task.cancel();
        for (ItemDisplay d : parts) if (d != null) d.remove();
        if (hit != null) hit.remove();
    }

    // =================================================================================================================
    // Steuerung von außen (gleiche Namen wie früher)
    // =================================================================================================================

    public boolean busy() {
        return tick < busyUntil;
    }

    public void stance(Stance s) {
        stance = s;
    }

    /** Ruhe: aufrecht, Blick auf den Tisch. */
    public void rest() {
        stance = Stance.UPRIGHT;
        look = null;
    }

    public void lookAt(Location target) {
        look = target == null ? null : target.clone();
        lookUntil = tick + 60;
    }

    /** Karte austeilen: Stapel links an der Brust, die rechte Hand schickt die Karte zum Platz. */
    public void deal(Location to) {
        float sy = to == null ? 0 : localYaw(to);
        lookAt(to);
        play(new Clip("deal", 12)
                .k(0, LSX, -32, LSZ, -14, LEX, -78, RSX, -38, RSZ, 18, REX, -70)
                .k(4, LSX, -32, LSZ, -14, LEX, -78, RSX, -62, RSY, sy * 0.6f, RSZ, 4, REX, -18, TP, 4)
                .k(8, LSX, -32, LSZ, -14, LEX, -78, RSX, -40, RSZ, 16, REX, -62)
                .k(12, LSX, -30, LSZ, -12, LEX, -74));
    }

    /** Karte umdrehen: kurzes Drehen der rechten Hand über dem Filz. */
    public void flip() {
        play(new Clip("flip", 10).k(0, RSX, -50, REX, -30, TP, 5).k(5, RSX, -46, RSZ, 10, REX, -42, TP, 6).k(10));
    }

    /** Hand ruhig über den Tisch führen: „Ihre Karten, meine Herrschaften.“ */
    public void sweep() {
        play(new Clip("sweep", 40)
                .k(0, RSX, -30, RSZ, 10, REX, -40)
                .k(8, RSX, -58, RSY, -38, RSZ, -6, REX, -12, HY, -30, HP, 18, TP, 5, TY, -6)
                .k(26, RSX, -58, RSY, 34, RSZ, 6, REX, -12, HY, 28, HP, 18, TP, 5, TY, 6)
                .k(32, RSX, -40, RSY, 20, REX, -40, HP, 26, TP, 3)
                .k(40));
    }

    /** Mischen (Riffle) mit beiden Händen auf dem Filz, zum Schluss Brücke und Aufklopfen. */
    public void shuffle() {
        Clip c = new Clip("shuffle", 46);
        for (int i = 0; i <= 6; i++) {
            float a = i % 2 == 0 ? 10 : -10;
            c.k(i * 4, RSX, -40, RSZ, 22, REX, -52 + a, LSX, -40, LSZ, -22, LEX, -52 - a, HP, 34, TP, 9);
        }
        c.k(30, RSX, -48, RSZ, 26, REX, -74, LSX, -48, LSZ, -26, LEX, -74, HP, 30, TP, 7);
        c.k(36, RSX, -40, RSZ, 22, REX, -40, LSX, -40, LSZ, -22, LEX, -40, HP, 36, TP, 9);
        c.k(46);
        play(c);
    }

    /** Chips zum Gewinner schieben: vorbeugen, zweimal schieben. */
    public void push(Location to) {
        float sy = to == null ? 0 : localYaw(to);
        lookAt(to);
        play(new Clip("push", 26)
                .k(0, RSX, -50, RSY, sy * 0.7f, REX, -50, TP, 6)
                .k(7, RSX, -74, RSY, sy * 0.7f, REX, -8, TP, 13)
                .k(12, RSX, -60, RSY, sy * 0.7f, REX, -34, TP, 10)
                .k(18, RSX, -76, RSY, sy * 0.7f, REX, -6, TP, 13)
                .k(26));
    }

    /** Verlorene Einsätze einsammeln: linke Hand reicht hin und zieht zu sich. */
    public void collect(Location from) {
        float sy = from == null ? 0 : localYaw(from);
        lookAt(from);
        play(new Clip("collect", 24)
                .k(0, LSX, -50, LSY, sy * 0.7f, LEX, -40, TP, 6)
                .k(8, LSX, -72, LSY, sy * 0.7f, LEX, -8, TP, 12)
                .k(16, LSX, -40, LSY, sy * 0.3f, LEX, -70, TP, 6)
                .k(24));
    }

    /** Spieler am Zug: anschauen, kurzes Nicken, dann mit gefalteten Händen warten. */
    public void prompt(Location seat) {
        lookAt(seat);
        lookUntil = tick + 20 * 30;
        stance = Stance.WAIT;
        play(new Clip("nod", 14).k(0).k(5, HP, 14).k(10, HP, 0).k(14));
    }

    /** Applaus (Blackjack eines Spielers). */
    public void applaud(Location seat) {
        lookAt(seat);
        Clip c = new Clip("applaud", 30);
        for (int i = 0; i <= 6; i++) {
            float z = i % 2 == 0 ? 38 : 24;
            c.k(2 + i * 3, RSX, -55, RSZ, z, REX, -64, LSX, -55, LSZ, -z, LEX, -64, HP, -4);
        }
        c.k(30);
        play(c);
    }

    /** Kopfschütteln (Bank überkauft), mit einem kleinen Schulterzucken. */
    public void shakeHead() {
        play(new Clip("shake", 26)
                .k(0, HY, 0, HP, 6)
                .k(4, HY, -14, HP, 6, RSZ, -12, LSZ, 12)
                .k(9, HY, 13, HP, 6)
                .k(14, HY, -10, HP, 6)
                .k(19, HY, 6, HP, 8, RSZ, -4, LSZ, 4)
                .k(26));
    }

    public void nod() {
        play(new Clip("nod", 14).k(0).k(5, HP, 16).k(10, HP, -2).k(14));
    }

    /** Leichte Verbeugung zum Rundenende, Hand an der Brust. */
    public void bow() {
        play(new Clip("bow", 34)
                .k(0)
                .k(8, TP, 20, HP, 12, RSX, -40, RSZ, 30, REX, -88)
                .k(20, TP, 20, HP, 12, RSX, -40, RSZ, 30, REX, -88)
                .k(34));
    }

    /** Auf den Tisch klopfen (Check, letzte Karte). */
    public void tap() {
        play(new Clip("tap", 16)
                .k(0, RSX, -44, REX, -40, TP, 6, HP, 30)
                .k(4, RSX, -40, REX, -58, TP, 6, HP, 30)
                .k(7, RSX, -44, REX, -40, TP, 6, HP, 30)
                .k(10, RSX, -40, REX, -58, TP, 6, HP, 30)
                .k(16));
    }

    /** Leerlauf: wird vom Spiel etwa jede Sekunde aufgerufen; players = Augenpositionen der Sitzenden. */
    public void idle(List<Location> players) {
        if (busy() || tick < nextIdle) return;
        nextIdle = tick + 60 + rnd.nextInt(120);
        if (stance == Stance.WAIT) stance = Stance.UPRIGHT;
        List<String> pool = new ArrayList<>(List.of("tie", "sleeve", "cuff", "lean", "room", "glance", "glance", "still"));
        if (players == null || players.isEmpty()) pool.removeIf(s -> s.equals("glance"));
        pool.remove(lastIdle);
        String pick = pool.get(rnd.nextInt(pool.size()));
        lastIdle = pick;
        switch (pick) {
            case "tie" -> tie();
            case "sleeve" -> sleeve();
            case "cuff" -> cuff();
            case "lean" -> lean(60 + rnd.nextInt(80));
            case "room" -> lookRoom();
            case "glance" -> lookAt(players.get(rnd.nextInt(players.size())));
            default -> {
            }
        }
    }

    /** Krawatte (Fliege) richten: beide Hände an den Knoten, kurz zurechtrücken, Kinn hoch, Brust raus. */
    public void tie() {
        play(new Clip("tie", 44)
                .k(0)
                .k(9, RSX, -58, RSZ, 30, REX, -108, LSX, -56, LSZ, -28, LEX, -110, HP, -8)
                .k(15, RSX, -60, RSZ, 36, REX, -106, LSX, -54, LSZ, -22, LEX, -112, HP, -10, HR, 4)
                .k(21, RSX, -58, RSZ, 26, REX, -110, LSX, -56, LSZ, -32, LEX, -108, HP, -10, HR, -3)
                .k(28, RSX, -50, RSZ, 26, REX, -96, LSX, -48, LSZ, -26, LEX, -98, HP, -12, TP, -6)
                .k(36, TP, -6, HP, -4)
                .k(44));
    }

    /** Sakko-Ärmel nachziehen: linken Arm anwinkeln, rechte Hand greift den Ärmel und zieht ihn zweimal nach. */
    public void sleeve() {
        play(new Clip("sleeve", 52)
                .k(0)
                .k(9, LSX, -38, LSZ, -8, LEX, -82, RSX, -44, RSZ, 34, REX, -66, HP, 30, HY, 16)
                .k(15, LSX, -38, LSZ, -8, LEX, -82, RSX, -42, RSZ, 26, REX, -58, HP, 30, HY, 16)
                .k(20, LSX, -38, LSZ, -8, LEX, -82, RSX, -44, RSZ, 34, REX, -66, HP, 30, HY, 16)
                .k(25, LSX, -38, LSZ, -8, LEX, -82, RSX, -42, RSZ, 25, REX, -57, HP, 30, HY, 16)
                .k(33, LSX, -30, LSZ, -4, LEX, -60, RSX, -14, RSZ, 4, REX, -20, HP, 12, HY, 6)
                .k(40, LSX, -12, LSZ, 6, LEX, -18, HP, 8)
                .k(52));
    }

    /** Manschette richten: rechter Arm angewinkelt, linke Hand dreht kurz am Manschettenknopf. */
    public void cuff() {
        play(new Clip("cuff", 40)
                .k(0)
                .k(9, RSX, -36, RSZ, 8, REX, -84, LSX, -44, LSZ, -34, LEX, -64, HP, 28, HY, -14)
                .k(16, RSX, -36, RSZ, 8, REX, -84, LSX, -46, LSZ, -38, LEX, -68, HP, 30, HY, -14, HR, -3)
                .k(23, RSX, -36, RSZ, 8, REX, -84, LSX, -44, LSZ, -32, LEX, -62, HP, 28, HY, -14)
                .k(30, RSX, -20, REX, -40, HP, 12)
                .k(40));
    }

    /** Leicht mit beiden Händen auf dem Tisch abstützen, nach einer Weile wieder aufrichten. */
    public void lean(int ticks) {
        stance = Stance.LEAN;
        busyUntil = Math.max(busyUntil, tick + ticks);
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (stance == Stance.LEAN) {
                    stance = Stance.UPRIGHT;
                    play(new Clip("straighten", 24).k(0, TP, 2).k(12, TP, -6, HP, -4).k(24));
                }
            }, ticks);
        } catch (Throwable ignored) {
            // Vorschau
        }
    }

    /** Ruhig durch den Raum schauen, Oberkörper dreht ein wenig mit. */
    public void lookRoom() {
        look = null;
        play(new Clip("room", 90)
                .k(0)
                .k(18, HY, -52, HP, -2, TY, -10)
                .k(38, HY, -50, HP, -4, TY, -10)
                .k(56, HY, 30, HP, 0, TY, 7)
                .k(74, HY, 46, HP, -2, TY, 9)
                .k(90));
    }

    // =================================================================================================================
    // Pose, Federn, Gelenkkette
    // =================================================================================================================

    private void play(Clip c) {
        clip = c;
        clipStart = tick;
        busyUntil = Math.max(busyUntil, tick + c.length);
    }

    /** Grundhaltung je nach Stand (ohne Atmung und Blick). */
    private void base(float[] p) {
        Arrays.fill(p, 0);
        switch (stance) {
            case UPRIGHT -> {
                // aufrecht, Brust leicht raus, Arme locker, Ellbogen ganz leicht gebeugt
                p[TP] = -3;
                p[HP] = 14;
                p[RSX] = -5;
                p[RSZ] = -3;
                p[REX] = -12;
                p[LSX] = -5;
                p[LSZ] = 3;
                p[LEX] = -12;
            }
            case WAIT -> {
                // Hände vorne gefaltet, aufmerksam
                p[TP] = -2;
                p[RSX] = -24;
                p[RSZ] = 20;
                p[REX] = -58;
                p[LSX] = -24;
                p[LSZ] = -20;
                p[LEX] = -58;
            }
            case LEAN -> {
                // leicht auf die Tischkante gestützt
                p[TP] = 11;
                p[HP] = 18;
                p[RSX] = -46;
                p[RSZ] = 6;
                p[REX] = -10;
                p[LSX] = -46;
                p[LSZ] = -6;
                p[LEX] = -10;
            }
        }
    }

    private void step() {
        if (!ok()) {
            if (task != null) task.cancel();
            return;
        }
        tick++;
        float[] target = new float[N];
        base(target);
        float t = tick / 20f;
        // Atmung: Brust hebt sich, Schultern minimal mit
        float breath = (float) Math.sin(t * 2 * Math.PI / 4.2);
        target[TP] += -0.7f * breath;
        target[RSZ] += -0.8f * breath;
        target[LSZ] += 0.8f * breath;
        // Gewichtsverlagerung in unregelmäßigen Abständen
        if (tick >= nextSway) {
            swayTarget = (rnd.nextFloat() * 2 - 1) * 1.8f;
            nextSway = tick + 100 + rnd.nextInt(140);
        }
        sway += (swayTarget - sway) * 0.02f;
        target[TR] += sway;
        target[LG] -= sway * 0.8f;
        // Blick: Ziel oder Tisch, mit kleinen Sakkaden
        if (tick >= nextSaccade) {
            sacY = (rnd.nextFloat() * 2 - 1) * (look != null ? 3 : 6);
            sacP = (rnd.nextFloat() * 2 - 1) * 2.5f;
            nextSaccade = tick + 25 + rnd.nextInt(60);
        }
        if (look != null && tick > lookUntil) look = null;
        if (look != null) {
            float[] yp = localYawPitch(look);
            target[HY] = clamp(yp[0], -70, 70) * 0.8f;
            target[TY] += clamp(yp[0], -70, 70) * 0.2f;
            target[HP] = clamp(yp[1], -20, 45);
        }
        target[HY] += sacY;
        target[HP] += sacP;
        // Geste
        if (clip != null) {
            int ct = (int) (tick - clipStart);
            if (ct > clip.length) clip = null;
            else clip.sample(ct, target);
        }
        // kritisch gedämpfte Federn
        float dt = 0.05f;
        for (int j = 0; j < N; j++) {
            float w = OMEGA[j];
            float a = w * w * (target[j] - cur[j]) - 2 * w * vel[j];
            vel[j] += a * dt;
            cur[j] += vel[j] * dt;
        }
        if (tick % 2 == 0) apply(3);
    }

    private float localYaw(Location to) {
        return localYawPitch(to)[0];
    }

    /** Gierwinkel (positiv = zur linken Körperseite) und Neigung (positiv = nach unten) vom Kopf zum Ziel. */
    private float[] localYawPitch(Location to) {
        double hx = root.getX(), hy = root.getY() + 1.62, hz = root.getZ();
        double dx = to.getX() - hx, dy = to.getY() - hy, dz = to.getZ() - hz;
        double r = Math.toRadians(yaw);
        // Welt -> lokal (Blickrichtung +Z)
        double lx = dx * Math.cos(r) + dz * Math.sin(r);
        double lz = -dx * Math.sin(r) + dz * Math.cos(r);
        float y = (float) Math.toDegrees(Math.atan2(lx, lz));
        float p = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(lx, lz)));
        return new float[]{y, p};
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    private static Matrix4f rot(Matrix4f m, float yawD, float pitchD, float rollD) {
        return m.rotateY(rad(yawD)).rotateX(rad(pitchD)).rotateZ(rad(rollD));
    }

    /** Gelenkkette ausrechnen und an die Anzeigen schicken. Maße in Skin-Pixeln (Füße bei 0, Kopf 24..32). */
    private void apply(int interp) {
        float px = SCALE / 16f;
        Matrix4f base = new Matrix4f().rotateY(rad(-yaw)).scale(px);
        Matrix4f legs = rot(new Matrix4f(base).translate(0, 12, 0), 0, 0, cur[LG]);
        Matrix4f torso = rot(new Matrix4f(base).translate(0, 12, 0), cur[TY], cur[TP], cur[TR]);
        Matrix4f head = rot(new Matrix4f(torso).translate(0, 12, 0), cur[HY], cur[HP], cur[HR]);
        Matrix4f rUp = rot(new Matrix4f(torso).translate(-6, 11, 0), cur[RSY], cur[RSX], cur[RSZ]);
        Matrix4f rLo = rot(new Matrix4f(rUp).translate(0, -5, 0), 0, cur[REX], 0);
        Matrix4f lUp = rot(new Matrix4f(torso).translate(6, 11, 0), cur[LSY], cur[LSX], cur[LSZ]);
        Matrix4f lLo = rot(new Matrix4f(lUp).translate(0, -5, 0), 0, cur[LEX], 0);
        Matrix4f rLeg = new Matrix4f(legs).translate(-2, 0, 0);
        Matrix4f lLeg = new Matrix4f(legs).translate(2, 0, 0);
        Matrix4f[] ms = {head, torso, rLeg, lLeg, rUp, rLo, lUp, lLo};
        for (int i = 0; i < parts.length; i++) {
            ItemDisplay d = parts[i];
            if (d == null || !d.isValid()) continue;
            // Modell: 16 px je Block, Gelenk in der Modellmitte
            d.setTransformationMatrix(new Matrix4f(ms[i]).scale(16f));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
        }
    }

    /** Geste aus Schlüsselbildern; nicht gesetzte Gelenke behalten die Grundhaltung. */
    static final class Clip {
        final String name;
        final int length;
        private final List<int[]> ticks = new ArrayList<>();
        private final List<float[]> poses = new ArrayList<>();

        Clip(String name, int length) {
            this.name = name;
            this.length = length;
        }

        /** Schlüsselbild bei t: Paare (Gelenk, Wert). */
        Clip k(int t, float... jv) {
            float[] p = new float[N];
            Arrays.fill(p, Float.NaN);
            for (int i = 0; i + 1 < jv.length; i += 2) p[(int) jv[i]] = jv[i + 1];
            ticks.add(new int[]{t});
            poses.add(p);
            return this;
        }

        void sample(int t, float[] target) {
            int n = ticks.size();
            int i = 0;
            while (i < n - 1 && ticks.get(i + 1)[0] <= t) i++;
            float[] a = poses.get(i);
            float[] b = i + 1 < n ? poses.get(i + 1) : a;
            int ta = ticks.get(i)[0], tb = i + 1 < n ? ticks.get(i + 1)[0] : ta;
            float u = tb == ta ? 1 : Math.max(0, Math.min(1, (t - ta) / (float) (tb - ta)));
            u = u * u * (3 - 2 * u);
            for (int j = 0; j < N; j++) {
                float va = Float.isNaN(a[j]) ? target[j] : a[j];
                float vb = Float.isNaN(b[j]) ? target[j] : b[j];
                target[j] = va + (vb - va) * u;
            }
        }
    }

    /** Für die Vorschau: Pose direkt setzen (ohne Federn). */
    public void previewPose(String gesture, int atTick) {
        Clip c = switch (gesture) {
            case "tie" -> { tie(); yield clip; }
            case "sleeve" -> { sleeve(); yield clip; }
            case "cuff" -> { cuff(); yield clip; }
            case "sweep" -> { sweep(); yield clip; }
            case "shuffle" -> { shuffle(); yield clip; }
            case "applaud" -> { applaud(null); yield clip; }
            case "bow" -> { bow(); yield clip; }
            case "deal" -> { deal(null); yield clip; }
            default -> null;
        };
        if (gesture.equals("lean")) stance = Stance.LEAN;
        if (gesture.equals("wait")) stance = Stance.WAIT;
        float[] p = new float[N];
        base(p);
        if (c != null) c.sample(atTick, p);
        System.arraycopy(p, 0, cur, 0, N);
        apply(0);
    }
}
