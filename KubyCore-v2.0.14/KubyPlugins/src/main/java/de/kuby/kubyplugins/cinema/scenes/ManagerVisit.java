package de.kuby.kubyplugins.cinema.scenes;

import de.kuby.kubyplugins.cinema.Ease;
import de.kuby.kubyplugins.cinema.Ev;
import de.kuby.kubyplugins.cinema.Frame;
import de.kuby.kubyplugins.cinema.Prop;
import de.kuby.kubyplugins.cinema.Rig;
import de.kuby.kubyplugins.cinema.Script;
import de.kuby.kubyplugins.cinema.V3;

/**
 * Erster Jackpot: der Gastgeber (Dealer) begleitet den Spieler ins Büro des Geschäftsführers (etwa 17 Sekunden,
 * ab Sekunde 2 überspringbar). Das Büro ist eine Kulisse aus Block-Teilen hoch über dem Casino: Holzboden mit rotem
 * Teppich, vertäfelte Wände, großes Fenster mit Goldleisten, Schreibtisch mit Goldkante, Ledersessel, Bücherregal,
 * goldener Tresor, Lampe.
 *
 * <ol>
 *   <li>Titelkarte „JACKPOT“, Blick von oben auf die Tür</li>
 *   <li>der Gastgeber kommt herein, der Spieler folgt; der Gastgeber tritt zur Seite und verbeugt sich</li>
 *   <li>der Geschäftsführer kommt um den Schreibtisch, Handschlag, er spricht (Untertitel)</li>
 *   <li>er überreicht einen goldenen Chip; goldener Blitz, Titelkarte „HIGH ROLLER“: Einsatzlimits x3</li>
 * </ol>
 *
 * @param office Mitte der Bürotür am Boden (das Büro liegt in +z dahinter)
 */
public final class ManagerVisit implements Script {

    static final int LEN = 340;
    private final V3 O;

    public ManagerVisit(V3 office) {
        this.O = office;
    }

    @Override
    public int length() {
        return LEN;
    }

    @Override
    public int skippableFrom() {
        return 40;
    }

    private V3 at(double x, double y, double z) {
        return O.add(x, y, z);
    }

    private static double seg(int t, int a, int b) {
        return Ease.clamp01((t - a) / (double) (b - a));
    }

