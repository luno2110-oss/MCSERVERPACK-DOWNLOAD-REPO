package de.kuby.rematch.client.fx;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import de.kuby.rematch.KubyRematch;
import de.kuby.rematch.mixin.PostChainAccessor;
import de.kuby.rematch.mixin.PostPassAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Bildschirm-Effekte über einen eigenen Nachbearbeitungs-Shader (assets/kubyrematch/post_effect/impact.json):
 * Schockwelle, Zoom-Unschärfe mit Farbsäumen, Aufglühen, Blitz, Treffer-Bild, Speedlines, Vignette.
 * Läuft nur, solange ein Effekt sichtbar ist. Werte werden jedes Bild in den Uniform-Block "KubyFx" geschrieben.
 */
public final class ScreenFx {
    private static final Identifier CHAIN = Identifier.fromNamespaceAndPath(KubyRematch.MOD_ID, "impact");
    private static final Map<PostPass, GpuBuffer> buffers = new WeakHashMap<>();
    private static final ByteBuffer data = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
    public static boolean enabled = true;

    private static double clock, impactAt = -100, power, cx = 0.5, cy = 0.5, freeze;
    private static double speedTarget, speed;
    private static long last = System.nanoTime();
    private static boolean failed;

    private ScreenFx() { }

    /** Harter Treffer an einem Weltpunkt; power 0..1; freeze = Dauer des Hit-Stops (für das Treffer-Bild). */
    public static void impact(Vec3 world, double power, double freeze) {
        ScreenFx.impactAt = clock;
        ScreenFx.power = power;
        ScreenFx.freeze = freeze;
        double[] uv = project(world);
        cx = uv[0];
        cy = uv[1];
    }

    /** Wunschstärke der Speedlines für dieses Bild (wird weich angefahren). */
    public static void speedlines(double target) { speedTarget = Math.max(speedTarget, target); }

    /** Weltpunkt → Bildschirm (0..1, Ursprung unten links wie die Textur). Hinter der Kamera: Mitte. */
    public static double[] project(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 v = p.subtract(cam.position());
        Vector3fc f = cam.forwardVector(), u = cam.upVector(), l = cam.leftVector();
        double z = v.x * f.x() + v.y * f.y() + v.z * f.z();
        if (z < 0.05) return new double[]{0.5, 0.5};
        double x = -(v.x * l.x() + v.y * l.y() + v.z * l.z());
        double y = v.x * u.x() + v.y * u.y() + v.z * u.z();
        double th = Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        return new double[]{0.5 + 0.5 * x / (z * th * aspect), 0.5 + 0.5 * y / (z * th)};
    }

    /** Nach dem Zeichnen der Welt aufgerufen (GameRendererMixin). */
    public static void render(GraphicsResourceAllocator pool) {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9) * de.kuby.rematch.client.anim.ClientAnims.timeScale();
        last = now;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isPaused()) clock += dt;
        speed += (speedTarget - speed) * (1 - Math.exp(-dt * 10));
        speedTarget = 0;
        double t = clock - impactAt;
        double s = t < 0 ? 0 : power * Math.exp(-t * 6.5);
        double flash = t < 0 ? 0 : power * Math.max(0, 1 - t / 0.13);
        double frame = t < 0 ? 0 : power >= 0.8 ? (t < freeze ? 1 : Math.max(0, 1 - (t - freeze) / 0.04)) : 0;
        double wave = t < 0.6 ? power : 0;
        double vig = Math.max(s * 0.9, speed * 0.7);
        if (!enabled || failed || (s < 0.01 && flash <= 0 && speed < 0.01 && wave <= 0)) return;
        try {
            PostChain chain = mc.getShaderManager().getPostChain(CHAIN, Set.of(PostChain.MAIN_TARGET_ID));
            if (chain == null) return;
            data.clear();
            put(s, t, cx, cy);
            put(speed, frame, flash, vig);
            put(clock, (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight()), wave, 0);
            data.flip();
            for (PostPass pass : ((PostChainAccessor) chain).kuby$passes()) {
                PostPassAccessor acc = (PostPassAccessor) pass;
                if (!acc.kuby$uniforms().containsKey("KubyFx")) continue;
                GpuBuffer buf = buffers.get(pass);
                if (buf == null) {
                    buf = RenderSystem.getDevice().createBuffer(() -> "Kuby Fx", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 48);
                    Map<String, GpuBuffer> m = new HashMap<>(acc.kuby$uniforms());
                    m.put("KubyFx", buf);
                    acc.kuby$setUniforms(m);
                    buffers.put(pass, buf);
                }
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buf.slice(), data);
                data.rewind();
            }
            chain.process(mc.getMainRenderTarget(), pool);
        } catch (Throwable e) {
            failed = true;
            KubyRematch.LOG.error("Bildeffekte abgeschaltet (Shader-Fehler)", e);
        }
    }

    private static void put(double a, double b, double c, double d) {
        data.putFloat((float) a).putFloat((float) b).putFloat((float) c).putFloat((float) d);
    }
}
