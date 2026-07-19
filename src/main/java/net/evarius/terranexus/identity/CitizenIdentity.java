package net.evarius.terranexus.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CitizenIdentity(
        String playerUuid,
        String citizenNumber,
        String firstName,
        String lastName,
        String birthDate,
        String birthPlace,
        String birthCountry,
        String nationality,
        String gender,
        String address
) {
    public static final Codec<CitizenIdentity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("player_uuid").forGetter(CitizenIdentity::playerUuid),
            Codec.STRING.fieldOf("citizen_number").forGetter(CitizenIdentity::citizenNumber),
            Codec.STRING.fieldOf("first_name").forGetter(CitizenIdentity::firstName),
            Codec.STRING.fieldOf("last_name").forGetter(CitizenIdentity::lastName),
            Codec.STRING.fieldOf("birth_date").forGetter(CitizenIdentity::birthDate),
            Codec.STRING.fieldOf("birth_place").forGetter(CitizenIdentity::birthPlace),
            Codec.STRING.fieldOf("birth_country").forGetter(CitizenIdentity::birthCountry),
            Codec.STRING.fieldOf("nationality").forGetter(CitizenIdentity::nationality),
            Codec.STRING.optionalFieldOf("gender", "Nicht angegeben").forGetter(CitizenIdentity::gender),
            Codec.STRING.optionalFieldOf("address", "Nicht gemeldet").forGetter(CitizenIdentity::address)
    ).apply(instance, CitizenIdentity::new));

    public CitizenIdentity withField(String field, String value) {
        return switch (field.toLowerCase()) {
            case "vorname", "firstname" -> new CitizenIdentity(playerUuid, citizenNumber, value, lastName, birthDate, birthPlace, birthCountry, nationality, gender, address);
            case "nachname", "lastname" -> new CitizenIdentity(playerUuid, citizenNumber, firstName, value, birthDate, birthPlace, birthCountry, nationality, gender, address);
            case "geburtsdatum", "birthdate" -> new CitizenIdentity(playerUuid, citizenNumber, firstName, lastName, value, birthPlace, birthCountry, nationality, gender, address);
            case "geburtsort", "birthplace" -> new CitizenIdentity(playerUuid, citizenNumber, firstName, lastName, birthDate, value, birthCountry, nationality, gender, address);
            case "geburtsland", "birthcountry" -> new CitizenIdentity(playerUuid, citizenNumber, firstName, lastName, birthDate, birthPlace, value, nationality, gender, address);
            case "nationalitaet", "nationality" -> new CitizenIdentity(playerUuid, citizenNumber, firstName, lastName, birthDate, birthPlace, birthCountry, value, gender, address);
            case "geschlecht", "gender" -> new CitizenIdentity(playerUuid, citizenNumber, firstName, lastName, birthDate, birthPlace, birthCountry, nationality, value, address);
            case "adresse", "address" -> new CitizenIdentity(playerUuid, citizenNumber, firstName, lastName, birthDate, birthPlace, birthCountry, nationality, gender, value);
            default -> null;
        };
    }
}