    @Override
    public void frame(int t, Frame fr) {
        set(fr);
        // ---------- Figuren ----------
        // Gastgeber (Dealer): kommt herein, tritt zur Seite, verbeugt sich, schaut dann zum Geschäftsführer
        double hu = seg(t, 40, 82);
        V3 host = at(-0.6, 0, -1.5).lerp(at(-0.6, 0, 2.4), Ease.inOutSine(hu));
        double hs = seg(t, 82, 96);
        host = host.lerp(at(-1.7, 0, 3.0), Ease.inOutSine(hs));
        float[] hp = hu > 0 && hu < 1 || hs > 0 && hs < 1 ? Rig.walk(t / 18.0) : Rig.stand();
        double hyaw = hs > 0 && hs < 1 ? -60 : t > 96 ? 160 : 0;
        if (t >= 98 && t < 122) {
            double b = Math.sin(seg(t, 98, 122) * Math.PI);
            hp = Rig.set(hp, Rig.TP, (float) (24 * b), Rig.HP, (float) (14 * b), Rig.RSX, -40, Rig.RSZ, 30, Rig.REX, -88);
        }
        Rig.props(fr, "host", "dealer", host.up(Rig.bob(t / 18.0) * (hu > 0 && hu < 1 ? 1 : 0)), hyaw, hp, 2);

        // Spieler: folgt dem Gastgeber, bleibt vor dem Schreibtisch stehen
        double pu = seg(t, 48, 94);
        fr.actor = at(0.2, 0, -1.8).lerp(at(-0.35, 0, 2.8), Ease.inOutSine(pu));
        fr.actorYaw = 0;
        fr.headYaw = t > 130 && t < 300 ? -8 : 0;
        fr.headPitch = 2;
        fr.swing = t == 172 || t == 182 || t == 192;

        // Geschäftsführer: hinter dem Schreibtisch, kommt um den Tisch, Handschlag, spricht, überreicht den Chip
        V3 m;
        float[] mp;
        double myaw = 180;
        double w1 = seg(t, 122, 140), w2 = seg(t, 140, 152), w3 = seg(t, 152, 166);
        if (t < 122) {
            m = at(0, 0, 6.4);
            mp = Rig.set(Rig.stand(), Rig.RSX, -24, Rig.RSZ, 20, Rig.REX, -58, Rig.LSX, -24, Rig.LSZ, -20, Rig.LEX, -58);
        } else if (t < 166) {
            if (t < 140) {
                m = at(0, 0, 6.4).lerp(at(1.9, 0, 6.3), Ease.inOutSine(w1));
                myaw = -90;
            } else if (t < 152) {
                m = at(1.9, 0, 6.3).lerp(at(1.9, 0, 4.3), Ease.inOutSine(w2));
                myaw = 180;
            } else {
                m = at(1.9, 0, 4.3).lerp(at(0.45, 0, 3.75), Ease.inOutSine(w3));
                myaw = 140;
            }
            mp = Rig.walk(t / 17.0);
            m = m.up(Rig.bob(t / 17.0));
        } else {
            m = at(0.45, 0, 3.75);
            myaw = 165;
            mp = Rig.stand();
            if (t < 205) {
                // Handschlag: Hand vor, auf und ab
                double s = Math.sin((t - 166) * 0.55);
                mp = Rig.set(mp, Rig.RSX, (float) (-62 + 6 * s), Rig.RSZ, 8, Rig.REX, (float) (-14 + 8 * s), Rig.TP, 4);
            } else if (t < 255) {
                // spricht: kleine Handgesten, Kopf nickt mit
                double g = Math.sin((t - 205) * 0.21);
                mp = Rig.set(mp, Rig.LSX, (float) (-34 + 10 * g), Rig.LSZ, -14, Rig.LEX, (float) (-64 + 12 * g),
                        Rig.HP, (float) (4 + 3 * Math.sin((t - 205) * 0.4)));
            } else if (t < 300) {
                // überreicht den goldenen Chip
                double h = Ease.smooth(seg(t, 255, 272));
                mp = Rig.set(mp, Rig.RSX, (float) (-20 - 48 * h), Rig.RSZ, 6, Rig.REX, (float) (-60 + 44 * h), Rig.HP, 10);
            } else {
                mp = Rig.set(mp, Rig.RSX, -24, Rig.RSZ, 20, Rig.REX, -58, Rig.LSX, -24, Rig.LSZ, -20, Rig.LEX, -58, Rig.TP, -4);
            }
        }
        Rig.props(fr, "boss", "manager", m, myaw, mp, 2);

        // goldener Chip in der Hand des Geschäftsführers, schwebt zum Spieler
        if (t >= 262 && t < 312) {
            double c = Ease.inOutSine(seg(t, 270, 296));
            V3 chip = m.add(-0.25, 1.15, -0.45).lerp(at(-0.25, 1.25, 3.1), c);
            fr.prop(Prop.item("chip", "kuby:casino/c3chip_gold", chip, 0.55).rotated(new double[]{
                    Math.sin(t * 0.12) * Math.sqrt(0.5), 0, 0, Math.cos(t * 0.12)}).lit(15).snap(2));
            fr.prop(Prop.item("chipglow", "kuby:fx/glow", chip, 0.5).tint(0xFFC83A).lit(15).board(1).snap(2));
        }

        // ---------- Kamera ----------
        V3 cam, look;
        if (t < 40) {
            double u = Ease.inOutSine(t / 40.0);
            cam = at(3.2, 3.3, 7.3).lerp(at(2.6, 3.0, 6.6), u);
            look = at(0, 1.2, 0);
        } else if (t < 96) {
            cam = at(2.7, 1.7, 4.8);
            look = host.up(1.4).lerp(fr.actor.up(1.4), 0.4);
            fr.cut = t == 40;
        } else if (t < 126) {
            cam = fr.actor.add(0.75, 1.95, -1.3);
            look = at(0, 1.5, 6.4);
            fr.cut = t == 96;
        } else if (t < 166) {
            cam = at(3.6, 2.0, 3.6);
            look = m.up(1.2).lerp(at(0.5, 1.2, 4.5), 0.5);
            fr.cut = t == 126;
        } else if (t < 205) {
            cam = at(1.85, 1.5, 2.45);
            look = at(0.05, 1.15, 3.3);
            fr.cut = t == 166;
        } else if (t < 255) {
            double u = seg(t, 205, 255);
            cam = m.add(0.7, 1.62, -1.15 + 0.2 * u);
            look = m.up(1.55);
            fr.cut = t == 205;
        } else if (t < 300) {
            cam = at(1.5, 1.5, 2.35);
            look = at(-0.05, 1.25, 3.3);
            fr.cut = t == 255;
        } else {
            double u = Ease.outCubic(seg(t, 300, LEN));
            cam = at(3.2, 1.9, 1.6).lerp(at(3.5, 2.4, 0.9), u);
            look = at(0, 1.25, 3.4);
            fr.cut = t == 300;
        }
        fr.cam = cam;
        fr.look(look);

        // ---------- Bild ----------
        fr.bars = t < 10 ? Ease.smooth(t / 10.0) : t > LEN - 12 ? Ease.smooth((LEN - t) / 12.0) : 1;
        fr.grade = t >= 300 ? "gold" : "warm";
        if (t >= 6 && t < 36) {
            fr.card = "JACKPOT";
            fr.cardSub = "<gray>Der Geschäftsführer bittet Sie zu sich.";
        } else if (t >= 100 && t < 140) {
            fr.cardSub = "<gray>Gastgeber: <white>Darf ich vorstellen – unser Geschäftsführer.";
        } else if (t >= 166 && t < 205) {
            fr.cardSub = "<#F2C94C>Geschäftsführer: <white>Herzlichen Glückwunsch zu Ihrem Jackpot!";
        } else if (t >= 205 && t < 250) {
            fr.cardSub = "<#F2C94C>Geschäftsführer: <white>Leute wie Sie spielen bei uns nach anderen Regeln.";
        } else if (t >= 250 && t < 298) {
            fr.cardSub = "<#F2C94C>Geschäftsführer: <white>Ab heute dreifache Höchsteinsätze – an jedem Tisch.";
        } else if (t >= 304 && t < LEN - 8) {
            fr.card = "HIGH ROLLER";
            fr.cardSub = "<gray>Einsatzlimits <gold><b>x3</b></gold> im ganzen Casino";
        }

        // ---------- Ton ----------
        if (t == 0) {
            fr.ev(new Ev.Flash(0x000000, 0, 2, 10));
            fr.ev(new Ev.Sky(12600, 0));
            fr.ev(new Ev.Sound("minecraft:block.note_block.chime", 0.8f, 1.2f));
        }
        if (t == 44) fr.ev(new Ev.SoundAt("minecraft:block.wooden_door.open", at(0, 1, 0), 1f, 0.9f));
        if ((t > 40 && t < 96 || t > 122 && t < 166) && t % 7 == 0)
            fr.ev(new Ev.Sound("minecraft:block.wood.step", 0.35f, 0.9f + (t % 3) * 0.05f));
        if (t == 104) fr.ev(new Ev.Sound("minecraft:entity.villager.ambient", 0.25f, 0.6f));
        if (t == 170) fr.ev(new Ev.Sound("minecraft:entity.player.levelup", 0.4f, 1.6f));
        if (t == 276) fr.ev(new Ev.Sound("minecraft:block.amethyst_block.chime", 1f, 1.2f));
        if (t == 300) {
            fr.ev(new Ev.Flash(0xFFD25A, 0, 1, 7));
            fr.ev(new Ev.Sound("minecraft:ui.toast.challenge_complete", 1f, 1.0f));
            fr.ev(new Ev.Sound("minecraft:entity.firework_rocket.twinkle", 0.8f, 1.0f));
        }
    }

