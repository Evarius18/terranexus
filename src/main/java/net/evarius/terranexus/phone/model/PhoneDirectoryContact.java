package net.evarius.terranexus.phone.model;

import java.util.UUID;

/** A saved phone contact that can be resolved to an approved TerraNexus citizen. */
public record PhoneDirectoryContact(UUID playerId, String name, String number) {
}
