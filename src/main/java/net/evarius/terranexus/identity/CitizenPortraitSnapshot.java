package net.evarius.terranexus.identity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/** Immutable skin reference captured when an identity card is issued. */
public record CitizenPortraitSnapshot(UUID profileId, String textureValue, String textureSignature) {
    private static final String PROFILE_ID = "portrait_profile_uuid";
    private static final String TEXTURE_VALUE = "portrait_texture_value";
    private static final String TEXTURE_SIGNATURE = "portrait_texture_signature";

    public CitizenPortraitSnapshot {
        textureValue = textureValue == null ? "" : textureValue;
        textureSignature = textureSignature == null ? "" : textureSignature;
    }

    public static CitizenPortraitSnapshot capture(GameProfile profile) {
        Property texture = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        return new CitizenPortraitSnapshot(profile.getId(), texture == null ? "" : texture.value(),
                texture == null || !texture.hasSignature() ? "" : texture.signature());
    }

    public static CitizenPortraitSnapshot read(NbtCompound data) {
        String profile = data.getString(PROFILE_ID, "");
        try {
            return new CitizenPortraitSnapshot(UUID.fromString(profile),
                    data.getString(TEXTURE_VALUE, ""), data.getString(TEXTURE_SIGNATURE, ""));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void write(NbtCompound data) {
        data.putString(PROFILE_ID, profileId.toString());
        data.putString(TEXTURE_VALUE, textureValue);
        if (!textureSignature.isBlank()) data.putString(TEXTURE_SIGNATURE, textureSignature);
    }

    public String wireValue() {
        return profileId + "|" + textureValue + "|" + textureSignature;
    }

    public static CitizenPortraitSnapshot fromWireValue(String value) {
        if (value == null) return null;
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3) return null;
        try { return new CitizenPortraitSnapshot(UUID.fromString(parts[0]), parts[1], parts[2]); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
