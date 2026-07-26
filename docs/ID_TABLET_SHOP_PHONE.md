# Ausweis, Bauamt-Tablet, Shops, Immobilien und TN-Handy

## Technische Basis

- Minecraft 1.21.8, Fabric Loader 0.19.2, Fabric API 0.136.1 und Yarn Mappings.
- Java 21; es wurden keine neuen Laufzeit-Libraries ergänzt.
- Alle Änderungen verwenden die vorhandenen servergesteuerten GUI-Aktionen und PersistentStates.
- Simple Voice Chat ist bewusst **keine** harte Abhängigkeit. Telefonie-Addons können Apps über
  `PhoneAppRegistry` und Kontodaten lesend über `TerraNexusEconomyApi` anbinden.

## Personalausweis

Die Bürgerinformation wird als eigenständig gerenderte, responsive ID-Karte angezeigt. Sie besitzt:

- Kartenformat mit Sicherheitsmuster, Chip, Primär- und Sekundärportrait,
- zweisprachige Feldbezeichnungen, Statusfeld und maschinenlesbare Zone,
- einheitliche Skalierung innerhalb der verfügbaren GUI-Fläche,
- einen vollständig innerhalb der Karte liegenden Schließen-Button.

Der Ausweis bleibt ein fiktionales TerraNexus-Dokument und kopiert kein amtliches deutsches
Dokument. Bürgerdaten und Freigabestatus bleiben vollständig serverseitig.

## Bauamt-Tablet

Das Item `terranexus:building_authority_tablet` verwendet das importierte
Blockbench-Modell. `tablet_bauamt.json` enthält die Geometrie aus
`tablet_huelle_robust.json` bereits vollständig; deshalb wird nicht zusätzlich
ein zweites, deckungsgleiches Modell gerendert.

Verwendete Texturen:

- `textures/item/building_authority_tablet/rgb_sheet.png`
- `textures/item/land_survey_tool/tasten.png`
- `textures/item/building_authority_tablet/display.png`

Die dritte, im Blockbench-Export nur über einen lokalen Dateinamen referenzierte
Displaygrafik lag nicht bei und wurde durch eine eigenständige
TerraNexus-Bauamt-Platzhaltergrafik ersetzt. Es sind keine zusätzlichen
Libraries oder Mod-Abhängigkeiten erforderlich.

## Shop-Assistent

Ein Operator oder `TNAdmin` beschriftet die erste Zeile eines Schildes mit
`[Shop]` und klickt es an. Der Assistent fragt Item-ID, Verkaufspreis und
Ankaufspreis ab. In der Vorschau lassen sich Verkauf (V) und Ankauf (K)
unabhängig aktivieren, deaktivieren und anschließend speichern.

Die Administratorberechtigung ist aus Sicherheitsgründen nicht per Config
abschaltbar. Grundstückszuordnung, Kiste, Entfernung, Item-ID und Preise werden
beim endgültigen Speichern erneut serverseitig geprüft.

Bestehende gespeicherte Shops erhalten beim Laden automatisch für beide neuen
Schalter den kompatiblen Standardwert `true`; Preise von `0` bleiben weiterhin
deaktiviert.

Die Handelsansicht wird vom Client als kompakter Dialog statt als bildschirmfüllender
Verwaltungsdesktop dargestellt. Bestand, Spielerbestand, Betreiber und erlaubte
Handelsrichtungen werden bei jeder Aktion erneut serverseitig geprüft.

## TN-Handy und Economy-API

`terranexus:mobile_phone` öffnet eine eigene responsive Smartphone-Oberfläche im Hochformat.
Sie besitzt App-Kacheln, Statusleiste, Scrollen, exakte Hitboxen sowie kurze Öffnungs- und
Schließanimationen. Die eingebaute Bank-App
zeigt Kontostand, Kontonummer, Kontostatus und die letzten Buchungen.

Weitere Addons registrieren eine `PhoneApplication` über `PhoneAppRegistry`.
Kontodaten können ausschließlich lesend über
`TerraNexusEconomyApi.accountSnapshot(ServerPlayerEntity)` bezogen werden.
Geldänderungen bleiben absichtlich in den serverseitigen Economy-Services.
Damit kann ein späteres Simple-Voice-Chat-Telefonmodul Apps, Kontakte,
Nachrichten und Telefonie ergänzen, ohne eine harte Laufzeitabhängigkeit
zwischen TerraNexus und Simple Voice Chat zu erzeugen.

