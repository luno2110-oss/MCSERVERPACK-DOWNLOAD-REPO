package de.kuby.kubyplugins.bedrock;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Kamera für Bedrock-Spieler in Kino-Szenen (Rundgang, Cutscenes). Bedrock kann nicht durch ein anderes Objekt schauen
 * (Java: Zuschauer-Ziel), im echten Test sah man sich deshalb nur von hinten. Geyser hat dafür eine Kamera-Schnittstelle
 * (Bedrock-Kamerabefehle): Position und Blickrichtung mit weichem Übergang setzen, am Ende zurück zur eigenen Sicht.
 * Über Reflexion, damit es ohne Geyser (oder mit älterem Geyser) einfach nichts tut; dann teleportiert die Kino-Engine
 * den Spieler selbst an die Kamera.
 */
public final class BedrockCamera {

    private static Boolean available;

    private BedrockCamera() {
    }

    private static Object camera(UUID id) throws Exception {
        Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
        Object a = api.getMethod("api").invoke(null);
        Object conn = a.getClass().getMethod("connectionByUuid", UUID.class).invoke(a, id);
        if (conn == null) return null;
        return conn.getClass().getMethod("camera").invoke(conn);
    }

    /** Kamera an (x, y, z) mit Blick yaw/pitch, Übergang in easeSeconds. false: geht nicht (dann selbst teleportieren). */
    public static boolean set(UUID id, double x, double y, double z, float yaw, float pitch, float easeSeconds) {
        if (Boolean.FALSE.equals(available)) return false;
        try {
            Object cam = camera(id);
            if (cam == null) return false;
            Class<?> posC = Class.forName("org.geysermc.geyser.api.bedrock.camera.CameraPosition");
            Object b = posC.getMethod("builder").invoke(null);
            Class<?> vec = Class.forName("org.cloudburstmc.math.vector.Vector3f");
            Object v = vec.getMethod("from", float.class, float.class, float.class).invoke(null, (float) x, (float) y, (float) z);
            call(b, "position", vec, v);
            call(b, "rotationX", int.class, Math.round(pitch));
            call(b, "rotationY", int.class, Math.round(yaw));
            if (easeSeconds > 0) {
                Class<?> ease = Class.forName("org.geysermc.geyser.api.bedrock.camera.CameraEaseType");
                Object linear = ease.getField("LINEAR").get(null);
                call(b, "easeType", ease, linear);
                call(b, "easeSeconds", float.class, easeSeconds);
            }
            Object pos = b.getClass().getMethod("build").invoke(b);
            Method send = cam.getClass().getMethod("sendCameraPosition", posC);
            send.setAccessible(true);
            send.invoke(cam, pos);
            available = true;
            return true;
        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    /** Zurück zur normalen Sicht des Spielers. */
    public static void clear(UUID id) {
        try {
            Object cam = camera(id);
            if (cam != null) {
                Method m = cam.getClass().getMethod("clearCameraInstructions");
                m.setAccessible(true);
                m.invoke(cam);
            }
        } catch (Throwable ignored) {
            // ohne Geyser-Kamera gab es nichts zurückzusetzen
        }
    }

    private static void call(Object o, String name, Class<?> type, Object value) throws Exception {
        Method m = o.getClass().getMethod(name, type);
        m.setAccessible(true);
        m.invoke(o, value);
    }
}
