# TerraNexus-Konfiguration

TerraNexus erzeugt beim ersten Start automatisch den Ordner `config/TerraNexus/`. Alle Dateien werden als
UTF-8 geschrieben. Fehlende Felder werden bei späteren Mod-Updates ergänzt, vorhandene Werte und unbekannte
Zusatzfelder bleiben erhalten. Schreibvorgänge erfolgen über eine temporäre Datei und einen atomaren
Dateiaustausch. Nicht lesbare JSON-Dateien werden als `.invalid-<Zeitstempel>` gesichert und mit sicheren
Standardwerten neu erstellt.

Geldbeträge in den JSON-Dateien verwenden stets die kleinste Währungseinheit. Bei zwei Nachkommastellen
entsprechen `10000` daher `100,00 TN€`. Basispunkte bei Gebühren bedeuten: 100 Basispunkte = 1 Prozent.

## Dateien

### `general.json`

- `configVersion`: Version der Konfigurationsstruktur.
- `serverDisplayName`: RP-Name in Verwaltungsoberflächen.

### `economy.json`

- Währungsname, Symbol und null bis zwei Nachkommastellen.
- Startguthaben neuer Bürgerkonten; Standard bleibt `0`.
- maximales Limit normaler Spielerüberweisungen.
- Überweisungsgebühr in Basispunkten.
- Präfix und Ziffernzahl neu erzeugter Kontonummern.

Geänderte Nummernregeln wirken ausschließlich auf neue Konten. Bestehende Kontonummern bleiben stabil.

### `bank.json`

- maximales Limit einer Schalter-Ein- oder -Auszahlung;
- Verfügbarkeit der Kontosperrfunktion;
- minimale Länge eines Suchbegriffs.

Bankrollen und ihre Rechte sind absichtlich nicht konfigurierbar.

### `institutions.json`

- maximale Mitarbeiterzahl;
- maximale Länge von Namen und Personalvermerken;
- optionale Gründungsgebühr;
- Standardrolle neuer Mitarbeiter;
- Rollen, die künftig vergeben werden dürfen;
- auswählbare Institutionsarten;
- Bezeichnungen, anhand derer eine Institution als Zentralbank erkannt wird.

`owner` kann nicht deaktiviert oder als normale Rolle vergeben werden. Werden Rollen aus der Liste entfernt,
bleiben vorhandene Mitarbeiterdatensätze aus Gründen der Rückwärtskompatibilität erhalten.

### `salary.json`

- automatische Gehaltsläufe aktivieren oder pausieren;
- Zahlungsintervall in Minuten;
- Standard- und Höchstgehalt;
- Benachrichtigungen für Mitarbeiter und verantwortliches Personal;
- maximale Mitarbeiterzahl je Verwaltungsebene;
- Gehaltsgruppen und Standardbeträge für Städte, Gemeinden und andere Verwaltungsebenen.

Beim Pausieren werden keine Datensätze gelöscht. Nach einer erneuten Aktivierung wird ein überfälliger Lauf
beim nächsten Wartungsintervall verarbeitet.

### `claims.json`

- vertikale Standardgrenzen neuer Chunk- und Freiformgrundstücke;
- maximale Polygonpunkte und Länge von Grundstücksnamen;
- Dauer privater Grenzmarkierungen;
- simulierte Minuten pro Miettag und maximal offene Mietzahlungen;
- Grundstückseintrittsmeldung;
- Schutz für Interaktionen, Container, Redstone, Explosionen, Kolben, Automation, Flüssigkeiten, Feuer und
  Ackerboden;
- optionales PvP-Verbot innerhalb von Grundstücken.

Der Bauschutz, Eigentumsprüfung und individuelle Berechtigungsprüfung können nicht deaktiviert werden.

### `shops.json`

- Shopsystem aktivieren oder pausieren;
- Shops ausschließlich auf geclaimten Grundstücken verlangen;
- Ankauf von Spieler-Items aktivieren;
- maximale Shopanzahl je Eigentümer;
- maximale Itemmenge je Transaktion;
- maximaler Einzelpreis.

Ein Deaktivieren pausiert Interaktion und Neuanlage, löscht aber keine gespeicherten Shops. Eigentums-, Bestands-
und Kontoprüfungen sind nicht konfigurierbar und bleiben immer verpflichtend.

### `timeclock.json`

- Stempeluhr und laufende Statusanzeige aktivieren oder pausieren;
- Aktualisierungsintervall der Echtzeitanzeige;
- optionales automatisches Ausstempeln beim Verlassen des Servers;
- maximale Zahl detailliert gespeicherter Schichten je Mitarbeiter;
- frei erweiterbare Besetzungs- und Gameplay-Regeln mit Institutionsart, Standardwert und Vergleichsoperator.

