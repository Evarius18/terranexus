package net.evarius.terranexus.network.gui;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GuiTextActionPayload(String sessionToken, int elementId, String value) implements CustomPayload {
    public static final Id<GuiTextActionPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "custom_gui_text_action"));
    public static final PacketCodec<RegistryByteBuf, GuiTextActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), GuiTextActionPayload::sessionToken,
            PacketCodecs.VAR_INT, GuiTextActionPayload::elementId,
            PacketCodecs.string(2000), GuiTextActionPayload::value,
            GuiTextActionPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
