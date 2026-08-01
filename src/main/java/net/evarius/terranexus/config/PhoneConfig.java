package net.evarius.terranexus.config;

public final class PhoneConfig {
    public String _description = "TerraNexus-Handy: Synchronisierung und maximale Anzahl übertragener RP-VCA-Historieneinträge.";
    public int synchronizationIntervalTicks = 10;
    public int historyLimit = 50;
    public int maximumDialLength = 64;
    public int messengerRetentionHours = 168;
    public int messengerMaximumMessageLength = 500;
    public int messengerMaximumMessagesPerConversation = 500;
    public int messengerMaximumGroupMembers = 32;
    public int messengerMaximumConversationsPerPlayer = 100;
    public int messengerCleanupIntervalTicks = 1200;

    void validate() {
        synchronizationIntervalTicks = ConfigManager.clamp(synchronizationIntervalTicks, 2, 100);
        historyLimit = ConfigManager.clamp(historyLimit, 5, 250);
        maximumDialLength = ConfigManager.clamp(maximumDialLength, 8, 128);
        messengerRetentionHours = ConfigManager.clamp(messengerRetentionHours, 1, 8760);
        messengerMaximumMessageLength = ConfigManager.clamp(messengerMaximumMessageLength, 32, 2000);
        messengerMaximumMessagesPerConversation = ConfigManager.clamp(messengerMaximumMessagesPerConversation, 20, 5000);
        messengerMaximumGroupMembers = ConfigManager.clamp(messengerMaximumGroupMembers, 2, 100);
        messengerMaximumConversationsPerPlayer = ConfigManager.clamp(messengerMaximumConversationsPerPlayer, 10, 500);
        messengerCleanupIntervalTicks = ConfigManager.clamp(messengerCleanupIntervalTicks, 200, 72000);
    }
}
