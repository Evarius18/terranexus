package net.evarius.terranexus.landlord;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LandLease(String propertyId, String landlordAccount, String tenantId, long rent, long deposit,
                        int periodDays, long nextDueAt, int missedPayments, boolean active,
                        int termPayments, int paymentsCompleted, boolean autoRenew,
                        long startedAt, long endsAt, String depositAccount) {
    public static final Codec<LandLease> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("property_id").forGetter(LandLease::propertyId),
            Codec.STRING.fieldOf("landlord_account").forGetter(LandLease::landlordAccount),
            Codec.STRING.fieldOf("tenant_id").forGetter(LandLease::tenantId),
            Codec.LONG.fieldOf("rent").forGetter(LandLease::rent),
            Codec.LONG.fieldOf("deposit").forGetter(LandLease::deposit),
            Codec.INT.fieldOf("period_days").forGetter(LandLease::periodDays),
            Codec.LONG.fieldOf("next_due_at").forGetter(LandLease::nextDueAt),
            Codec.INT.fieldOf("missed_payments").forGetter(LandLease::missedPayments),
            Codec.BOOL.fieldOf("active").forGetter(LandLease::active),
            Codec.INT.optionalFieldOf("term_payments", 0).forGetter(LandLease::termPayments),
            Codec.INT.optionalFieldOf("payments_completed", 0).forGetter(LandLease::paymentsCompleted),
            Codec.BOOL.optionalFieldOf("auto_renew", true).forGetter(LandLease::autoRenew),
            Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(LandLease::startedAt),
            Codec.LONG.optionalFieldOf("ends_at", 0L).forGetter(LandLease::endsAt),
            Codec.STRING.optionalFieldOf("deposit_account", "").forGetter(LandLease::depositAccount)
    ).apply(instance, LandLease::new));

    public LandLease(String propertyId, String landlordAccount, String tenantId, long rent, long deposit,
                     int periodDays, long nextDueAt, int missedPayments, boolean active) {
        this(propertyId, landlordAccount, tenantId, rent, deposit, periodDays, nextDueAt, missedPayments,
                active, 0, 0, true, 0L, 0L, landlordAccount);
    }

    public static LandLease offer(String propertyId, String landlordAccount, String tenantId, long rent,
                                  long deposit, int periodDays, int termPayments, boolean autoRenew) {
        return new LandLease(propertyId, landlordAccount, tenantId, rent, deposit, Math.max(1, periodDays),
                0L, 0, false, Math.max(0, termPayments), 0, autoRenew, 0L, 0L, "");
    }

    public LandLease activate(long now, String escrowAccount) {
        long period = LandManagementState.periodMillis(periodDays);
        long end = termPayments <= 0 ? 0L : safeAdd(now, safeMultiply(period, termPayments));
        return new LandLease(propertyId, landlordAccount, tenantId, rent, deposit, periodDays,
                safeAdd(now, period), 0, true, termPayments, 0, autoRenew, now, end, escrowAccount);
    }

    public LandLease afterSuccessfulPayment(long now) {
        int completed = paymentsCompleted + 1;
        if (termPayments > 0 && completed >= termPayments && autoRenew) completed = 0;
        long period = LandManagementState.periodMillis(periodDays);
        long newEnd = termPayments > 0 && completed == 0 && autoRenew
                ? safeAdd(now, safeMultiply(period, termPayments)) : endsAt;
        return new LandLease(propertyId, landlordAccount, tenantId, rent, deposit, periodDays,
                safeAdd(now, period), 0, true, termPayments, completed, autoRenew, startedAt, newEnd, depositAccount);
    }

    public LandLease afterFailedPayment(long now) {
        return new LandLease(propertyId, landlordAccount, tenantId, rent, deposit, periodDays,
                safeAdd(now, LandManagementState.periodMillis(periodDays)), missedPayments + 1, true,
                termPayments, paymentsCompleted, autoRenew, startedAt, endsAt, depositAccount);
    }

    public boolean termCompletedAfterNextPayment() {
        return termPayments > 0 && paymentsCompleted + 1 >= termPayments && !autoRenew;
    }

    private static long safeAdd(long left, long right) {
        try { return Math.addExact(left, right); } catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }
    private static long safeMultiply(long left, int right) {
        try { return Math.multiplyExact(left, right); } catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }
}
