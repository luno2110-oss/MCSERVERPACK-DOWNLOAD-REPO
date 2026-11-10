package de.kuby.rematch;

import de.kuby.rematch.net.HelloPayload;
import de.kuby.rematch.net.WelcomePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Gemeinsamer Einstieg (Client und Server): registriert die Netzwerk-Pakete. */
public final class KubyRematch implements ModInitializer {
    public static final String MOD_ID = "kubyrematch";
    /** Protokoll-Version des Handshakes. Hochzählen, wenn sich Pakete inkompatibel ändern (muss zu KubyCore passen). */
    public static final int PROTOCOL = 1;
    public static final Logger LOG = LoggerFactory.getLogger("KubyRematch");

    public static String modVersion() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WelcomePayload.TYPE, WelcomePayload.CODEC);
        LOG.info("Kuby Rematch {} geladen (Protokoll {})", modVersion(), PROTOCOL);
    }
}
