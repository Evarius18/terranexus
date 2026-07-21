package net.evarius.terranexus.network;

import net.evarius.terranexus.TerraNexus;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record UndoSurveyPointPayload() implements CustomPayload {
    public static final UndoSurveyPointPayload INSTANCE = new UndoSurveyPointPayload();
    public static final Id<UndoSurveyPointPayload> ID = new Id<>(Identifier.of(TerraNexus.MOD_ID, "undo_survey_point"));
    public static final PacketCodec<RegistryByteBuf, UndoSurveyPointPayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
