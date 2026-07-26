package net.evarius.terranexus.landlord;

public final class LandTransferPolicyTest {
    private LandTransferPolicyTest() {}

    public static void run() {
        require(!LandTransferService.ready(request("player", "player", false, false, false)),
                "Spielerübertragung ohne Zustimmungen wurde freigegeben");
        require(LandTransferService.ready(request("player", "player", true, true, false)),
                "Spielerübertragung mit beiden Zustimmungen wurde blockiert");
        require(!LandTransferService.ready(request("player", "player", true, false, false)),
                "Leitungsübertragung ohne Empfängerzustimmung wurde freigegeben");
        require(LandTransferService.ready(request("player", "institution", true, false, false)),
                "Institution als Empfänger wurde unnötig zur Zustimmung gezwungen");
        require(!LandTransferService.ready(request("institution", "player", true, true, false)),
                "Institutionsgrundstück wurde ohne Institutionsfreigabe übertragen");
        require(LandTransferService.ready(request("institution", "player", false, true, true)),
                "Institutionsgrundstück mit Leitungs- und Empfängerfreigabe wurde blockiert");
    }

    private static LandTransferRequest request(String oldType, String newType, boolean owner,
                                               boolean recipient, boolean institution) {
        return new LandTransferRequest("request", "property", oldType, "old", newType, "new",
                "actor", owner, recipient, institution, 0L);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
