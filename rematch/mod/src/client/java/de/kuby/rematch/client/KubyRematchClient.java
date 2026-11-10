package de.kuby.rematch.client;

import de.kuby.rematch.KubyRematch;
import de.kuby.rematch.net.HelloPayload;
import de.kuby.rematch.net.WelcomePayload;
import de.kuby.rematch.client.anim.ClientAnims;
import de.kuby.rematch.client.anim.KanimCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * Client-Einstieg. Schritt 1: Handshake mit KubyCore. Sobald der Server den Kanal {@code kuby:rematch_hello}
 * anmeldet (Paper schickt das beim Betreten), meldet sich die Mod mit Protokoll und Version.
 */
public final class KubyRematchClient implements ClientModInitializer {
    /** Ob der Server die Mod in dieser Verbindung bestätigt hat. Später Schalter für Kamera, Animationen usw. */
    private static volatile boolean serverReady;
    private static volatile boolean helloSent;

    public static boolean serverReady() { return serverReady; }

    @Override
    public void onInitializeClient() {
        KanimCommand.register();
        de.kuby.rematch.client.fx.Fx.registerParticles();
        de.kuby.rematch.client.anim.DemoMode.init();
        ClientPlayNetworking.registerGlobalReceiver(WelcomePayload.TYPE, (payload, ctx) -> {
            serverReady = payload.compatible();
            KubyRematch.LOG.info("Server antwortet: Protokoll {}, kompatibel {}", payload.protocol(), payload.compatible());
            if (!payload.message().isEmpty()) {
                ctx.client().gui.getChat().addMessage(Component.literal(payload.message()));
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> trySendHello());
        // Paper meldet seine Kanäle evtl. erst nach JOIN an: dann hier senden
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (channels.contains(HelloPayload.TYPE.id())) trySendHello();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { serverReady = false; helloSent = false; ClientAnims.clear(); });
    }

    private static void trySendHello() {
        if (helloSent || !ClientPlayNetworking.canSend(HelloPayload.TYPE)) return;
        helloSent = true;
        ClientPlayNetworking.send(new HelloPayload(KubyRematch.PROTOCOL, KubyRematch.modVersion()));
        KubyRematch.LOG.info("Handshake an den Server geschickt");
    }
}