`rules` ist eine nach stabilen Regel-IDs gegliederte Map. `institutionTypeKeywords` begrenzt eine Regel auf
passende Institutionsarten; eine leere Liste gilt für alle. Unterstützte Vergleiche sind `AT_LEAST`,
`MORE_THAN`, `AT_MOST` und `LESS_THAN`. `warnWhenUnsatisfied` steuert nur die Warnanzeige. Die Bedingung kann
unabhängig davon über die zentrale Stempeluhr-API von späteren Simulationen abgefragt werden. Der
institutionsspezifische Wert lässt sich durch berechtigte Mitarbeiter in der Stempeluhr ändern; er wird sicher
in der Welt gespeichert, während die JSON-Datei den Serverstandard vorgibt.

### `administration.json`

- Name und Ebenenbezeichnung der unveränderlichen Wilderness-Wurzel;
- frei benennbare Hierarchieebenen von der kleinsten bis zur größten Einheit;
- maximale Zahl und Namenslänge von Verwaltungseinheiten;
- auswählbare private und öffentliche Flächennutzungen;
- sichere Standardfreigaben für öffentliche Straßen, Wege, Parks, Plätze und Gewässer;
- öffentliche Rechte, optionale PvP-Sperre und Umweltschutz der virtuellen Wilderness-Fläche.

Die Position eines Levels in `hierarchyLevels` ist seine stabile Hierarchiestufe. Anzeigenamen können geändert
werden, ohne gespeicherte Einheiten neu anzulegen. Beim Umsortieren ändert sich dagegen bewusst die fachliche
Bedeutung der Stufe. Die Wilderness-Wurzel kann weder gelöscht noch als normales Spielereigentum übertragen
werden. Auch wenn `wildernessPublicBuildingAllowed` aktiviert wird, durchläuft jeder Bauversuch weiterhin die
zentrale Berechtigungsprüfung.

### `immigration.json`

- maximale Länge amtlicher Textfelder;
- Präfix und Ziffernzahl neuer Bürgernummern;
- amtliche Geschlechtsauswahl.

Nummernregeln gelten nur für neue Bürgerakten. Einreisefreigabe und Behördenrechte bleiben verpflichtend.

### `desktop.json`

- Einträge pro Seite in Standardlisten und in der dreispaltigen Einreiseansicht;
- Anzeige von Kontonummern in Kontoübersichten.

Die Werte werden auf die technisch sichere Größe des serverseitigen 9×6-Menüs begrenzt.

### `performance.json`

- Aktualisierungsintervalle für Grenzvisualisierung, Grundstückseintritt und Wartung;
- Partikelbudget pro Spieler und Kante;
- maximale Chunk-Indexgröße je Grundstück;
- Grenze für die exakte, spaltenweise Polygon-Überschneidungsprüfung.

Große Grundstücke oberhalb der Indexgrenze bleiben funktionsfähig und werden in einem separaten Broad-Claim-
Index geführt. Eine niedrigere exakte Überschneidungsgrenze lehnt sehr große, nicht eindeutig prüfbare
Überlappungen sicherheitshalber ab.

### `logging.json`

- Debugausgaben;
- Meldung erfolgreicher Config-Ladevorgänge;
- Konsolenlogging von Transaktionen und verweigerten Aktionen;
- maximale Anzahl persistenter Grundstücks-Auditdatensätze.

Das persistente Transaktionsjournal und notwendige Integritätsprotokolle lassen sich nicht abschalten. Eine
separate Datenbank existiert in der aktuellen Architektur nicht, daher gibt es keine wirkungslose
`databaseLogging`-Option.

## Migration und Reload

Die bisherige `config/terranexus.json` wird beim ersten Start als Quelle für Währung, Mietdauer und
Gehaltsintervall gelesen. Sie wird weder gelöscht noch überschrieben. Welt-PersistentStates behalten ihre IDs
und Datenformate; eine manuelle Datenmigration ist nicht erforderlich.

Operatoren können Änderungen mit `/tnadmin reload-config` übernehmen. Strukturabhängige
Grundstücksindizes und die konfigurierbaren Hierarchiebezeichnungen werden dabei direkt neu aufgebaut. Für Änderungen an Mixins oder Mod-Dateien ist weiterhin
ein Serverneustart erforderlich.

Minecraft verwaltet das Speichern der PersistentStates und die Container-Synchronisation selbst. Eigene
Autosave- oder Netzwerkintervalle werden deshalb bewusst nicht angeboten: parallele Speicher- oder
Synchronisationssysteme würden Datenrisiken und unnötige Pakete erzeugen.

Es werden außerdem keine leeren `jobs.json`-, `police.json`- oder `permissions.json`-Dateien erzeugt. Für Jobs
und Polizei existiert derzeit kein fachliches Modul; eine Config ohne Verbraucher wäre irreführend. Die
Berechtigungsmatrix bleibt bewusst Bestandteil des Codes, damit eine fehlerhafte Datei keine
Sicherheitsprüfung abschalten kann. Sobald entsprechende RP-Module hinzukommen, können sie als weitere
fachliche Config-Dateien in denselben Manager aufgenommen werden.
