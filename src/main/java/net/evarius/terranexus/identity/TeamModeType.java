package net.evarius.terranexus.identity;

public enum TeamModeType {
    SUPPORT("support", AuthorityState.SUPPORTER),
    BUILDER("builder", AuthorityState.BUILDER),
    MODERATION("moderation", AuthorityState.MODERATOR);

    private final String id;
    private final String requiredRole;
    TeamModeType(String id,String requiredRole){this.id=id;this.requiredRole=requiredRole;}
    public String id(){return id;}
    public String requiredRole(){return requiredRole;}
    public static TeamModeType byId(String id){for(TeamModeType value:values())if(value.id.equalsIgnoreCase(id))return value;return null;}
}
