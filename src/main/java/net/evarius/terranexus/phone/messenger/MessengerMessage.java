package net.evarius.terranexus.phone.messenger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record MessengerMessage(String id, String senderId, String body, long sentAt, long expiresAt) {
    public static final Codec<MessengerMessage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(MessengerMessage::id),
            Codec.STRING.fieldOf("sender_id").forGetter(MessengerMessage::senderId),
            Codec.STRING.fieldOf("body").forGetter(MessengerMessage::body),
            Codec.LONG.fieldOf("sent_at").forGetter(MessengerMessage::sentAt),
            Codec.LONG.fieldOf("expires_at").forGetter(MessengerMessage::expiresAt)
    ).apply(instance, MessengerMessage::new));
}
