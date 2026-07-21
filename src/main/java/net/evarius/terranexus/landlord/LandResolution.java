package net.evarius.terranexus.landlord;

/** Effective ownership and jurisdiction at one world position. A null property represents Wilderness. */
public record LandResolution(LandProperty property, AdministrativeArea jurisdiction,
                             String ownerType, String ownerId, String landUse) {
    public boolean wilderness() { return property == null; }
}
