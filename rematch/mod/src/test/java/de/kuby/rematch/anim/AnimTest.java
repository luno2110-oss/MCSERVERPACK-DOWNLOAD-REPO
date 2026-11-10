package de.kuby.rematch.anim;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimTest {
    static Rig rig() {
        try (Reader r = new InputStreamReader(AnimTest.class.getResourceAsStream("/assets/kubyrematch/rematch/rig.json"), StandardCharsets.UTF_8)) {
            return Rig.read(r);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static Clip clip(String name) {
        try (Reader r = new InputStreamReader(AnimTest.class.getResourceAsStream("/assets/kubyrematch/rematch/anim/" + name + ".json"), StandardCharsets.UTF_8)) {
            return Clip.read(r, rig());
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Punkt (Minecraft-Modellraum, Ruhelage) durch die gelöste Kette eines Teils schicken. */
    static double[] partPoint(PartSolver s, PartSolver.Part part, double x, double y, double z) {
        // Kette der Eltern aufbauen
        List<PartSolver.Part> chain = new ArrayList<>();
        for (PartSolver.Part p = part; ; p = PartSolver.Part.values()[p.parent]) { chain.add(0, p); if (p.parent < 0) break; }
        Mat4 m = new Mat4();
        for (PartSolver.Part p : chain) {
            double[] o = s.out[p.ordinal()];
            m = m.mul(Mat4.translation(o[0], o[1], o[2])).mul(Mat4.rotZYX(o[3], o[4], o[5]));
        }
        return m.apply(x - part.px, y - part.py, z - part.pz);
    }

    static Pose pose(Rig rig, String bone, double rx, double ry, double rz) {
        Pose p = new Pose(rig.size());
        int i = rig.index(bone);
        p.rot[i][0] = rx; p.rot[i][1] = ry; p.rot[i][2] = rz;
        return p;
    }

    @Test
    void ruhelage_ist_vanilla() {
        Rig rig = rig();
        PartSolver s = new PartSolver(rig);
        s.solve(new Pose(rig.size()));
        double[] head = s.out[PartSolver.Part.HEAD.ordinal()];
        assertArrayEquals(new double[]{0, 0, 0, 0, 0, 0}, java.util.Arrays.copyOf(head, 6), 1e-9);
        double[] arm = s.out[PartSolver.Part.RIGHT_ARM.ordinal()];
        assertArrayEquals(new double[]{-5, 2, 0, 0, 0, 0}, java.util.Arrays.copyOf(arm, 6), 1e-9);
        double[] fore = s.out[PartSolver.Part.RIGHT_FOREARM.ordinal()];
        assertArrayEquals(new double[]{-1, 4, 0, 0, 0, 0}, java.util.Arrays.copyOf(fore, 6), 1e-9);
        double[] belly = s.out[PartSolver.Part.BELLY.ordinal()];
        assertArrayEquals(new double[]{0, 12, 0, 0, 0, 0}, java.util.Arrays.copyOf(belly, 6), 1e-9);
        double[] shin = s.out[PartSolver.Part.LEFT_SHIN.ordinal()];
        assertArrayEquals(new double[]{0, 6, 0, 0, 0, 0}, java.util.Arrays.copyOf(shin, 6), 1e-9);
    }

    @Test
    void huefte_vorn_knie_hinten() {
        Rig rig = rig();
        PartSolver s = new PartSolver(rig);
        // Hüfte x+90: Bein waagerecht nach vorn (Figur schaut nach -z)
        s.solve(pose(rig, "right2", 90, 0, 0));
        double[] foot = partPoint(s, PartSolver.Part.RIGHT_SHIN, -1.9, 24, 0);
        assertEquals(-1.9, foot[0], 1e-6);
        assertEquals(12, foot[1], 1e-6);
        assertEquals(-12, foot[2], 1e-6);
        // Knie x-90: Unterschenkel nach hinten (+z)
        s.solve(pose(rig, "right_leg2", -90, 0, 0));
        foot = partPoint(s, PartSolver.Part.RIGHT_SHIN, -1.9, 24, 0);
        assertEquals(18, foot[1], 1e-6);
        assertEquals(6, foot[2], 1e-6);
    }

    @Test
    void rechter_arm_z_plus_nach_aussen() {
        Rig rig = rig();
        PartSolver s = new PartSolver(rig);
        s.solve(pose(rig, "right", 0, 0, 90));
        double[] hand = partPoint(s, PartSolver.Part.RIGHT_FOREARM, -6, 12, 0);
        assertTrue(hand[0] < -13, "Hand weit außen rechts (Minecraft -x): " + hand[0]);
        assertTrue(Math.abs(hand[1] - 1) < 2, "Hand auf Schulterhöhe: " + hand[1]);
        // linker Arm z- nach außen
        s.solve(pose(rig, "left", 0, 0, -90));
        hand = partPoint(s, PartSolver.Part.LEFT_FOREARM, 6, 12, 0);
        assertTrue(hand[0] > 13, "linke Hand außen: " + hand[0]);
    }

    @Test
    void rumpf_nimmt_kopf_und_arme_mit() {
        Rig rig = rig();
        PartSolver s = new PartSolver(rig);
        // Bauch x-30: Oberkörper nach vorn
        s.solve(pose(rig, "torso1", -30, 0, 0));
        double[] top = partPoint(s, PartSolver.Part.HEAD, 0, -8, 0);
        assertTrue(top[2] < -5, "Kopf geht nach vorn: " + top[2]);
        double[] sh = partPoint(s, PartSolver.Part.RIGHT_ARM, -5, 2, 0);
        assertTrue(sh[2] < -3, "Schulter geht mit: " + sh[2]);
    }

    static Clip testClip(String json) { return Clip.read(new StringReader(json), rig()); }

    @Test
    void keyframes_linear_catmull_step() {
        Clip c = testClip("{\"name\":\"t\",\"length\":1,\"bones\":{\"head\":{\"rotation\":[[0,0,0,0,\"linear\"],[1,10,0,0,\"linear\"]],"
                + "\"position\":[[0,0,0,0,\"step\"],[0.5,0,4,0,\"step\"]]}}}");
        Pose p = new Pose(rig().size());
        int h = rig().index("head");
        c.sample(0.25, p);
        assertEquals(2.5, p.rot[h][0], 1e-9);
        assertEquals(0, p.pos[h][1], 1e-9);
        c.sample(0.75, p);
        assertEquals(4, p.pos[h][1], 1e-9);
        Clip cm = testClip("{\"name\":\"c\",\"length\":1,\"bones\":{\"head\":{\"rotation\":[[0,0,0,0,\"catmullrom\"],[0.5,10,0,0,\"catmullrom\"],[1,0,0,0,\"catmullrom\"]]}}}");
        cm.sample(0.5, p);
        assertEquals(10, p.rot[h][0], 1e-9);
        cm.sample(0.25, p);
        assertTrue(p.rot[h][0] > 5, "Catmull-Rom wölbt nach oben");
    }

    @Test
    void animator_ueberblenden_ereignisse_hitstop_prioritaet() {
        Rig rig = rig();
        ClipLibrary lib = new ClipLibrary(rig);
        lib.put(testClip("{\"name\":\"schuss\",\"length\":1,\"meta\":{\"priority\":80,\"blend_in\":0.1,\"blend_out\":0.2,\"cancel_after\":0.8},"
                + "\"bones\":{\"head\":{\"rotation\":[[0,0,0,0],[1,10,0,0]]}},\"events\":[[0.5,\"kontakt\"]]}"));
        lib.put(testClip("{\"name\":\"pass\",\"length\":0.5,\"meta\":{\"priority\":40},\"bones\":{}}"));
        Animator a = new Animator(lib);
        List<String> ev = new ArrayList<>();
        a.setListener((an, clip, e) -> { ev.add(clip.name + ":" + e); an.hitStop(0.1); });
        assertTrue(a.play("schuss"));
        assertFalse(a.active() && a.weight() > 0.5);
        a.update(0.05);
        assertTrue(a.weight() > 0 && a.weight() < 1, "blendet ein");
        a.update(0.1);
        assertEquals(1, a.weight(), 1e-9);
        assertFalse(a.play("pass"), "Pass darf den Schuss nicht abbrechen");
        a.update(0.40); // t = 0.55 -> Kontakt
        assertEquals(List.of("schuss:kontakt"), ev);
        double t = a.action().time;
        a.update(0.05); // eingefroren
        assertEquals(t, a.action().time, 1e-9);
        a.update(0.06); // Hit-Stop vorbei
        a.update(0.25); // t > 0.8
        assertTrue(a.play("pass"), "nach dem Abbruchfenster geht der Pass");
        a.update(1.0);
        a.update(1.0);
        assertFalse(a.active(), "Pass zu Ende und ausgeblendet");
    }

    @Test
    void echte_animationen_laden_und_loesen() {
        Rig rig = rig();
        PartSolver s = new PartSolver(rig);
        for (String n : List.of("schuss_normal", "sprung_volley", "fallrueckzieher")) {
            Clip c = clip(n);
            assertTrue(c.length > 0.5);
            assertTrue(c.eventTime("kontakt") > 0, n + " hat eine Kontakt-Marke");
            Pose p = new Pose(rig.size());
            for (double t = 0; t <= c.length; t += 1 / 60.0) {
                c.sample(t, p);
                for (double[] part : s.solve(p))
                    for (double v : part) assertFalse(Double.isNaN(v), n + " NaN bei " + t);
            }
        }
    }
}
