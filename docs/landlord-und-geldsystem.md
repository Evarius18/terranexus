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
`/give @s terranexus:land_survey_tool` ausgeben. `/tnadmin land-info` zeigt an der aktuellen Position die
effektive Fläche, Zuständigkeit, den Eigentümer und die Flächennutzung – einschließlich virtueller Wilderness.

## Vermessung und Erstellung

Grundstücke können als Chunk, Quader oder freie Polygonform angelegt werden. Für eine Freiform muss das
Landvermessungsgerät tatsächlich im Inventar liegen. Nach dem Start in der Bauamt-GUI setzt ein Rechtsklick auf
einen Block einen Eckpunkt; ein Linksklick entfernt den zuletzt gesetzten Punkt – auch beim Klick in die Luft.
Der Client sendet dafür nur einen Bedienimpuls; Besitz des Werkzeugs, Rolle, Dimension und aktive Auswahl werden
auf dem Server erneut geprüft. Der Entwurf wird serverseitig
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
Actionbar. Die virtuelle Wilderness wird bei einem geclaimten Grundstück nicht zusätzlich angezeigt; sie erscheint
nur auf tatsächlich ungeclaimter Fläche oder als separat eingetragene Zuständigkeit.

## Verkauf, Vermietung und Rechte

Verkäufe prüfen das Guthaben serverseitig, buchen atomar und schreiben das Eigentum unmittelbar um. Als Verkäufer
können Bürger, Institutionen und Verwaltungsebenen auftreten; der Erlös fließt immer auf das zum aktuellen
Grundbucheigentümer gehörende Konto. Aktive Mietverträge sperren einen Verkauf. Eigentumswechsel und Finanzbuchung
werden dauerhaft protokolliert.

Mietangebote werden erst durch den vorgesehenen Mieter aktiviert. Dabei wird die Kaution auf ein separates
Treuhandkonto gebucht; anschließend erfolgen periodische Mietabbuchungen. Verträge unterstützen eine frei wählbare
Zahl von Zahlungsperioden, unbefristete Laufzeit sowie optionale automatische Verlängerung. Nach der konfigurierten
Zahl offener Termine endet der Vertrag. Bei Ablauf oder Kündigung wird eine verfügbare Kaution atomar zurückgebucht.
Nach Vertragsende fallen die Zugriffsrechte automatisch an den Eigentümer zurück.

Ein Miettag entspricht standardmäßig 1.440 Minuten. Für Tests kann `rentDayDurationMinutes` in
`config/TerraNexus/claims.json` auf `1` gesetzt und anschließend neu geladen werden.

Die Rechteverwaltung trennt Bauen, allgemeine Interaktion, Container und Redstone. Eigentümer können diese
Rechte öffentlich oder einzeln für registrierte Bürger freigeben. Aktive Mieter erhalten Grundstückszugriff.

## Kistenshops

Ein Shop besteht aus einer einzelnen Kiste und einem direkt angrenzenden Schild. Das Schild verwendet vier Zeilen:

```text
[Shop]
minecraft:stone
K: 10.00
V: 5.00
```

`K` ist der Preis, zu dem Kunden aus der Kiste kaufen; `V` ist der Preis, zu dem der Shop Items von Kunden ankauft.
Ein Preis darf `0` sein, um diese Handelsrichtung zu deaktivieren. Ein berechtigter Flächenverantwortlicher
rechtsklickt das fertig beschriftete Schild einmal zur Registrierung; danach öffnet derselbe Rechtsklick die
Shop-GUI. Lagerbestand, Inventarplatz, Kontodeckung, Entfernung und aktueller Grundstückseigentümer werden bei
jedem Handel erneut geprüft.

Die Kiste ist für Kunden versiegelt. Abbau und Aufhebung sind nur durch Shop- oder Flächenverantwortliche möglich.
Gehandelt werden ausschließlich unveränderte Standard-Items; benannte, verzauberte oder mit Daten befüllte
Varianten werden nicht gezählt. Nach einem Eigentümerwechsel pausiert ein alter Shop automatisch, bis ihn der neue
Verantwortliche aufhebt und neu registriert. So fließt kein Erlös weiter an den früheren Eigentümer.

