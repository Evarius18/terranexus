# TerraNexus-Telefon und RP Voice Additions

## Optionale Kopplung

TerraNexus prüft zuerst die Fabric-Mod-ID `rp-vca`. Nur bei erfolgreicher Prüfung wird
`com.evarius.rpvca.api.RpVcaApi` reflektiert geladen. Ohne RP Voice Additions bleibt die
Telefon-Kachel ausgeblendet; alle übrigen Handy-Apps funktionieren unverändert.

Die Bridge verwendet ausschließlich `RpVcaApi.getPhoneService()` und die öffentliche
`PhoneApi`. Interne RP-VCA-Services, Profile oder private Felder werden nicht gelesen.

Verwendete API-Funktionen:

- Rufnummer: `allocateNumber`, `getAssignedNumber`
- Status und Notruf: `getStatus`, `getEmergencyNumbers`
- Gespräche: `call`, `answer`, `decline`, `hangup`, `toggleSpeaker`
- Kontakte: `getContacts`, `upsertContact`, `removeContact`
- Verlauf: `getCallHistory`, `removeHistoryEntry`, `clearOwnCallHistory`,
  `clearCallHistory`, `clearAllCallHistories`

Kontakte und Anrufhistorie werden ausschließlich in RP Voice Additions gespeichert.
TerraNexus hält davon nur unveränderliche Ansichten für das aktuell geöffnete UI und besitzt
keine parallele Kontakt- oder Historienpersistenz.

## Serverautorisierung

Der Client sendet nur eine benannte Aktion und die benötigten Eingaben. TerraNexus prüft die
aktive Handy-Sitzung und grundlegende Zustände; die endgültige Nummernauflösung,
Netzabdeckung, Berechtigung und Datenmutation erfolgen in `PhoneApi`.

Administrative History-Aktionen werden nur Operatoren angeboten. Die öffentliche API prüft
ihre eigene konfigurierbare Berechtigungsstufe nochmals serverseitig. Das Leeren sämtlicher
Historien erfordert zusätzlich eine explizite Bestätigung im TerraNexus-Paket.

## Rettungsorganisationen

`config/TerraNexus/institutions.json` enthält `emergencyOrganizationMappings`. Die Schlüssel
`FW`, `RD` und `POL` sind nur Standardwerte und können erweitert oder ersetzt werden. Mitglieder
passender TerraNexus-Institutionen werden über RP-VCA's öffentliche
`InstitutionMembershipProvider`-Schnittstelle mit Institutions-ID, Name, Typ und Alias
bereitgestellt.

RP-VCA ändert seine Notrufzustellung derzeit in `PhoneService.onTeam(...)` ausschließlich anhand
eines Vanilla-Scoreboard-Teams. Damit die konfigurierten TerraNexus-Aliase auch bei
`EmergencyConfig.Number.responderTeam` ausgewertet werden, muss RP-VCA dort zusätzlich
`RpVcaApi.institutionMembershipKeys(player)` berücksichtigen. TerraNexus verändert bewusst keine
Scoreboard-Teams als Umgehung.

## Konfiguration

`config/TerraNexus/phone.json` begrenzt Synchronisierungsintervall, übertragene History-Einträge
und Eingabelänge. Die fachlichen Telefon- und Kontaktlimits bleiben Eigentum der RP-VCA-
Konfiguration.
