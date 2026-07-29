package net.evarius.terranexus.network.phone;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PhoneStatePayload(String json) implements CustomPayload {
    public static final Id<PhoneStatePayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "phone_state"));
    public static final PacketCodec<RegistryByteBuf, PhoneStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(65535), PhoneStatePayload::json, PhoneStatePayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
