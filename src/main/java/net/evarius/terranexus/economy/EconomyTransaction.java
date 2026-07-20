package net.evarius.terranexus.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record EconomyTransaction(String id, long timestamp, String sender, String recipient, long amount,
                                 String purpose, String actorUuid, String institutionId, String type,
                                 boolean successful, long senderBalance, long recipientBalance) {
    public static final Codec<EconomyTransaction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(EconomyTransaction::id),
            Codec.LONG.fieldOf("timestamp").forGetter(EconomyTransaction::timestamp),
            Codec.STRING.fieldOf("sender").forGetter(EconomyTransaction::sender),
            Codec.STRING.fieldOf("recipient").forGetter(EconomyTransaction::recipient),
            Codec.LONG.fieldOf("amount").forGetter(EconomyTransaction::amount),
            Codec.STRING.optionalFieldOf("purpose", "").forGetter(EconomyTransaction::purpose),
            Codec.STRING.optionalFieldOf("actor_uuid", "").forGetter(EconomyTransaction::actorUuid),
            Codec.STRING.optionalFieldOf("institution_id", "").forGetter(EconomyTransaction::institutionId),
            Codec.STRING.optionalFieldOf("type", "TRANSFER").forGetter(EconomyTransaction::type),
            Codec.BOOL.optionalFieldOf("successful", true).forGetter(EconomyTransaction::successful),
            Codec.LONG.optionalFieldOf("sender_balance", 0L).forGetter(EconomyTransaction::senderBalance),
            Codec.LONG.optionalFieldOf("recipient_balance", 0L).forGetter(EconomyTransaction::recipientBalance)
    ).apply(instance, EconomyTransaction::new));
}
