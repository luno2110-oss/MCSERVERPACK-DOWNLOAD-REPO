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
        door(fr, t);
        // ---------- Figuren ----------
        // Spieler: folgt dem Gastgeber, bleibt vor dem Schreibtisch stehen, wendet sich dem Geschäftsführer zu
        double pu = seg(t, 48, 94);
        V3 me = at(0.2, 0, -1.8).lerp(at(-0.35, 0, 2.8), Ease.inOutSine(pu));
        V3 bossEnd = at(0.05, 0, 3.45);

        // Geschäftsführer: sitzt am Schreibtisch (Hände auf dem Tisch, liest), schaut auf, als die Tür aufgeht, steht auf,
        // kommt um den Tisch, Handschlag auf Armlänge, spricht mit Gesten, überreicht den goldenen Chip
        V3 m;
        float[] mp;
        double myaw;
        V3 sit = at(0, -0.2, 6.95), stand = at(0, 0, 6.4);
        if (t < 108) {
            m = sit;
            myaw = 180;
            float look = (float) (14 - 16 * Ease.smooth(seg(t, 46, 60)));
            mp = Rig.set(Rig.stand(), Rig.RLX, -90, Rig.LLX, -90, Rig.RSX, -52, Rig.REX, -34, Rig.RSZ, 6, Rig.LSX, -52, Rig.LEX, -34,
                    Rig.LSZ, -6, Rig.HP, look, Rig.TP, 4);
            mp = alive(mp, t);
        } else if (t < 122) {
            // aufstehen: nach vorn lehnen, Beine strecken, vom Stuhl hoch
            double k = Ease.smooth(seg(t, 108, 122));
            m = sit.lerp(stand, k);
            myaw = 180;
            float[] sitP = Rig.set(Rig.stand(), Rig.RLX, -90, Rig.LLX, -90, Rig.RSX, -52, Rig.REX, -34, Rig.LSX, -52, Rig.LEX, -34);
            mp = Rig.mix(sitP, Rig.stand(), k);
            mp = Rig.set(mp, Rig.TP, (float) (24 * Math.sin(k * Math.PI)));
        } else if (t < 166) {
            double w1 = seg(t, 122, 140), w2 = seg(t, 140, 152), w3 = seg(t, 152, 166);
            V3 c1 = at(1.9, 0, 6.3), c2 = at(1.9, 0, 4.3);
            if (t < 140) {
                m = stand.lerp(c1, Ease.inOutSine(w1));
                myaw = turn(180, V3.yawOf(c1.sub(stand)), seg(t, 122, 128));
            } else if (t < 152) {
                m = c1.lerp(c2, Ease.inOutSine(w2));
                myaw = turn(V3.yawOf(c1.sub(stand)), V3.yawOf(c2.sub(c1)), seg(t, 138, 145));
            } else {
                m = c2.lerp(bossEnd, Ease.inOutSine(w3));
                myaw = turn(V3.yawOf(c2.sub(c1)), V3.yawOf(bossEnd.sub(c2)), seg(t, 150, 156));
            }
            mp = Rig.mix(Rig.stand(), Rig.walk(t / 17.0), Math.min(1, Math.min(seg(t, 122, 128), 1 - seg(t, 160, 166))));
            m = m.up(Rig.bob(t / 17.0));
        } else {
            m = bossEnd;
            myaw = turn(V3.yawOf(bossEnd.sub(at(1.9, 0, 4.3))), V3.yawOf(me.sub(bossEnd)), seg(t, 160, 170));
            mp = alive(Rig.stand(), t);
            if (t < 205) {
                // Handschlag: Hand auf Armlänge vor, drei Mal auf und ab, Kopf leicht geneigt
                double in = Ease.smooth(seg(t, 166, 172)) * (1 - Ease.smooth(seg(t, 198, 205)));
                double sh = Math.sin((t - 166) * 0.55) * (t > 172 && t < 196 ? 1 : 0);
                mp = Rig.set(mp, Rig.RSX, (float) (-5 - 62 * in + 6 * sh), Rig.RSZ, (float) (8 * in), Rig.REX, (float) (-12 - 4 * in + 8 * sh),
                        Rig.TP, (float) (5 * in), Rig.HP, (float) (6 * in));
            } else if (t < 255) {
                mp = talk(mp, t, seg(t, 205, 212));
            } else if (t < 300) {
                // überreicht den goldenen Chip: Hand mit dem Chip vor, hält ihn hin, gibt ihn ab
                double h = Ease.smooth(seg(t, 255, 272)) * (1 - Ease.smooth(seg(t, 290, 300)));
                mp = Rig.set(mp, Rig.RSX, (float) (-20 - 48 * h), Rig.RSZ, 6, Rig.REX, (float) (-60 + 44 * h), Rig.HP, (float) (4 + 6 * h));
            } else {
                mp = Rig.set(mp, Rig.RSX, -24, Rig.RSZ, 20, Rig.REX, -58, Rig.LSX, -24, Rig.LSZ, -20, Rig.LEX, -58, Rig.TP, -4);
            }
        }
        Rig.props(fr, "boss", "manager", m, myaw, mp, 2);

        // Gastgeber (Dealer): kommt herein, tritt zur Seite, stellt mit offener Hand vor, steht dann wie ein Kellner da
        // (Hände übereinander) und schaut zu, wer gerade spricht
        double hu = seg(t, 40, 82);
        V3 host = at(-0.6, 0, -1.5).lerp(at(-0.6, 0, 2.4), Ease.inOutSine(hu));
        double hs = seg(t, 82, 96);
        V3 aside = at(-1.7, 0, 3.0);
        host = host.lerp(aside, Ease.inOutSine(hs));
        boolean hWalk = hu > 0 && hu < 1 || hs > 0 && hs < 1;
        float[] hp = hWalk ? Rig.mix(Rig.stand(), Rig.walk(t / 18.0), Math.min(1, Math.min(seg(t, 40, 46), 1 - seg(t, 90, 96))))
                : alive(Rig.stand(), t + 31);
        V3 bossHead = m.up(1.5);
        double hyaw;
        if (t < 82) {
            hyaw = 0;
        } else if (t < 96) {
            hyaw = turn(0, V3.yawOf(aside.sub(at(-0.6, 0, 2.4))), seg(t, 82, 88));
        } else {
            hyaw = turn(V3.yawOf(aside.sub(at(-0.6, 0, 2.4))), V3.yawOf(bossHead.sub(host)), seg(t, 94, 102));
        }
        if (t >= 98 && t < 140) {
            // „Darf ich vorstellen“: offene Hand zum Geschäftsführer, kurzes Nicken
            double a = Math.sin(Math.min(1, seg(t, 98, 108)) * Math.PI / 2) * (1 - seg(t, 128, 140));
            hp = Rig.set(hp, Rig.RSX, (float) (-5 - 50 * a), Rig.RSZ, (float) (-14 * a), Rig.REX, (float) (-12 - 18 * a),
                    Rig.HP, (float) (4 * Math.sin(seg(t, 100, 112) * Math.PI)));
        } else if (t >= 140) {
            double a = Ease.smooth(seg(t, 140, 150));
            hp = Rig.set(hp, Rig.RSX, (float) (-5 - 19 * a), Rig.RSZ, (float) (20 * a), Rig.REX, (float) (-12 - 46 * a),
                    Rig.LSX, (float) (-5 - 19 * a), Rig.LSZ, (float) (-20 * a), Rig.LEX, (float) (-12 - 46 * a));
            if (t >= 300 && t < 324) {
                double bw = Math.sin(seg(t, 300, 324) * Math.PI);
                hp = Rig.set(hp, Rig.TP, (float) (16 * bw), Rig.HP, (float) (10 * bw));
            }
        }
        Rig.props(fr, "host", "dealer", host.up(hWalk ? Rig.bob(t / 18.0) : 0), hyaw, hp, 2);

        fr.actor = me;
        double myawP = t < 150 ? 0 : turn(0, V3.yawOf(bossEnd.sub(me)), seg(t, 150, 162));
        fr.actorYaw = (float) myawP;
        fr.headYaw = (float) (t > 96 && t < 150 ? turn(0, V3.yawOf(m.sub(me)), seg(t, 96, 104)) : myawP);
        fr.headPitch = 2;
        fr.swing = t == 175 || t == 184 || t == 193;

        // goldener Chip: aus der Hand des Geschäftsführers in die Hand des Spielers
        if (t >= 262 && t < 312) {
            double c = Ease.inOutSine(seg(t, 272, 294));
            V3 from = m.add(V3.dir(myaw, 0).mul(0.5)).up(1.12).add(V3.dir(myaw, 0).right().mul(0.25));
            V3 to = me.add(V3.dir(myawP, 0).mul(0.4)).up(1.0).add(V3.dir(myawP, 0).right().mul(0.32));
            V3 chip = from.lerp(to, c).up(0.12 * Math.sin(c * Math.PI));
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
            // über die linke Schulter des Spielers (rechts stünde der Gastgeber halb im Bild)
            cam = fr.actor.add(1.0, 1.9, -1.0);
            look = m.up(t < 112 ? 1.15 : 1.45);
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
        if (t == 110) fr.ev(new Ev.SoundAt("minecraft:block.wood.place", at(0, 0.5, 6.9), 0.6f, 0.8f));
        if (t == 170) fr.ev(new Ev.Sound("minecraft:entity.player.levelup", 0.4f, 1.6f));
        if (t == 276) fr.ev(new Ev.Sound("minecraft:block.amethyst_block.chime", 1f, 1.2f));
        if (t == 300) {
            fr.ev(new Ev.Flash(0xFFD25A, 0, 1, 7));
            fr.ev(new Ev.Sound("minecraft:ui.toast.challenge_complete", 1f, 1.0f));
            fr.ev(new Ev.Sound("minecraft:entity.firework_rocket.twinkle", 0.8f, 1.0f));
        }
    }

    private static double turn(double from, double to, double k) {
        return Ease.lerpAngle(from, to, Ease.smooth(Ease.clamp01(k)));
    }

    /** Atmen und leichtes Verlagern des Gewichts. */
    private static float[] alive(float[] p, int t) {
        return Rig.set(p, Rig.TP, (float) (p[Rig.TP] + 1.2 * Math.sin(t * Math.PI * 2 / 80)),
                Rig.TR, (float) (p[Rig.TR] + 1.4 * Math.sin(t * Math.PI * 2 / 160)));
    }

    /** Sprechen: Kopf nickt im Satzrhythmus, linke Hand öffnet sich zur Geste. */
    private static float[] talk(float[] p, int t, double a) {
        double beat = (t % 44) / 44.0;
        double g = beat < 0.55 ? Math.sin(beat / 0.55 * Math.PI) : 0;
        return Rig.set(p, Rig.HP, (float) (p[Rig.HP] + a * (4 + 3 * Math.sin(t * 0.4))),
                Rig.LSX, (float) (p[Rig.LSX] - a * (22 + 14 * g)), Rig.LSZ, (float) (p[Rig.LSZ] + a * (-8 + 18 * g)),
                Rig.LEX, (float) (p[Rig.LEX] - a * (40 + 16 * g)), Rig.TY, (float) (p[Rig.TY] + a * 3 * g));
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

    /** Flügeltür (zwei Flügel, Scharniere außen), geht ab Tick 40 nach innen auf. */
    private void door(Frame fr, int t) {
        double open = Ease.inOutSine(seg(t, 40, 54)) * 90;
        for (int side = -1; side <= 1; side += 2) {
            // Blockanzeige dreht um ihre Ecke: Scharnier bei x = ±1, Flügel ragt zur Mitte
            double ang = side < 0 ? -open : 180 + open;
            double r = Math.toRadians(ang) / 2;
            fr.prop(Prop.block("door" + side, "minecraft:dark_oak_planks", at(side, 0, -0.04), 1)
                    .scaled(new V3(1.0, 2.6, 0.08)).rotated(new double[]{0, Math.sin(r), 0, Math.cos(r)}).lit(10).snap(2));
            // Knauf dreht mit dem Flügel (die Verschiebung einer Anzeige dreht nicht mit, darum selbst drehen)
            double a = Math.toRadians(ang);
            V3 knob = new V3(0.82 * Math.cos(a) - 0.03 * Math.sin(a), 1.15, -0.82 * Math.sin(a) - 0.03 * Math.cos(a));
            fr.prop(Prop.block("knob" + side, "minecraft:gold_block", at(side, 0, -0.04), 1)
                    .moved(knob).scaled(new V3(0.08, 0.08, 0.14))
                    .rotated(new double[]{0, Math.sin(r), 0, Math.cos(r)}).lit(14).snap(2));
        }
    }

    private void block(Frame fr, String id, String data, double x, double y, double z, double sx, double sy, double sz, int light) {
        fr.prop(Prop.block("set_" + id, data, at(x, y, z), 1).scaled(new V3(sx, sy, sz)).lit(light));
    }
}
