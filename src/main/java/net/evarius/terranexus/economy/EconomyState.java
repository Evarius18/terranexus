package net.evarius.terranexus.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.evarius.terranexus.TerraNexus;
import net.evarius.terranexus.config.ConfigManager;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyState extends PersistentState {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Codec<EconomyState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("balances", Map.of()).forGetter(state -> state.balances),
            Codec.unboundedMap(Codec.STRING, BankAccount.CODEC).optionalFieldOf("accounts", Map.of()).forGetter(state -> state.accounts),
            EconomyTransaction.CODEC.listOf().optionalFieldOf("transactions", List.of()).forGetter(state -> state.transactions)
    ).apply(instance, EconomyState::new));
    private static final PersistentStateType<EconomyState> TYPE =
            new PersistentStateType<>("terranexus_economy", EconomyState::new, CODEC, DataFixTypes.LEVEL);

    private final Map<String, Long> balances;
    private final Map<String, BankAccount> accounts;
    private final Map<String, String> accountNumberIndex;
    private final List<EconomyTransaction> transactions;
    private final Map<String, List<EconomyTransaction>> accountTransactions = new HashMap<>();
    private final Map<String, List<EconomyTransaction>> institutionTransactions = new HashMap<>();

    public EconomyState() { this(new HashMap<>(), new HashMap<>(), new ArrayList<>()); }
    private EconomyState(Map<String, Long> balances, Map<String, BankAccount> accounts,
                         List<EconomyTransaction> transactions) {
        this.balances = new HashMap<>(balances);
        this.accounts = new HashMap<>(accounts);
        this.accountNumberIndex = new HashMap<>();
        this.accounts.values().forEach(account -> accountNumberIndex.put(normalizeAccountNumber(account.accountNumber()), account.accountKey()));
        this.transactions = new ArrayList<>(transactions);
        this.transactions.forEach(this::indexTransaction);
    }

    public static EconomyState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public long balance(UUID owner) { return balance(playerAccount(owner)); }
    public long balance(String account) { return balances.getOrDefault(account, 0L); }
    public static String playerAccount(UUID owner) { return owner.toString(); }
    public static String institutionAccount(String id) { return "institution:" + id; }
    public static String areaAccount(String id) { return "area:" + id; }

    public BankAccount ensureAccount(String account) {
        BankAccount existing = accounts.get(account);
        if (existing != null) return existing;
        boolean newBalance = !balances.containsKey(account);
        BankAccount created = new BankAccount(account, createAccountNumber(), System.currentTimeMillis(), false);
        accounts.put(account, created);
        accountNumberIndex.put(normalizeAccountNumber(created.accountNumber()), account);
        long initialBalance = newBalance && isPlayerAccount(account) ? ConfigManager.economy().playerStartBalance : 0L;
        balances.putIfAbsent(account, initialBalance);
        markDirty();
        if (initialBalance > 0) record("SYSTEM", account, initialBalance, "Konfiguriertes Startguthaben", "SYSTEM", "",
                "START_BALANCE", true, 0, initialBalance);
        return created;
    }

    public BankAccount account(String account) { return ensureAccount(account); }
    public Collection<BankAccount> allAccounts() {
        balances.keySet().forEach(this::ensureAccount);
        return List.copyOf(accounts.values());
    }
    public BankAccount findByNumber(String number) {
        allAccounts();
        String accountKey = accountNumberIndex.get(normalizeAccountNumber(number));
        return accountKey == null ? null : accounts.get(accountKey);
    }
    public void setFrozen(String account, boolean frozen) {
        setFrozen(account, frozen, "", institutionFrom(account));
    }
    public void setFrozen(String account, boolean frozen, String actorUuid) {
        setFrozen(account, frozen, actorUuid, institutionFrom(account));
    }
    public void setFrozen(String account, boolean frozen, String actorUuid, String auditInstitutionId) {
        accounts.put(account, ensureAccount(account).withFrozen(frozen));
        record(account, account, 0, frozen ? "Konto gesperrt" : "Konto entsperrt", actorUuid,
                auditInstitutionId, frozen ? "ACCOUNT_FROZEN" : "ACCOUNT_UNFROZEN", true,
                balance(account), balance(account));
        markDirty();
    }
    public boolean isFrozen(String account) { return ensureAccount(account).frozen(); }

    public void deposit(UUID owner, long cents) { deposit(playerAccount(owner), cents); }
    public void deposit(String account, long cents) {
        if (!adjust(account, cents, "Administrative Einzahlung", "", institutionFrom(account), "DEPOSIT"))
            throw new IllegalArgumentException("Invalid deposit");
    }
    public boolean withdraw(UUID owner, long cents) {
        if (cents <= 0) return false;
        return adjust(playerAccount(owner), -cents, "Administrative Auszahlung", "", "", "WITHDRAWAL");
    }
    public boolean transfer(UUID from, UUID to, long cents) {
        return transfer(playerAccount(from), playerAccount(to), cents);
    }
    public boolean transfer(String from, String to, long cents) {
        return transfer(from, to, cents, "Überweisung", "", institutionFrom(from), "TRANSFER");
    }

    public boolean transfer(String from, String to, long cents, String purpose, String actorUuid,
                            String institutionId, String type) {
        ensureAccount(from);
        ensureAccount(to);
        long source = balance(from), target = balance(to);
        boolean regularTransfer = "TRANSFER".equals(type);
        long fee = regularTransfer ? transferFee(cents) : 0L;
        long debit;
        boolean arithmeticValid = true;
        try { debit = Math.addExact(cents, fee); }
        catch (ArithmeticException overflow) { debit = Long.MAX_VALUE; arithmeticValid = false; }
        boolean valid = arithmeticValid && cents > 0 && (!regularTransfer || cents <= ConfigManager.economy().maximumTransferAmount)
                && (!regularTransfer || !from.startsWith("system:") && !to.startsWith("system:"))
                && !from.equals(to) && !isFrozen(from) && source >= debit;
        long updatedTarget = target;
        String feeAccount = "system:fees";
        long updatedFees = fee > 0 ? balance(feeAccount) : 0L;
        if (valid) {
            try {
                updatedTarget = Math.addExact(target, cents);
                if (fee > 0) updatedFees = Math.addExact(updatedFees, fee);
            }
            catch (ArithmeticException overflow) { valid = false; }
        }
        if (valid) {
            balances.put(from, source - debit);
            balances.put(to, updatedTarget);
            if (fee > 0) {
                ensureAccount(feeAccount);
                balances.put(feeAccount, updatedFees);
            }
            markDirty();
        }
        record(from, to, cents, purpose, actorUuid, institutionId, type, valid,
                valid ? source - debit : source, valid ? updatedTarget : target);
        if (valid && fee > 0) record(from, "system:fees", fee, "Überweisungsgebühr", actorUuid, institutionId,
                "TRANSFER_FEE", true, source - debit, balance("system:fees"));
        return valid;
    }

    public boolean adjust(String account, long delta, String purpose, String actorUuid,
                          String institutionId, String type) {
        ensureAccount(account);
        long current = balance(account);
        boolean valid = delta != 0 && !(delta < 0 && (isFrozen(account) || current < -delta));
        long updated = current;
        if (valid) {
            try { updated = Math.addExact(current, delta); }
            catch (ArithmeticException overflow) { valid = false; }
        }
        if (valid) {
            balances.put(account, updated);
            markDirty();
        }
        String sender = delta < 0 ? account : "CASH";
        String recipient = delta < 0 ? "CASH" : account;
        record(sender, recipient, Math.abs(delta), purpose, actorUuid, institutionId, type, valid,
                delta < 0 ? (valid ? updated : current) : 0L,
                delta < 0 ? 0L : (valid ? updated : current));
        return valid;
    }

    public List<EconomyTransaction> history(String account) {
        return reversed(accountTransactions.getOrDefault(account, List.of()));
    }
    public List<EconomyTransaction> institutionHistory(String institutionId) {
        return reversed(institutionTransactions.getOrDefault(institutionId, List.of()));
    }
    public List<EconomyTransaction> transactions() {
        List<EconomyTransaction> result = new ArrayList<>(transactions.size());
        for (int index = transactions.size() - 1; index >= 0; index--) result.add(transactions.get(index));
        return List.copyOf(result);
    }

    private void record(String sender, String recipient, long amount, String purpose, String actorUuid,
                        String institutionId, String type, boolean successful, long senderBalance, long recipientBalance) {
        EconomyTransaction entry = new EconomyTransaction(UUID.randomUUID().toString(), System.currentTimeMillis(), sender, recipient,
                Math.max(0, amount), purpose == null ? "" : purpose, actorUuid == null ? "" : actorUuid,
                institutionId == null ? "" : institutionId, type == null ? "TRANSFER" : type,
                successful, senderBalance, recipientBalance);
        transactions.add(entry);
        indexTransaction(entry);
        if (ConfigManager.logging().logTransactions)
            TerraNexus.LOGGER.info("Buchung {} {} -> {}: {} [{}]", type, sender, recipient, amount, successful ? "OK" : "ABGELEHNT");
        markDirty();
    }

    private String createAccountNumber() {
        var config = ConfigManager.economy();
        long bound = powerOfTen(config.accountNumberDigits);
        while (true) {
            String number = config.accountNumberPrefix + String.format("%0" + config.accountNumberDigits + "d", RANDOM.nextLong(bound));
            if (!accountNumberIndex.containsKey(normalizeAccountNumber(number))) return number;
        }
    }
    private void indexTransaction(EconomyTransaction entry) {
        accountTransactions.computeIfAbsent(entry.sender(), ignored -> new ArrayList<>()).add(entry);
        if (!entry.recipient().equals(entry.sender())) accountTransactions.computeIfAbsent(entry.recipient(), ignored -> new ArrayList<>()).add(entry);
        if (!entry.institutionId().isBlank()) institutionTransactions.computeIfAbsent(entry.institutionId(), ignored -> new ArrayList<>()).add(entry);
    }
    private static List<EconomyTransaction> reversed(List<EconomyTransaction> source) {
        List<EconomyTransaction> result = new ArrayList<>(source.size());
        for (int index = source.size() - 1; index >= 0; index--) result.add(source.get(index));
        return List.copyOf(result);
    }
    private static long powerOfTen(int digits) { long value = 1; for (int index = 0; index < digits; index++) value *= 10; return value; }
    private static String normalizeAccountNumber(String number) { return number == null ? "" : number.replace(" ", "").toUpperCase(); }
    private static boolean isPlayerAccount(String account) { try { UUID.fromString(account); return true; } catch (IllegalArgumentException ignored) { return false; } }
    private static long transferFee(long amount) {
        if (amount <= 0 || ConfigManager.economy().transferFeeBasisPoints <= 0) return 0;
        try { return Math.max(1, Math.multiplyExact(amount, ConfigManager.economy().transferFeeBasisPoints) / 10_000L); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }
    private static String institutionFrom(String account) {
        return account.startsWith("institution:") ? account.substring("institution:".length()) : "";
    }

    public static String format(long cents) {
        var config = ConfigManager.economy();
        return BigDecimal.valueOf(cents, config.currencyDecimals).toPlainString() + " " + config.currencySymbol;
    }

    public static Long parseAmount(String value, boolean allowZero) {
        if (value == null) return null;
        try {
            long amount = new BigDecimal(value.trim().replace(',', '.'))
                    .movePointRight(ConfigManager.economy().currencyDecimals)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            return amount > 0 || (allowZero && amount == 0) ? amount : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
