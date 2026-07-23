package net.evarius.terranexus.network.gui;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenSearchPayload(String token, String title, String placeholder, String initialValue,
                                int minimumLength, int maximumLength) implements CustomPayload {
    public static final Id<OpenSearchPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "open_search"));
    public static final PacketCodec<RegistryByteBuf, OpenSearchPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), OpenSearchPayload::token,
            PacketCodecs.string(160), OpenSearchPayload::title,
            PacketCodecs.string(256), OpenSearchPayload::placeholder,
            PacketCodecs.string(96), OpenSearchPayload::initialValue,
            PacketCodecs.VAR_INT, OpenSearchPayload::minimumLength,
            PacketCodecs.VAR_INT, OpenSearchPayload::maximumLength,
            OpenSearchPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
