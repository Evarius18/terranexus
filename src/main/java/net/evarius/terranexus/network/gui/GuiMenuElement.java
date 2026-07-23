package net.evarius.terranexus.network.gui;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

public record GuiMenuElement(int id, String icon, String label, String tooltip,
                             boolean enabled, boolean selected) {
    public static final PacketCodec<RegistryByteBuf, GuiMenuElement> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, GuiMenuElement::id,
            PacketCodecs.string(32), GuiMenuElement::icon,
            PacketCodecs.string(128), GuiMenuElement::label,
            PacketCodecs.string(512), GuiMenuElement::tooltip,
            PacketCodecs.BOOLEAN, GuiMenuElement::enabled,
            PacketCodecs.BOOLEAN, GuiMenuElement::selected,
            GuiMenuElement::new);

    public GuiIcon resolvedIcon() {
        try { return GuiIcon.valueOf(icon); }
        catch (IllegalArgumentException ignored) { return GuiIcon.HOME; }
    }
}
