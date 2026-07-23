package net.evarius.terranexus.network.gui;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SearchStatusPayload(String token, String state, String message) implements CustomPayload {
    public static final Id<SearchStatusPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "search_status"));
    public static final PacketCodec<RegistryByteBuf, SearchStatusPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), SearchStatusPayload::token,
            PacketCodecs.string(16), SearchStatusPayload::state,
            PacketCodecs.string(256), SearchStatusPayload::message,
            SearchStatusPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
