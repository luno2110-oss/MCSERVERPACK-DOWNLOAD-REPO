package de.kuby.rematch.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → Client, Kanal {@code kuby:rematch_welcome}: Antwort von KubyCore auf den Handshake.
 * Aufbau: VarInt Protokoll des Servers, Bool kompatibel, String Text für den Spieler.
 */
public record WelcomePayload(int protocol, boolean compatible, String message) implements CustomPacketPayload {
    public static final Type<WelcomePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("kuby", "rematch_welcome"));
    public static final StreamCodec<ByteBuf, WelcomePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WelcomePayload::protocol,
            ByteBufCodecs.BOOL, WelcomePayload::compatible,
            ByteBufCodecs.stringUtf8(256), WelcomePayload::message,
            WelcomePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
