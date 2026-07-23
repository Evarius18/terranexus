package net.evarius.terranexus.network.gui;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GuiActionPayload(String sessionToken, String action, int elementId) implements CustomPayload {
    public static final Id<GuiActionPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "custom_gui_action"));
    public static final PacketCodec<RegistryByteBuf, GuiActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), GuiActionPayload::sessionToken,
            PacketCodecs.string(32), GuiActionPayload::action,
            PacketCodecs.VAR_INT, GuiActionPayload::elementId,
            GuiActionPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
