# TerraNexus-Handy und RP Voice Additions

Die Telefon-App ist eine optionale Integration für die Mod-ID `rp-vca`. TerraNexus besitzt
keine direkte Klassen- oder Laufzeitabhängigkeit auf RP Voice Additions. Erst nach einem
erfolgreichen `FabricLoader.isModLoaded("rp-vca")` wird die Reflection-Bridge erzeugt.
Ohne Add-on wird die Telefon-Kachel nicht registriert; alle anderen Handy-Apps bleiben
unverändert erreichbar.

## Öffentliche Integrationsgrenze

`RpVoicePhoneProvider` verwendet ausschließlich den öffentlichen Einstieg
`com.evarius.rpvca.RpVoiceServices` und den davon gelieferten `PhoneService.ClientView`.
Darüber werden Gesprächsstatus, Netzabdeckung, Kontakte, Notrufnummern und die öffentlichen
Gesprächsaktionen angebunden. Private Felder der anderen Mod werden nicht gelesen.

Ein inkompatibler API- oder Linkage-Fehler setzt den Provider für die laufende Sitzung auf
`unhealthy`. Dadurch wird eine bereits geöffnete Telefonseite beendet und die App bei der
nächsten Anzeige des Rasters ausgelassen.

## Konkreter Erweiterungspunkt: Anrufhistorie

RP Voice Additions 1.0.0 stellt noch keine öffentliche Historien-API bereit. Deshalb leitet
`PhoneHistoryService` die Historie aus den serverseitig gelesenen Zustandsübergängen ab und
speichert sie in `PhoneHistoryState`. Die Anzahl wird durch `phone.json/historyLimit` begrenzt.

Sobald RP Voice Additions eine öffentliche, spielerbezogene Historien-API anbietet, wird
`PhoneFeatureProvider` um eine Historienabfrage ergänzt und `RpVoicePhoneProvider` kann diese
Einträge direkt liefern. `PhoneClientState` und die Historienseite bleiben dabei unverändert;
die aktuelle Zustandsableitung kann dann als Fallback bestehen bleiben.

## Konfiguration

`config/TerraNexus/phone.json`:

- `synchronizationIntervalTicks`: Intervall für serverseitige Statusaktualisierungen.
- `historyLimit`: maximale gespeicherte Einträge pro Spieler.
- `maximumDialLength`: maximale serverseitig akzeptierte Länge einer Rufangabe.
