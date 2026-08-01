# TerraNexus – Team-Wiki

Diese Anleitung beschreibt die Bedienung der produktiven Verwaltungsfunktionen. Alle Änderungen werden
serverseitig geprüft. Ein sichtbarer Button allein ersetzt niemals die benötigte Rolle.

## 1. Rollen und Rechte

### Hauptrollen

| Rolle | Zweck |
|---|---|
| `admin` | Vollständige Administration |
| `supporter` | Supportfälle und protokollierte Bürgerlöschung per Command |
| `whitelister` | Einreiseprüfung und Freischaltung |
| `civil_registrar` | Bürger- und Standesamtsverwaltung, amtliche Ausreise |
| `immigration_officer` | Einreisebehörde, Bürgerakten und amtliche Ausreise |
| `builder` | Baumodus; keine Eigentümer-, Bank- oder Bürgerrechte |
| `land_surveyor` | Grundstücke vermessen und Formen ändern |
| `land_clerk` | Grundbuch-Sachbearbeitung und Grundstückserstellung |
| `land_administrator` | Bauamtsleitung einschließlich Hierarchie und Löschung |
| `land_registrar` | Kompatible umfassende Bauamtsrolle |
| `tn_admin_test` | Ausschließlich Entwicklung und Test |

Rolle vergeben: `/authority grant <Spieler> <Rolle>`  
Rolle entziehen: `/authority revoke <Spieler> <Rolle>`  
Rollen prüfen: `/authority list <Spieler>`

Die Commands benötigen Operatorrechte. Rollenprüfungen bleiben fest im Servercode und können nicht durch
eine Config abgeschaltet werden.

### Institutionsrollen

`Owner` besitzt alle Rechte. `Director` verwaltet Personal, Rollen, Finanzen und Einstellungen. `Manager`
verwaltet Mitarbeiter, Aufgaben und ausgewählte Bankfunktionen. `Auditor` liest Finanz- und Auditdaten.
`Accountant` verwaltet Buchungen, Rechnungen und Gehälter. `HR` verwaltet Personal und Rollen. `Employee`
besitzt ausschließlich Standardzugriff. Rollen werden in der Institutionsverwaltung geändert.

## 2. Bürgerverwaltung

Einreisebehörde oder Verwaltungscomputer öffnen, Bürger auswählen und die Akte prüfen. Akten können dort
bearbeitet, freigegeben und als Personalausweis ausgestellt werden.

### Amtliche Ausreise

In der Bürgerakte `Amtliche Ausreise` wählen, Begründung eintragen und bestätigen. Vorher müssen
Institutions-Eigentum übertragen sowie aktive Mietverträge und eigene Verwaltungsebenen beendet werden.
Grundstücke gehen an Wilderness zurück; persönliche Verknüpfungen werden bereinigt. Der Vorgang wird im
Ausreiseaudit gespeichert.

### Support-Löschung

Die Supportfunktion erscheint absichtlich in keiner normalen GUI:

`/identity support-delete <Bürgernummer|UUID> <Begründung>`

Nur `supporter`, `admin` und `tn_admin_test` dürfen den Command verwenden. Bearbeiter, Zeitpunkt, Bürger,
Modus, Grund und Bereinigungsergebnis werden revisionssicher protokolliert.

## 3. Grundstücksverwaltung

Für die vollständige Grundstücksübersicht werden Bauamtsrolle, Bauamts-Tablet und für Vermessungen das
physische Landvermessungsgerät benötigt. Rechtsklick setzt einen Eckpunkt, Linksklick entfernt den zuletzt
gesetzten Punkt. Das Tablet danach erneut öffnen, um die Auswahl abzuschließen oder zu verwerfen.

### Grundstück anlegen

Im Tablet `Chunk anlegen`, `Quader auswählen` oder `Freiformvermessung starten` wählen. Freiformen benötigen
mindestens drei nicht kreuzende Punkte. Vor dem Speichern zeigt die Vorschau die Grenze; Überschneidungen
werden serverseitig abgelehnt.

### Grundstück vergrößern oder verkleinern

Grundstück öffnen, `Form neu vermessen` wählen und die neue vollständige Außengrenze markieren. Name, ID,
Eigentümer, Verträge und Historie bleiben erhalten. Bereits vorhandene Unterbereiche müssen weiterhin
vollständig innerhalb der neuen Grenze liegen.

### Teilfläche abtrennen

`Teilfläche abtrennen` wählen, einen Namen vergeben und die Teilfläche innerhalb des Hauptgrundstücks
markieren. Die Fläche wird aus dem Hauptgrundstück ausgespart und als neues Grundstück angelegt. Anschließend
kann sie beim bisherigen Eigentümer bleiben oder über die normale zustimmungspflichtige Grundbuchübertragung
weitergegeben werden.

### Interne Unterbereiche

`Unterbereich anlegen` wählen, Typ und Namen festlegen und die Fläche markieren. Verfügbare Typen stehen in
`config/TerraNexus/claims.json`, standardmäßig Wohnung, Gemeinschaftsbereich, Keller, Garage,
Gewerbeeinheit und Sonstiger Bereich. Unterbereiche besitzen kein eigenes Eigentum und keine parallelen
Grundstücksrechte; Schutz, Eigentümer und Zuständigkeit werden immer vom Hauptgrundstück geerbt. Sie können
in der Unterbereichsliste umbenannt oder gelöscht werden.

### Eigentümerwechsel

In der Grundstücksakte `Eigentümer übertragen` verwenden. Die vorhandenen Zustimmungspflichten gelten auch
für abgetrennte Flächen. Land Survey allein umgeht keine Zustimmungen; höhere Verwaltungsrechte und
Institutionen folgen den zentralen Sonderregeln.

