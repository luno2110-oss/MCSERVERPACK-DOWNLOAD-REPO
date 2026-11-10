package de.kuby.rematch.anim;

import java.util.ArrayList;
import java.util.List;

/**
 * Animations-Zustand einer Figur.
 * <ul>
 * <li>Grundebene: eine Schleife (z. B. stehen/laufen), weich gewechselt. Ohne Grundebene bleibt die normale
 *     Minecraft-Bewegung sichtbar, die Aktion wird über {@link #weight()} darübergeblendet.</li>
 * <li>Aktionsebene: Schuss, Pass, Jubel ... mit Überblenden (Easing), Priorität und Abbruchfenster.</li>
 * <li>Additive Ebene: kleine Zusätze (Atmen, Neigung) obendrauf.</li>
 * <li>Ereignis-Marken (z. B. "kontakt") gehen an den {@link Listener}; Hit-Stop friert die Aktion kurz ein.</li>
 * </ul>
 */
public final class Animator {
    public interface Listener { void onEvent(Animator animator, Clip clip, String event); }

    /** Eine laufende Animation. */
    public static final class Instance {
        public final Clip clip;
        public double time;
        final double speed;
        final int priority;
        final double blendIn, blendOut;
        final Easing easeIn, easeOut;
        /** Uhr (Animator-Zeit), seit der sie läuft bzw. ausblendet; -1 = blendet nicht aus. */
        double started, stopping = -1;
        /** Gewicht der Aktionsebene beim Start (weiches Einblenden aus dem aktuellen Stand). */
        double startWeight;

        Instance(Clip clip, double speed, double now) {
            this.clip = clip;
            this.speed = speed * clip.meta.speed();
            this.priority = clip.meta.priority();
            this.blendIn = clip.meta.blendIn();
            this.blendOut = clip.meta.blendOut();
            this.easeIn = clip.meta.easeIn();
            this.easeOut = clip.meta.easeOut();
            this.started = now;
        }

        boolean cancellable() {
            double c = clip.meta.cancelAfter();
            return c >= 0 && time >= c;
        }

        /** Zeit im Clip (Schleife umgebrochen, sonst am Ende festgehalten). */
        double sampleTime() {
            if (clip.loop == Clip.Loop.LOOP && clip.length > 0) return time % clip.length;
            return Math.min(time, clip.length);
        }
    }

    private final ClipLibrary lib;
    private Listener listener;
    private double clock;           // läuft immer (Überblendungen)
    private double freezeUntil = -1; // Hit-Stop
    private Instance base, prevBase;
    private double baseSwitch;
    private Instance action, prevAction;
    private double actionWeight;    // Gewicht der Aktionsebene über der Grundebene / Minecraft
    private final List<Instance> additive = new ArrayList<>();
    private final Pose out, a, b;

    public Animator(ClipLibrary lib) {
        this.lib = lib;
        int n = lib.rig().size();
        out = new Pose(n);
        a = new Pose(n);
        b = new Pose(n);
    }

    public void setListener(Listener l) { this.listener = l; }

    public Rig rig() { return lib.rig(); }

    public Instance action() { return action; }

    public double clock() { return clock; }

    public boolean frozen() { return clock < freezeUntil; }

    /** Startet eine Aktion. false, wenn eine wichtigere Aktion läuft und noch nicht abbrechbar ist. */
    public boolean play(String name) { return play(name, 1.0, false); }

    public boolean play(String name, double speed, boolean force) {
        Clip c = lib.get(name);
        if (c == null) return false;
        if (!force && action != null && action.stopping < 0 && !finished(action)
                && action.priority > c.meta.priority() && !action.cancellable()) return false;
        prevAction = action; // auch eine ausblendende Aktion weich übernehmen
        action = new Instance(c, speed, clock);
        action.startWeight = actionWeight;
        fireEvents(action, -1e-9, 0); // Marken bei 0
        return true;
    }

    /** Blendet die Aktion aus (Ausblendzeit aus dem Clip, oder eigene). */
    public void stop() {
        if (action != null && action.stopping < 0) action.stopping = clock;
    }

    /** Grund-Schleife wechseln (null = normale Minecraft-Bewegung). */
    public void setBase(String name) {
        Clip c = name == null ? null : lib.get(name);
        if (base != null && c != null && base.clip == c) return;
        prevBase = base;
        base = c == null ? null : new Instance(c, 1.0, clock);
        baseSwitch = clock;
    }

