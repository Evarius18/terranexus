package net.evarius.terranexus.network.gui;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record OpenGuiPayload(String sessionToken, String title, List<GuiMenuElement> elements) implements CustomPayload {
    public static final Id<OpenGuiPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "open_custom_gui"));
    public static final PacketCodec<RegistryByteBuf, OpenGuiPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), OpenGuiPayload::sessionToken,
            PacketCodecs.string(160), OpenGuiPayload::title,
            GuiMenuElement.CODEC.collect(PacketCodecs.toList(54)), OpenGuiPayload::elements,
            OpenGuiPayload::new);

    public OpenGuiPayload {
        elements = List.copyOf(elements);
    }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
