package net.evarius.terranexus.phone.messenger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

public record MessengerConversation(String id, String type, String name, String ownerId, List<String> members,
                                    List<MessengerMessage> messages, Map<String, Long> lastReadAt, long createdAt) {
    public static final String DIRECT = "direct", GROUP = "group";
    public static final Codec<MessengerConversation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(MessengerConversation::id),
            Codec.STRING.fieldOf("type").forGetter(MessengerConversation::type),
            Codec.STRING.optionalFieldOf("name", "").forGetter(MessengerConversation::name),
            Codec.STRING.optionalFieldOf("owner_id", "").forGetter(MessengerConversation::ownerId),
            Codec.STRING.listOf().fieldOf("members").forGetter(MessengerConversation::members),
            MessengerMessage.CODEC.listOf().optionalFieldOf("messages", List.of()).forGetter(MessengerConversation::messages),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("last_read_at", Map.of()).forGetter(MessengerConversation::lastReadAt),
            Codec.LONG.fieldOf("created_at").forGetter(MessengerConversation::createdAt)
    ).apply(instance, MessengerConversation::new));
    public boolean group() { return GROUP.equals(type); }
}