    public void addAdditive(String name) {
        Clip c = lib.get(name);
        if (c != null && additive.stream().noneMatch(i -> i.clip == c)) additive.add(new Instance(c, 1.0, clock));
    }

    public void removeAdditive(String name) {
        additive.removeIf(i -> i.clip.name.equals(name));
    }

    /** Friert die Aktion für die Dauer ein (Hit-Stop beim Ballkontakt). */
    public void hitStop(double seconds) {
        freezeUntil = Math.max(freezeUntil, clock + seconds);
    }

    /** Ob die Figur gerade von der Mod bewegt wird. */
    public boolean active() { return action != null || base != null || weight() > 1e-4; }

    /** Wie stark die Mod-Pose die normale Minecraft-Pose ersetzt (0..1). */
    public double weight() {
        double bw = base != null ? 1 : prevBase != null ? 1 - fade(baseSwitch, 0.25) : 0;
        return Math.max(bw, actionWeight);
    }

    private static boolean finished(Instance i) {
        return i.clip.loop == Clip.Loop.ONCE && i.time >= i.clip.length;
    }

    private double fade(double since, double dur) {
        return dur <= 0 ? 1 : Math.min(1, (clock - since) / dur);
    }

    /** Zeit weiterlaufen lassen (Sekunden). */
    public void update(double dt) {
        if (dt <= 0) return;
        boolean wasFrozen = frozen();
        clock += dt;
        if (!wasFrozen) {
            if (action != null) {
                double t0 = action.time;
                action.time += dt * action.speed;
                fireEvents(action, t0, action.time);
                if (finished(action) && action.stopping < 0) action.stopping = clock;
            }
            if (prevAction != null) prevAction.time += dt * prevAction.speed;
        }
        if (base != null) base.time += dt;
        if (prevBase != null) {
            prevBase.time += dt;
            if (fade(baseSwitch, 0.25) >= 1) prevBase = null;
        }
        for (Instance i : additive) i.time += dt;
        // Gewicht der Aktionsebene
        if (action == null) {
            actionWeight = 0;
        } else if (action.stopping >= 0) {
            double k = action.easeOut.apply(fade(action.stopping, action.blendOut));
            actionWeight = Math.min(actionWeight, 1 - k);
            if (k >= 1) { action = null; prevAction = null; actionWeight = 0; }
        } else {
            double k = action.easeIn.apply(fade(action.started, action.blendIn));
            actionWeight = action.startWeight + (1 - action.startWeight) * k;
        }
        if (action != null && prevAction != null && fade(action.started, action.blendIn) >= 1) prevAction = null;
    }

    private void fireEvents(Instance i, double from, double to) {
        if (listener == null || i.clip.events.isEmpty()) return;
        double len = i.clip.length;
        if (i.clip.loop == Clip.Loop.LOOP && len > 0) {
            double f = from % len, t = f + (to - from);
            for (double base = 0; base < t; base += len)
                for (Clip.Event e : i.clip.events)
                    if (e.time() + base > f && e.time() + base <= t) listener.onEvent(this, i.clip, e.name());
            return;
        }
        for (Clip.Event e : i.clip.events)
            if (e.time() > from && e.time() <= to) listener.onEvent(this, i.clip, e.name());
    }

    /** Fertige Pose (Grundebene, darüber Aktion mit Überblendung, dann additiv). */
    public Pose pose() {
        // Grundebene
        if (base != null) {
            base.clip.sample(base.sampleTime(), out);
            if (prevBase != null) {
                prevBase.clip.sample(prevBase.sampleTime(), a);
                double k = Easing.IN_OUT_QUAD.apply(fade(baseSwitch, 0.25));
                a.lerpTo(out, k);
                out.copyFrom(a);
            }
        } else {
            out.reset();
        }
        // Aktion (mit Überblendung von der vorigen Aktion)
        if (action != null) {
            action.clip.sample(action.sampleTime(), a);
            if (prevAction != null) {
                prevAction.clip.sample(prevAction.sampleTime(), b);
                b.lerpTo(a, action.easeIn.apply(fade(action.started, action.blendIn)));
                a.copyFrom(b);
            }
            // Ohne Grundebene übernimmt weight() das Überblenden mit Minecraft; mit Grundebene hier mischen
            out.lerpTo(a, base != null ? actionWeight : 1);
        }
        for (Instance i : additive) {
            i.clip.sample(i.sampleTime(), a);
            out.add(a, 1);
        }
        return out;
    }
}
