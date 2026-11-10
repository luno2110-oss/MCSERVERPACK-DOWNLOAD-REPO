package de.kuby.rematch.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → Server, Kanal {@code kuby:rematch_hello}: „Ich habe die Mod.“
 * Aufbau: VarInt Protokoll, String Mod-Version (VarInt-Länge + UTF-8). KubyCore liest genau das.
 */
public record HelloPayload(int protocol, String modVersion) implements CustomPacketPayload {
    public static final Type<HelloPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("kuby", "rematch_hello"));
    public static final StreamCodec<ByteBuf, HelloPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HelloPayload::protocol,
            ByteBufCodecs.stringUtf8(64), HelloPayload::modVersion,
            HelloPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
