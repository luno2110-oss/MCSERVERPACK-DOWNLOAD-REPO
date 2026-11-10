package de.kuby.rematch.client.anim;

import de.kuby.rematch.KubyRematch;
import de.kuby.rematch.anim.Animator;
import de.kuby.rematch.anim.Clip;
import de.kuby.rematch.anim.ClipLibrary;
import de.kuby.rematch.anim.Mat4;
import de.kuby.rematch.anim.PartSolver;
import de.kuby.rematch.anim.Pose;
import de.kuby.rematch.anim.Rig;
import com.mojang.math.Transformation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Animatoren aller sichtbaren Spieler (nach Entity-ID), Laden der Animationen und Effekte an den Marken.
 * Animationen kommen aus der Mod (assets/kubyrematch/rematch/anim) und zusätzlich aus dem Ordner
 * {@code kuby_rematch/anim} im Spielordner (zum Ausprobieren ohne neuen Build, /kanim reload).
 */
public final class ClientAnims {
    private static ClipLibrary lib;
    private static PartSolver solver;
    private static final Map<Integer, Animator> animators = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> lastUpdate = new ConcurrentHashMap<>();
    private static final Map<Integer, Display.BlockDisplay> balls = new ConcurrentHashMap<>();
    private static final Map<Integer, Vec3> ballLast = new ConcurrentHashMap<>();
    private static double timeScale = 1.0;
    public static boolean effects = true;
    /** Fortbewegung (Stehen/Laufen/Sprinten der Mod) für alle sichtbaren Spieler. */
    public static boolean locomotion = true;
    private static final Map<Integer, double[]> motion = new ConcurrentHashMap<>(); // x, z, bodyRot, nanos

    private ClientAnims() { }

    public static ClipLibrary library() {
        if (lib == null) reload();
        return lib;
    }

    public static PartSolver solver() {
        if (solver == null) reload();
        return solver;
    }

    public static void setTimeScale(double s) { timeScale = Math.max(0.02, Math.min(4, s)); }

    public static double timeScale() { return timeScale; }

    /** Lädt Gerüst und Animationen neu. Gibt die Anzahl der Animationen zurück. */
    public static synchronized int reload() {
        Optional<Path> base = FabricLoader.getInstance().getModContainer(KubyRematch.MOD_ID)
                .flatMap(c -> c.findPath("assets/kubyrematch/rematch"));
        try {
            Rig rig;
            try (Reader r = Files.newBufferedReader(base.orElseThrow().resolve("rig.json"), StandardCharsets.UTF_8)) {
                rig = Rig.read(r);
            }
            List<Clip> clips = new ArrayList<>();
            readDir(base.get().resolve("anim"), rig, clips);
            readDir(FabricLoader.getInstance().getGameDir().resolve("kuby_rematch").resolve("anim"), rig, clips);
            if (lib == null) lib = new ClipLibrary(rig);
            lib.replaceAll(rig, clips);
            solver = new PartSolver(rig);
            animators.clear();
            motion.clear();
            KubyRematch.LOG.info("{} Animationen geladen: {}", clips.size(), lib.names());
            return clips.size();
        } catch (Exception e) {
            KubyRematch.LOG.error("Animationen konnten nicht geladen werden", e);
            if (lib == null) {
                lib = new ClipLibrary(new Rig(List.of("full_body"), java.util.Arrays.asList((String) null), List.of(new double[3])));
                solver = new PartSolver(lib.rig());
            }
            return 0;
        }
    }

