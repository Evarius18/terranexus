package net.evarius.terranexus.network.gui;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SearchActionPayload(String token, String action, String query) implements CustomPayload {
    public static final Id<SearchActionPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "search_action"));
    public static final PacketCodec<RegistryByteBuf, SearchActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), SearchActionPayload::token,
            PacketCodecs.string(16), SearchActionPayload::action,
            PacketCodecs.string(96), SearchActionPayload::query,
            SearchActionPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
