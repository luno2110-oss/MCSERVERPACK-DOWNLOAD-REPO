package de.kuby.kubyplugins.cinema;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Bild der Szene (ein Tick): Kamera, Darsteller, sichtbare Teile, Ereignisse und Bildschirm-Ebenen.
 * Das Drehbuch füllt es, die Engine setzt es um.
 */
public final class Frame {

    // Kamera
    public V3 cam = V3.ZERO;
    public float camYaw, camPitch;
    /** Harter Schnitt: Kamera springt (ohne Gleiten). */
    public boolean cut;

    // Darsteller (Figur mit dem Skin des Zuschauers); actor null = ausgeblendet
    public V3 actor;
    public float actorYaw, headYaw, headPitch;
    public boolean crouch, swing, glow;
    /**
     * Item in der Hand (Modell-Schlüssel) oder null; mit "|haltung" benutzt die Figur es dauerhaft:
     * "|trident" Arm hoch, "|crossbow" beide Arme nach vorn, "|block" Arm angewinkelt, "|bow" Bogen spannen.
     */
    public String hand;

    // Bildschirm
    /** Kino-Balken 0 (weg) bis 1 (ganz da). */
    public double bars;
    /** Farb-Stimmung über dem ganzen Bild (Name der Glyphe, z. B. "red", "warm", "space") oder null. */
    public String grade;
    /** Große Zeile in der Bildmitte (Titelkarte) oder null. */
    public String card, cardSub;

    public final List<Prop> props = new ArrayList<>();
    public final List<Object> events = new ArrayList<>();

    public Frame prop(Prop p) {
        props.add(p);
        return this;
    }

    public Frame ev(Object e) {
        events.add(e);
        return this;
    }

    public void look(V3 target) {
        camYaw = cam.yawTo(target);
        camPitch = cam.pitchTo(target);
    }
}