    private static void readDir(Path dir, Rig rig, List<Clip> out) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    Clip c = Clip.read(r, rig);
                    out.removeIf(o -> o.name.equals(c.name)); // Ordner im Spiel überschreibt die Mod
                    out.add(c);
                } catch (Exception e) {
                    KubyRematch.LOG.warn("Animation {} fehlerhaft: {}", p.getFileName(), e.toString());
                }
            }
        }
    }

    /** Animator der Figur, auf die aktuelle Zeit gebracht; null, wenn sie keinen hat. */
    public static Animator animator(int entityId) {
        Animator a = animators.get(entityId);
        if (a == null) return null;
        long now = System.nanoTime();
        Long last = lastUpdate.put(entityId, now);
        Minecraft mc = Minecraft.getInstance();
        if (last != null && !mc.isPaused()) {
            double dt = Math.min(0.1, (now - last) / 1e9) * timeScale;
            a.update(dt);
        }
        updateBall(entityId, a);
        speedlineHint(entityId, a);
        return a;
    }

    /**
     * Animator einer Spielerfigur für dieses Bild: legt ihn bei aktiver Fortbewegung an, füttert Tempo, Drehung,
     * Boden und Blick aus dem Render-Zustand und bringt ihn auf die aktuelle Zeit.
     */
    public static Animator animatorFor(net.minecraft.client.renderer.entity.state.AvatarRenderState st) {
        int id = st.id;
        Animator a = animators.get(id);
        if (a == null && locomotion) a = create(id);
        if (a == null) return null;
        if (a.locomotion() == null && locomotion) attachLocomotion(a);
        if (a.locomotion() != null) {
            long now = System.nanoTime();
            double[] m = motion.get(id);
            double speed = 0, turn = 0;
            if (m != null) {
                double dt = (now - m[3]) / 1e9;
                if (dt < 1e-3) { speed = m[4]; turn = m[5]; }
                else if (dt < 0.5) {
                    speed = Math.hypot(st.x - m[0], st.z - m[1]) / dt;
                    turn = Mth.wrapDegrees(st.bodyRot - m[2]) / dt;
                }
            }
            if (m == null || (now - m[3]) / 1e9 >= 1e-3) motion.put(id, new double[]{st.x, st.z, st.bodyRot, now, speed, turn});
            Minecraft mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            boolean grounded = e == null || e.onGround();
            a.locomotion().input(speed, turn, grounded, st.yRot, st.xRot);
            if (mc.player != null && id == mc.player.getId() && speed > 5.0) de.kuby.rematch.client.fx.ScreenFx.speedlines(0.25);
            a.locomotionActive(locomotion && !st.isCrouching && !st.isVisuallySwimming && !st.isFallFlying
                    && !st.isPassenger && st.swimAmount < 0.01 && !st.hasPose(net.minecraft.world.entity.Pose.SLEEPING));
        }
        return animator(id);
    }

    private static Animator create(int id) {
        return animators.computeIfAbsent(id, i -> {
            Animator n = new Animator(library());
            n.setListener((an, c, ev) -> onEvent(i, an, c, ev));
            if (locomotion) attachLocomotion(n);
            return n;
        });
    }

    private static void attachLocomotion(Animator a) {
        de.kuby.rematch.anim.Locomotion l = new de.kuby.rematch.anim.Locomotion(library(), "stehen", "luft", "gehen", "laufen", "sprinten");
        if (l.usable()) a.setLocomotion(l);
    }

    /** Animator anlegen (falls nötig) und eine Animation starten. */
    public static boolean play(int entityId, String clip, double speed, boolean force) {
        return create(entityId).play(clip, speed, force);
    }

    public static void stop(int entityId) {
        Animator a = animators.get(entityId);
        if (a != null) a.stop();
    }

    public static void setLocomotion(boolean on) {
        locomotion = on;
        if (!on) animators.values().forEach(a -> a.locomotionActive(false));
    }

    public static void clear() {
        motion.clear();
        animators.clear();
        lastUpdate.clear();
        balls.values().forEach(Entity::discard);
        balls.clear();
    }

    // ---- Effekte ----

    private static void onEvent(int id, Animator a, Clip clip, String event) {
        if (!effects) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (event.equals("kontakt") || event.startsWith("kontakt_")) {
            boolean hard = !event.equals("kontakt_leicht");
            double freeze = hard ? 0.09 : 0.04;
            a.hitStop(freeze);
            Vec3 p = worldPoint(id, a, "ball");
            if (p == null) return;
            mc.level.playLocalSound(p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.75f, false);
            if (hard) mc.level.playLocalSound(p.x, p.y, p.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6f, 1.4f, false);
            de.kuby.rematch.client.fx.Fx.contact(mc, id, p, shotDirection(id), hard);
            if (hard) de.kuby.rematch.client.fx.ScreenFx.impact(p, 1.0, freeze);
            CameraShake.kick(hard ? 0.6 : 0.25);
        } else if (event.startsWith("sound:")) {
            // Blockbench-Sound-Spuren: Name wie minecraft:entity.player.attack.strong (noch nicht ausgewertet)
        }
    }

    /** Blickrichtung des Körpers (waagerecht) – Richtung, in die geschossen wird. */
    static Vec3 shotDirection(int id) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        float yaw = e instanceof LivingEntity le ? le.yBodyRot : 0;
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    /** Speedlines beim Ausholen bis zum Kontakt (nur für Figuren nahe der Kamera). */
    private static void speedlineHint(int id, Animator a) {
        if (a.action() == null) return;
        double e = a.action().clip.eventTime("kontakt");
        if (e <= 0 || !a.action().clip.name.contains("hart")) return;
        Minecraft mc = Minecraft.getInstance();
        Entity ent = mc.level == null ? null : mc.level.getEntity(id);
        if (ent == null || ent.position().distanceTo(mc.gameRenderer.getMainCamera().position()) > 10) return;
        double x = a.action().time - e;
        double v = x < 0 ? de.kuby.rematch.anim.Easing.IN_QUAD.apply(Math.max(0, 1 + x / 0.4)) : Math.max(0, 1 - x / 0.35);
        de.kuby.rematch.client.fx.ScreenFx.speedlines(0.85 * v);
    }

    /** Punkt eines Knochens (Drehpunkt) in Weltkoordinaten. */
    public static Vec3 worldPoint(int entityId, Animator a, String bone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof LivingEntity le)) return null;
        double[] m = solver().bonePoint(a.pose(), bone);
        if (m == null) return null;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float yaw = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
        return toWorld(le.getPosition(pt), yaw, m[0], m[1], m[2]);
    }

    /** Minecraft-Modellraum (Pixel, y nach unten, Füße bei 24) → Welt. Wie LivingEntityRenderer. */
    static Vec3 toWorld(Vec3 pos, float bodyYaw, double x, double y, double z) {
        double k = 0.9375; // AvatarRenderer.scale
        double lx = -k * x / 16.0, ly = k * (1.501 - y / 16.0), lz = k * z / 16.0;
        double r = Math.toRadians(180 - bodyYaw);
        double c = Math.cos(r), s = Math.sin(r);
        // Drehung um Y (wie Axis.YP.rotationDegrees)
        double wx = c * lx + s * lz, wz = -s * lx + c * lz;
        return new Vec3(pos.x + wx, pos.y + ly, pos.z + wz);
    }

    // ---- Probeball (Block-Display, folgt dem Knochen "ball", solange die Animation ihn bewegt) ----

    private static void updateBall(int id, Animator a) {
        Minecraft mc = Minecraft.getInstance();
        boolean show = mc.level != null && a.active() && a.action() != null && a.action().clip.hasBone(a.rig().index("ball"));
        Display.BlockDisplay ball = balls.get(id);
        if (!show) {
            if (ball != null) { ball.discard(); balls.remove(id); }
            ballLast.remove(id);
            return;
        }
        if (ball == null || ball.isRemoved() || ball.level() != mc.level) {
            ball = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, mc.level);
            ball.setBlockState(Blocks.WHITE_CONCRETE.defaultBlockState());
            ball.setViewRange(4f);
            mc.level.addEntity(ball);
            balls.put(id, ball);
        }
        Pose pose = a.pose();
        Vec3 p = worldPoint(id, a, "ball");
        if (p == null) return;
        int bi = a.rig().index("ball");
        Entity owner = mc.level.getEntity(id);
        float yaw = owner instanceof LivingEntity le ? Mth.rotLerp(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), le.yBodyRotO, le.yBodyRot) : 0;
        // Drehung des Balls: Blockbench-Drehung gespiegelt in den Minecraft-Raum, dann Körperdrehung
        Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(180 - yaw))
                .mul(new Quaternionf().rotationZYX((float) Math.toRadians(pose.rot[bi][2]),
                        (float) -Math.toRadians(pose.rot[bi][1]), (float) -Math.toRadians(pose.rot[bi][0])));
        float s = 5f / 16f;
        Vector3f center = q.transform(new Vector3f(s / 2, s / 2, s / 2)).negate();
        ball.setTransformation(new Transformation(center, q, new Vector3f(s, s, s), new Quaternionf()));
        Vec3 prev = ballLast.put(id, p);
        if (prev != null && prev.distanceTo(p) > 0.12 && a.action().time > a.action().clip.eventTime("kontakt") && !a.frozen())
            de.kuby.rematch.client.fx.Fx.trail(mc, prev, p);
        ball.setTransformationInterpolationDuration(0);
        ball.snapTo(p.x, p.y, p.z);
    }
}
