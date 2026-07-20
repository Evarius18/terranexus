package net.evarius.terranexus.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BankAccount(String accountKey, String accountNumber, long createdAt, boolean frozen) {
    public static final Codec<BankAccount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("account_key").forGetter(BankAccount::accountKey),
            Codec.STRING.fieldOf("account_number").forGetter(BankAccount::accountNumber),
            Codec.LONG.fieldOf("created_at").forGetter(BankAccount::createdAt),
            Codec.BOOL.optionalFieldOf("frozen", false).forGetter(BankAccount::frozen)
    ).apply(instance, BankAccount::new));

    public BankAccount withFrozen(boolean value) {
        return new BankAccount(accountKey, accountNumber, createdAt, value);
    }
}
