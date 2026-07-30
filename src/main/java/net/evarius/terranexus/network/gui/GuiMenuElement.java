package net.evarius.terranexus.network.gui;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

public record GuiMenuElement(int id, String icon, Text label, Text tooltip,
                             boolean enabled, boolean selected) {
    public static final PacketCodec<RegistryByteBuf, GuiMenuElement> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, GuiMenuElement::id,
            PacketCodecs.string(32), GuiMenuElement::icon,
            TextCodecs.REGISTRY_PACKET_CODEC, GuiMenuElement::label,
            TextCodecs.REGISTRY_PACKET_CODEC, GuiMenuElement::tooltip,
            PacketCodecs.BOOLEAN, GuiMenuElement::enabled,
            PacketCodecs.BOOLEAN, GuiMenuElement::selected,
            GuiMenuElement::new);

    public GuiIcon resolvedIcon() {
        try { return GuiIcon.valueOf(icon); }
        catch (IllegalArgumentException ignored) { return GuiIcon.HOME; }
    }
}
