# TerraNexus – Grundstücks- und Geldsystem

## Geräte und Rollen

Der Verwaltungs-PC enthält für berechtigte Personen das Modul „Grundbuch und Landlord“. Das Bauamt-Tablet
öffnet dieselbe Fachanwendung, ist jedoch ausschließlich für Bauamtsmitarbeiter nutzbar. Ein Spieler ohne
Bauamtsrolle erhält keine Oberfläche und kann auch ein weitergegebenes Tablet nicht verwenden.

Der Admin-Desktop stellt abhängig von den Rollen die vollständige Fachauswahl bereit: Bürger- und
Einreiseverwaltung, Verwaltungsbank, Institutionen, Grundbuch/Landlord und für Administratoren das
Grundstücks-Audit. Die Zurück-Schaltflächen bleiben innerhalb des jeweiligen Arbeitsablaufs: Eine
Grundstücksakte führt zurück in die Grundbuchverwaltung und niemals unbeabsichtigt zum Bauamt-Tablet.

Die Berechtigungsstufen sind:

- `land_surveyor`: Grundstücke vermessen, anlegen und geometrisch bearbeiten;
- `land_clerk`: Eigentümer, Adressen, Verträge und Einzelrechte bearbeiten;
- `land_administrator`: vollständiger Zugriff, Verwaltungsebenen, Audit-Log und Löschungen;
- `land_registrar`: kompatible Altsystemrolle mit vollständigem Zugriff.

`/tnadmin test-access` erstellt bei Bedarf automatisch eine freigeschaltete Test-Bürgerakte, vergibt sämtliche
Testrollen und gibt Verwaltungs-PC, Tablets und Grundbuchauszug aus. Das Landvermessungsgerät wird bewusst nicht
ausgegeben, weil es als physische Lager-Hardware beschafft werden soll. Für Tests kann ein Operator es mit
`/give @s terranexus:land_survey_tool` ausgeben.

## Vermessung und Erstellung

Grundstücke können als Chunk, Quader oder freie Polygonform angelegt werden. Für eine Freiform muss das
Landvermessungsgerät tatsächlich im Inventar liegen. Nach dem Start in der Bauamt-GUI setzt ein Rechtsklick auf
einen Block einen Eckpunkt; ein Linksklick entfernt den zuletzt gesetzten Punkt. Der Entwurf wird serverseitig
gespeichert und überlebt reguläre Serverneustarts. Nur der vermessende Mitarbeiter sieht die laufende
Partikelkontur.

Vor der Eintragung erscheint ein Bestätigungsdialog mit Name, Form und Koordinaten. Abbrechen speichert noch kein
Grundstück und lässt die Auswahl für Korrekturen bestehen. Die Validierung erkennt zu wenige oder doppelte
Punkte, Flächen ohne Ausdehnung, sich kreuzende Kanten und Überschneidungen mit einem konkreten bestehenden
Grundstück. Bestehende Geometrien besitzen Rückgängig, Vorschau, Vergrößern und Verkleinern.

Die 3×3-Übersicht prüft ganze Chunkflächen und nicht nur den Chunkmittelpunkt. Dadurch werden auch kleine oder
in anderer Höhe liegende Quader korrekt als belegt angezeigt.

## Grundbuchauszug für Eigentümer

Eigentümer verwenden nicht das Bauamt-Tablet, sondern den Gegenstand `terranexus:land_registry_extract`.
Der Auszug zeigt eigene Grundstücke mit ID, eingetragener Adresse, Lage, Verträgen und Eigentümerhistorie sowie
relevante Kauf- und Mietangebote.

Für jedes eigene Grundstück kann eine private GS-Markierung aktiviert oder deaktiviert werden. Die Partikel
werden ausschließlich an den betreffenden Eigentümer gesendet und sind für andere Spieler unsichtbar. Nach fünf
Minuten schaltet sie sich automatisch ab. Beim Betreten eines Grundstücks erscheint dessen Name kurz in der
Actionbar.

## Verkauf, Vermietung und Rechte

Verkäufe prüfen das Guthaben serverseitig, buchen atomar und schreiben das Eigentum unmittelbar um. Mietangebote
werden erst durch den vorgesehenen Mieter aktiviert. Dabei wird die Kaution eingezogen; anschließend erfolgen
periodische Mietabbuchungen. Nach drei offenen Terminen endet der Vertrag. Bei regulärer Beendigung wird eine
verfügbare Kaution zurückgebucht.

Ein Miettag entspricht standardmäßig 1.440 Minuten. Für Tests kann `rentDayDurationMinutes` in
`config/terranexus.json` vor dem Serverstart auf `1` gesetzt werden.

Die Rechteverwaltung trennt Bauen, allgemeine Interaktion, Container und Redstone. Eigentümer können diese
Rechte öffentlich oder einzeln für registrierte Bürger freigeben. Aktive Mieter erhalten Grundstückszugriff.

## Verwaltung, Suche und Protokollierung

Die Hierarchie lautet `Region → Landkreis → Gemeinde → Stadtteil`; jede Ebene besitzt ein eigenes Bankkonto.
Die Bauamtssuche findet Grundstücke nach Grundstücks-ID, Name, Adresse oder RP-Besitzer. Ein persistentes
Audit-Log erfasst Erstellungen, Umbenennungen, Geometrie- und Adressänderungen, Eigentümerwechsel, Verkäufe und
Löschungen mit Zeitstempel und handelnder UUID. Eigentümerwechsel werden zusätzlich als Grundstückshistorie
gespeichert. Online betroffene Eigentümer erhalten eine Benachrichtigung.

Grundstücke und Vertragsdaten werden über Minecraft `PersistentState` gespeichert. Ein Chunk-Index beschleunigt
Positions- und Kartenabfragen bei größeren Grundstücksmengen; sehr große Flächen fallen sicher auf eine separate
Breitbereichsliste zurück.

## Bedienung und Datensicherheit

Felder mit festem Wertebereich – derzeit Geschlecht und Institutionsart – werden über anklickbare
Auswahloberflächen statt über Freitext eingegeben. Diese Oberflächen verwenden kein Ambossfenster und benötigen
keine Erfahrungslevel. Freitextfelder validieren leere und überlange Werte sowie das Geburtsdatum im Format
`TT.MM.JJJJ`.

Alle schreibenden Aktionen prüfen Rollen, Eigentum und den aktuellen Datensatz beim Klick erneut. Damit können
alte, noch geöffnete Menüs nach einem Rollen- oder Eigentümerwechsel keine unzulässigen Änderungen ausführen.
Ungültige gespeicherte Auswahlpunkte oder Miet-UUIDs werden beim Laden bzw. Abrechnungslauf übersprungen und
bringen den Server nicht zum Stillstand.
