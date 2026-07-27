package net.evarius.terranexus.identity;

public final class AuthorityRoleTest {
    private AuthorityRoleTest() {}

    public static void run() {
        require(AuthorityState.isKnownRole(AuthorityState.ADMIN), "Admin-Rolle ist nicht registriert");
        require(AuthorityState.isKnownRole(AuthorityState.SUPPORTER), "Supporter-Rolle ist nicht registriert");
        require(AuthorityState.isKnownRole(AuthorityState.WHITELISTER), "Whitelister-Rolle ist nicht registriert");
        require(AuthorityState.isKnownRole(AuthorityState.BUILDER), "Builder-Rolle ist nicht registriert");
        require(AuthorityState.knownRoles().stream().distinct().count() == AuthorityState.knownRoles().size(),
                "Rollenregister enthält doppelte Einträge");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