## 4. Verwaltungshierarchie

Über `Verwaltungshierarchie` können Gemeinde, Stadt, Landkreis, Bundesland und Land sowie zusätzliche
konfigurierte Ebenen erstellt werden. In der Detailansicht lassen sich Name und Verantwortung bearbeiten.
Eine Einheit kann gelöscht werden, wenn sie keine Untereinheiten, Beschäftigten, offenen Verträge und keinen
Kontosaldo besitzt. Grundstücke und Zuständigkeiten wechseln dabei zur übergeordneten Ebene. Wilderness
bleibt immer bestehen. Erstellen, Umbenennen, Eigentümerwechsel und Löschen werden protokolliert.

## 5. Messenger

Im Handy `Nachrichten` öffnen. `Neuer Einzelchat` wählt einen freigeschalteten Bürger. `Neue Gruppe` fragt
Gruppenname und Teilnehmer ab. Nur der Gruppeneigentümer kann die Mitgliedschaft verwalten.

Im Chat befindet sich die Eingabe direkt am unteren Rand; Enter oder `Senden` verschickt die Nachricht.
Offline-Spieler erhalten sie beim nächsten Login und sehen die Zahl ungelesener Nachrichten. Jede Nachricht
zeigt ihren automatischen Löschzeitpunkt. Die Frist und Mengenlimits stehen in
`config/TerraNexus/phone.json`. Abgelaufene Nachrichten werden physisch aus dem Welt-PersistentState entfernt.

## 6. Bankautomat

Einzahlungen akzeptieren Münzen und Scheine. Auszahlungen akzeptieren ausschließlich Beträge ab 5 Nexus in
5-Nexus-Schritten und geben nur Scheine aus. Die Stückelung wird automatisch mit möglichst wenigen Scheinen
berechnet. Fehlender Kontostand, Kontosperre oder zu wenig Inventarplatz verhindern die Buchung vollständig.

## 7. Fehlerbehebung

- Fehlender Menüpunkt: Rolle mit `/authority list` prüfen und das korrekte Gerät verwenden.
- Vermessung reagiert nicht: Landvermessungsgerät ins Inventar nehmen und Auswahl im Tablet starten.
- Grundstück kann nicht gespeichert werden: Kreuzungen, Überschneidungen und Unterbereiche prüfen.
- Verwaltungseinheit kann nicht gelöscht werden: Untereinheiten, Personal, Verträge und Gebietskonto leeren.
- Support-Löschung scheitert: Institutions-Eigentum, aktive Miete oder eigene Verwaltungsebene zuerst ordnen.
- Messenger zeigt keine Person: Bürgerakte muss existieren und freigeschaltet sein.

## 8. Relevante Konfigurationen

- `claims.json`: Polygonpunkte, Unterbereichstypen und Größenlimits
- `administration.json`: Ebenennamen, Wilderness und öffentliche Nutzungen
- `phone.json`: Messenger-Aufbewahrung, Nachrichten-, Gruppen- und Unterhaltungslimits
- `immigration.json`: Bürgerdaten und Größe des Ausreiseaudits
- `economy.json`: Währung, Konten und Transaktionsgrenzen
- `logging.json`: Auditgrenzen und Protokollierung

Nach Config-Änderungen `/tnadmin reload-config` verwenden. Änderungen an Mod-Dateien erfordern einen Neustart.

## 9. Ergänzungen: Gebäudeakten, Teammodus und Wahlen

Interne Unterbereiche werden in ihrer Detailansicht über `Mehrfamilienhaus zuordnen` einer Gebäudeakte
zugewiesen. Neue Gebäudeakten sind fest mit dem Hauptgrundstück verbunden. Wohnungen, Keller, Garagen und
Gemeinschaftsflächen behalten ihre eigene Geometrie und Klassifizierung, erscheinen aber gemeinsam unter dem
Mehrfamilienhaus. Die Verbindung kann einzeln geändert oder entfernt werden.

Die Verwaltungshierarchie ist nur mit `land_hierarchy_administrator`, `admin` oder `tn_admin_test` sichtbar
und änderbar. Normale Bauamtsrollen können weiterhin ihre jeweiligen Grundstücksaufgaben erledigen, aber
keine Gemeinden, Städte, Landkreise, Bundesländer oder Länder verändern.

Die Rollen `supporter`, `builder` und `moderator` schalten Sonderrechte erst im Arbeitsmodus frei:
`/teammode support [Grund]`, `/teammode builder [Grund]`, `/teammode moderation [Grund]` und
`/teammode off [Grund]`. Inventar einschließlich Rüstung und Offhand, Leben, Hunger, XP und Effekte werden
persistent gesichert und beim Beenden vollständig wiederhergestellt. Wechsel, Rolle, Zeit, Dauer und Grund
werden protokolliert.

Die Handy-App `Wahlen` zeigt aktive Vorgänge. Jeder freigeschaltete Bürger kann pro Wahl genau einmal
abstimmen. Verwaltungsbefehle:

- `/election create <id> <type> <area|global> <role|none> <Titel>`
- `/election option <id> <Bezeichnung>`
- `/election candidate <id> <Spieler>`
- `/election open <id> [Minuten]`
- `/election close <id>`

Für Bürgermeister- und Stadtratswahlen stehen `mayor` und `city_council` als Ergebnisrollen bereit.
Sachabstimmungen verwenden `none`. Laufzeiten und Limits stehen in `config/TerraNexus/elections.json`.

Im Messenger erzeugt Umschalt+Enter eine neue Zeile. Lange Texte werden umgebrochen, die verbleibenden
Zeichen werden angezeigt und `☺` öffnet eine kompakte Unicode-Emoji-Auswahl.
