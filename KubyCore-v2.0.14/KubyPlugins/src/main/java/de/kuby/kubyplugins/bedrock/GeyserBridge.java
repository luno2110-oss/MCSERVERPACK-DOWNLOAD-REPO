package de.kuby.kubyplugins.bedrock;

import java.lang.reflect.Method;
import java.util.UUID;

/** Geyser-API über Reflexion (für Server mit Geyser, aber ohne Floodgate, z. B. auth-type online). */
final class GeyserBridge {

    private GeyserBridge() {
    }

    private static Object api() throws Exception {
        Class<?> c = Class.forName("org.geysermc.geyser.api.GeyserApi");
        return c.getMethod("api").invoke(null);
    }

    static boolean isBedrock(UUID id) throws Exception {
        Object api = api();
        Method m = api.getClass().getMethod("isBedrockPlayer", UUID.class);
        return (Boolean) m.invoke(api, id);
    }

    static boolean send(UUID id, Object form) throws Exception {
        Object api = api();
        Class<?> formClass = Class.forName("org.geysermc.cumulus.form.Form");
        Method m = api.getClass().getMethod("sendForm", UUID.class, formClass);
        return (Boolean) m.invoke(api, id, form);
    }
}
