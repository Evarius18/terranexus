package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LandTransferRequest(
        String id,
        String propertyId,
        String oldOwnerType,
        String oldOwnerId,
        String newOwnerType,
        String newOwnerId,
        String initiatorId,
        boolean ownerApproved,
        boolean recipientApproved,
        boolean institutionApproved,
        long createdAt
) {
    public static final Codec<LandTransferRequest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(LandTransferRequest::id),
            Codec.STRING.fieldOf("property_id").forGetter(LandTransferRequest::propertyId),
            Codec.STRING.fieldOf("old_owner_type").forGetter(LandTransferRequest::oldOwnerType),
            Codec.STRING.fieldOf("old_owner_id").forGetter(LandTransferRequest::oldOwnerId),
            Codec.STRING.fieldOf("new_owner_type").forGetter(LandTransferRequest::newOwnerType),
            Codec.STRING.fieldOf("new_owner_id").forGetter(LandTransferRequest::newOwnerId),
            Codec.STRING.fieldOf("initiator_id").forGetter(LandTransferRequest::initiatorId),
            Codec.BOOL.optionalFieldOf("owner_approved", false).forGetter(LandTransferRequest::ownerApproved),
            Codec.BOOL.optionalFieldOf("recipient_approved", false).forGetter(LandTransferRequest::recipientApproved),
            Codec.BOOL.optionalFieldOf("institution_approved", false).forGetter(LandTransferRequest::institutionApproved),
            Codec.LONG.fieldOf("created_at").forGetter(LandTransferRequest::createdAt)
    ).apply(instance, LandTransferRequest::new));

    public LandTransferRequest withApprovals(boolean owner, boolean recipient, boolean institution) {
        return new LandTransferRequest(id, propertyId, oldOwnerType, oldOwnerId, newOwnerType, newOwnerId,
                initiatorId, owner, recipient, institution, createdAt);
    }
}
