package de.kuby.kubyplugins.bedrock;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/** Nur laden, wenn Floodgate läuft (sonst fehlen die Klassen). */
final class FloodgateBridge {

    private FloodgateBridge() {
    }

    static boolean isBedrock(UUID id) {
        return FloodgateApi.getInstance().isFloodgatePlayer(id);
    }

    static boolean send(UUID id, org.geysermc.cumulus.form.Form form) {
        return FloodgateApi.getInstance().sendForm(id, form);
    }
}