Die vorbereiteten Apps Kontakte, Telefon, Nachrichten, Firmen und Behörden sind sichtbar,
aber bis zur Anbindung eines fachlichen Backends deaktiviert. Die Immobilien-App ist bereits
funktionsfähig und zeigt Kaufangebote, Eigentum, eigene Mietverhältnisse sowie öffentliche
Wohnungs- und Hotelangebote.

## Immobilien, Wohnungen und Hotels

Bestehende Grundstücke bilden weiterhin die einheitliche räumliche und rechtliche Grundlage.
Zusätzliche konfigurierbare Nutzungsarten unterscheiden Wohnhäuser, Firmengebäude,
Mehrfamilienhäuser, Mietwohnungen, Hotels/Pensionen, Hotelzimmer und Sonderimmobilien.

- Kaufangebote verwenden die atomare Economy-Buchung und übertragen anschließend das Eigentum.
- Käufer erhalten einen personalisierten, servergeprüften Immobilienschlüssel.
- Vermieter können zielgerichtete oder öffentliche Mietangebote anlegen.
- Öffentliche Angebote ermöglichen Self-Check-in für Wohnungen und Hotelzimmer.
- Zimmer und Wohnungen werden als getrennte Freiform-/Quadergrundstücke modelliert. Dadurch
  besitzen sie eigene Bewohner, Verträge, Schlüssel, Klingelschilder/Briefkästen als normale
  geschützte Interaktionsblöcke und unabhängig verwaltbare Rechte.
- Beim Vertragsende werden Mieterrechte automatisch entzogen; ausgegebene Schlüssel werden
  dadurch ohne Itemsuche sofort ungültig.

Öffentliche Immobilienverkäufe sind standardmäßig gesperrt. Sonderimmobilien dürfen über
`claims.json` gezielt erlaubt oder verboten werden.

## Gehälter und Stempeluhr

Institutionsmitarbeiter besitzen nun eine persistente Gehaltsgruppe. Autorisierte Rollen können
im Personalmenü Gruppen auswählen, einen individuellen Betrag setzen, Gehälter deaktivieren
oder eine sofortige Auszahlung auslösen. Behörden verwenden das bestehende, integrierte
Verwaltungsgehaltssystem. Beide Bereiche buchen ausschließlich über die zentrale Economy und
führen ein Gehaltsjournal.

Die digitale Stempeluhr ist als `terranexus:time_clock_terminal` platzierbar:

1. HR bzw. eine berechtigte Leitung stellt in der Personalakte einen Dienstausweis aus.
2. Der Ausweis wird der betreffenden Person im RP übergeben.
3. Die Person hält den personalisierten Chip an das Terminal und kann ein-/ausstempeln.
4. Aktive Mitarbeiterzahl, Schichthistorie und Schwellenwarnungen werden live angezeigt.

Der Chip wird bei jeder Benutzung gegen das aktuelle Beschäftigungsverhältnis geprüft.
Entlassene Personen können ihn deshalb nicht weiterverwenden. Schwellenwerte werden zentral
in `time_clock.json` definiert und dürfen institutionsspezifisch überschrieben werden.

## Konfiguration und Migration

- `administration.json`: frei benennbare Immobilien-/Flächennutzungsarten.
- `claims.json`: Verkaufsregeln, Mieterrechte, Laufzeiten und Schutz.
- `salary.json`: Gehaltsgruppen, Obergrenzen und Zahlungsintervall.
- `time_clock.json`: Aktualisierung, Historienlänge und erweiterbare Besetzungsregeln.
- `shops.json`: Shoplimits, Preise, Grundstückspflicht und Handel.
- `desktop.json`: Einträge pro Seite und Aktualisierungsintervalle.

Alte Mitarbeiterdatensätze werden mit der Gruppe `Individuell` geladen. Alte Shops behalten
beide Handelsrichtungen. Bestehende Mietverträge bleiben unverändert; die zusätzlichen
Vertragsfelder besitzen kompatible Standardwerte.