## Zentralbank und Gehälter

Institutionen der Art `Zentralbank` erhalten auf dem Admin-Desktop eine eigene Fachanwendung. Owner und Director
dürfen nach einer Sicherheitsbestätigung Geld emittieren oder einziehen. Auditor und Accountant besitzen eine
Übersicht. TNAdmin behält den gekennzeichneten Entwicklungszugriff. Die Oberfläche zeigt Geldmenge, Verteilung,
Kontenzahl, abgelehnte Buchungen sowie filterbare Journale für Grundstücke, Mieten, Shops, Gehälter und Geldpolitik.

Institutionen verwenden ihre Personal- und Gehaltsverwaltung. Eigentümer von Städten, Gemeinden und anderen
Verwaltungsebenen sehen zusätzlich `Verwaltungsfinanzen` auf dem Admin-Desktop. Dort werden Gebietskonto,
Zahlungsverkehr, eigene Grundstücke/Gebäude, Mitarbeiter, konfigurierbare Gehaltsgruppen, nächste Auszahlung und
Gehaltsjournal verwaltet. Institutionseigentümer und Directors erreichen ihre Flächen über den Institutions-Desktop.
Fehlende Deckung
erzeugt keine Teilzahlung, sondern eine abgelehnte, nachvollziehbare Buchung.

Alle kombinierten Vorgänge verwenden den zentralen atomaren Buchungskern: Entweder werden Geld und Fachzustand
(Eigentum, Mietvertrag, Shopinventar oder Auszahlungstermin) gemeinsam übernommen oder ohne Teiländerung abgelehnt.
Konten, Verträge, Shops und Beschäftigungen liegen in Minecraft-`PersistentState` und werden mit der Welt gespeichert.

## Verwaltung, Suche und Protokollierung

Die Hierarchie ist nicht fest im Code verdrahtet. Standardmäßig reicht sie von `Ort / Stadt` über Gemeinde,
Landkreis, Region und Bundesland bis Staat. Namen und Anzahl der Stufen kommen aus `administration.json`.
Es können beliebig viele Orte, Gemeinden und weitere Einheiten je Stufe angelegt werden; neue Einheiten müssen
einer fachlich passenden übergeordneten Einheit zugewiesen werden.

Über allen konfigurierten Stufen liegt immer die virtuelle, unveränderliche `Wilderness`. Nicht explizit
eingetragene Blöcke gehören dadurch ohne gigantische Welt-Datensätze eindeutig dieser Wurzel. Bestehende und
neue Grundstücke besitzen zusätzlich genau eine Verwaltungszuständigkeit; alte Datensätze werden automatisch
der Wilderness zugeordnet. Verwaltungseinheiten können selbst Eigentümer von Grundstücken sein, ihre
Verantwortung kann einem Bürger oder einer Institution übertragen werden und jede Einheit hat ein eigenes Konto.

Die Flächennutzung unterscheidet Privatgrundstücke und konfigurierbare öffentliche Nutzungen wie Straße, Weg,
Park, Platz oder Gewässer. Eine öffentliche Klassifizierung setzt definierte Standardrechte, erlaubt aber
standardmäßig weder Bauen noch Container- oder Redstonezugriff. Diese Rechte können anschließend wie gewohnt
gezielt angepasst werden.

Die Bauamtssuche findet Grundstücke nach Grundstücks-ID, Name, Adresse, RP-Besitzer, Flächennutzung oder
Verwaltungszuständigkeit. Ein persistentes
Audit-Log erfasst Erstellungen, Umbenennungen, Geometrie- und Adressänderungen, Eigentümerwechsel, Verkäufe und
Löschungen mit Zeitstempel und handelnder UUID. Eigentümerwechsel werden zusätzlich als Grundstückshistorie
gespeichert. Online betroffene Eigentümer erhalten eine Benachrichtigung.

Grundstücke, Verwaltungshierarchie, Zuständigkeiten, Nutzungsarten und Vertragsdaten werden über Minecraft
`PersistentState` gespeichert. Ein Chunk-Index beschleunigt
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
