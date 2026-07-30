package net.evarius.terranexus.network.phone;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PhoneActionPayload(String action, String value, String secondaryValue) implements CustomPayload {
    public static final Id<PhoneActionPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "phone_action"));
    public static final PacketCodec<RegistryByteBuf, PhoneActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32), PhoneActionPayload::action,
            PacketCodecs.string(128), PhoneActionPayload::value,
            PacketCodecs.string(128), PhoneActionPayload::secondaryValue,
            PhoneActionPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