    /** Die Kulisse: Büro des Geschäftsführers (Block-Teile, beleuchtet). */
    private void set(Frame fr) {
        block(fr, "floor", "minecraft:dark_oak_planks", -4, -0.1, -3, 8, 0.1, 11, 11);
        block(fr, "carpet", "minecraft:red_wool", -0.7, 0, -3, 1.4, 0.03, 9.2, 12);
        block(fr, "wallL", "minecraft:dark_oak_planks", -4, 0, -0.15, 3, 4, 0.3, 10);
        block(fr, "wallR", "minecraft:dark_oak_planks", 1, 0, -0.15, 3, 4, 0.3, 10);
        block(fr, "lintel", "minecraft:dark_oak_planks", -1, 2.6, -0.15, 2, 1.4, 0.3, 10);
        block(fr, "sideL", "minecraft:stripped_dark_oak_wood", -4.3, 0, 0, 0.3, 4, 8, 10);
        block(fr, "sideR", "minecraft:stripped_dark_oak_wood", 4, 0, 0, 0.3, 4, 8, 10);
        block(fr, "backLow", "minecraft:stripped_dark_oak_wood", -4, 0, 8, 8, 1, 0.3, 10);
        block(fr, "window", "minecraft:light_blue_stained_glass", -3, 1, 8.05, 6, 2.4, 0.1, 15);
        block(fr, "backHigh", "minecraft:stripped_dark_oak_wood", -4, 3.4, 8, 8, 0.6, 0.3, 10);
        block(fr, "backL", "minecraft:stripped_dark_oak_wood", -4, 1, 8, 1, 2.4, 0.3, 10);
        block(fr, "backR", "minecraft:stripped_dark_oak_wood", 3, 1, 8, 1, 2.4, 0.3, 10);
        block(fr, "trim1", "minecraft:gold_block", -4, 1, 7.94, 8, 0.05, 0.06, 15);
        block(fr, "trim2", "minecraft:gold_block", -4, 3.38, 7.94, 8, 0.05, 0.06, 15);
        block(fr, "ceiling", "minecraft:dark_oak_planks", -4.3, 4, -3, 8.6, 0.2, 11.3, 9);
        block(fr, "light", "minecraft:sea_lantern", -0.35, 3.85, 3.2, 0.7, 0.15, 0.7, 15);
        block(fr, "deskBase", "minecraft:polished_blackstone", -1.3, 0, 5.3, 2.6, 0.85, 0.9, 11);
        block(fr, "deskTop", "minecraft:dark_oak_planks", -1.4, 0.85, 5.2, 2.8, 0.08, 1.1, 12);
        block(fr, "deskGold", "minecraft:gold_block", -1.4, 0.85, 5.17, 2.8, 0.08, 0.03, 15);
        block(fr, "chair", "minecraft:red_wool", -0.4, 0, 6.65, 0.8, 0.5, 0.7, 11);
        block(fr, "chairBack", "minecraft:red_wool", -0.4, 0.5, 7.2, 0.8, 0.95, 0.15, 11);
        block(fr, "books", "minecraft:bookshelf", -4, 0, 2, 0.6, 3, 3, 11);
        block(fr, "safe", "minecraft:gold_block", 3, 0, 6.6, 0.8, 1, 0.8, 14);
        block(fr, "lamp", "minecraft:lantern", 0.75, 0.93, 5.45, 0.4, 0.4, 0.4, 15);
        block(fr, "plant", "minecraft:potted_fern", 3.1, 0, 0.6, 0.8, 0.8, 0.8, 11);
    }

    private void block(Frame fr, String id, String data, double x, double y, double z, double sx, double sy, double sz, int light) {
        fr.prop(Prop.block("set_" + id, data, at(x, y, z), 1).scaled(new V3(sx, sy, sz)).lit(light));
    }
}
