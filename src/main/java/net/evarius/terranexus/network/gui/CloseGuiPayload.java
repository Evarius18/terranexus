package net.evarius.terranexus.network.gui;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CloseGuiPayload(String sessionToken) implements CustomPayload {
    public static final Id<CloseGuiPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "close_custom_gui"));
    public static final PacketCodec<RegistryByteBuf, CloseGuiPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), CloseGuiPayload::sessionToken, CloseGuiPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
