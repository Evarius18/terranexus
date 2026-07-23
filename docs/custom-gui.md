# TerraNexus Custom-GUI

## Architektur

TerraNexus verwendet für Verwaltungs- und Informationsseiten keine Vanilla-Truhen mehr. Die bisherigen
`SimpleInventory`-Builder bleiben vorerst als serverinterne, kompatible Beschreibungsschicht bestehen. Sie
werden nicht geöffnet und enthalten keine bewegbaren Inventargegenstände. `CustomGuiService` liest daraus
Titel, Beschreibung, ursprüngliche Position und Aktion und sendet ausschließlich ein begrenztes Darstellungsmodell
an den Client.

Der Client öffnet `TerraNexusMenuScreen`, einen normalen `Screen` ohne Container-Slots und Spielerinventar.
`GuiActionButton` rendert Hintergrund, Zustand und Icon aus `management_atlas.png`. Alle Layoutmaße und
UV-Bereiche liegen benannt in `ManagementGuiAtlas` beziehungsweise `TerraNexusMenuScreen`.

## Responsive Layouts

`StructuredGuiLayout` wählt ausschließlich anhand von `Screen.width` und `Screen.height` zwischen `LARGE`,
`MEDIUM` und `COMPACT`. Im Desktopmodus bleibt das breite Raster mit rechtem Detailpanel erhalten. Die
mittlere Darstellung reduziert Spalten und setzt Details unter den Inhalt. Im Kompaktmodus bleiben Schrift,
Icons und Hitboxen auf ihren Mindestgrößen; Karten- und Chunk-Raster scrollen und lange Werkzeugleisten werden
seitenweise über **Weitere Aktionen** zugänglich. Position, Darstellung und Klickbereich verwenden dieselben
finalen GUI-Koordinaten. Beschriftungen werden nicht unter 85 Prozent skaliert, sondern bei Bedarf mit Ellipse
gekürzt und vollständig als Tooltip angeboten.

## TerraNexus-Suche

Konten-, Empfänger-, Grundstücks- und Audit-Suchen öffnen `TerraNexusSearchScreen` als eigene Seite. Das
frühere Amboss-/Containerfenster wird für diese Suchvorgänge nicht mehr verwendet. Der Client sendet erst bei
Enter oder einem Klick auf **Suchen** das Sitzungstoken und den begrenzten Suchbegriff. `CustomSearchService`
validiert Token, Aktion und Länge serverseitig; die jeweilige Fachfunktion prüft anschließend erneut Rechte und
filtert die Daten. An den Client gelangt nur die konfigurierte Ergebnisseite über das bestehende begrenzte
GUI-Modell. Escape beziehungsweise **Zurück** führt über einen serverseitig hinterlegten Rücksprung zur
vorherigen Verwaltungsseite.

Jede geöffnete Seite erhält ein zufälliges Sitzungstoken. Ein Klick sendet nur Token, `GuiAction` und
Element-ID. Der Server akzeptiert ausschließlich die aktuelle Sitzung dieses Spielers, verwirft Aktionen auf
nicht vorhandene Elemente und macht Aktionssitzungen nach einem Klick ungültig. Erst danach führt er den
bisherigen Callback aus; dieser prüft Rechte, Kontostand, Besitz und aktuellen Datenzustand weiterhin
serverseitig. Eine neue Seite erhält immer ein neues Token. Disconnect und Schließen entfernen die Sitzung.

## Migration der bisherigen Icons

| Bisherige Itemgruppe | Neue Atlasdarstellung | Aktion |
| --- | --- | --- |
| Pfeil, Tür | `BACK` | bisheriger Zurück-/Navigationscallback über `custom_gui_action` |
| Spielerkopf, Namensschild | `PERSON` | Bürger-, Mitarbeiter- oder Besitzeransicht |
| Gold, Smaragd, Truhe, Trichter | `FINANCE` | Bank-, Kauf-, Verkauf- oder Zahlungsaktion |
| Karte, Kompass, Grasblock | `LAND` | Grundstück, Suche oder Kartennavigation |
| Buch, Papier, Bücherregal | `DOCUMENT` | Akte, Protokoll oder Dokumentansicht |
| Uhr, Redstone-Fackel | `CLOCK` | Stempeluhr, Laufzeit oder Historie |
| Komparator, Redstone | `SETTINGS` | Einstellung oder Rechtekonfiguration |
| Barriere, rote Zustandsicons | `WARNING` | Abbruch, Sperre oder kritische Bestätigung |
| grüne Zustandsicons | `CONFIRM` | Bestätigung oder aktiver Zustand |
| Ziegel, Schild, Eisenblock | `INSTITUTION` | Institution, Gebäude oder Verwaltungseinheit |
| Beacon | `ADMIN` | administrativer Bereich |
| sonstige Anzeigeelemente | `HOME` | neutrale Information beziehungsweise Startseite |

Die serverseitige Element-ID entspricht während der Migration der bisherigen Position von 0 bis 53. Dadurch
bleiben Pagination, Zurückpfade und sämtliche vorhandenen Callback-Zuordnungen unverändert. Freie
Integer-Paketaktionen existieren nicht: Der Pakettyp verwendet das Enum `GuiAction`; die Element-ID wird nur
innerhalb der tokenisierten Sitzung auf einen serverseitig hinterlegten Callback aufgelöst.

## Weitere Schaltfläche ergänzen

1. Wie bisher im betreffenden Menübuilder `ManagementHubScreen.display(...)` aufrufen.
2. Für einen aktiven Button denselben Elementplatz in die `actions`-Map eintragen.
3. Die Aktion vollständig serverseitig validieren und anschließend die Zielseite erneut über
   `CustomGuiService.open(...)` öffnen.
4. Falls ein neues semantisches Icon benötigt wird, `GuiIcon` und die Atlaszuordnung in
   `ManagementGuiAtlas.icon(...)` erweitern.

Für reine Infokarten wird kein Callback eingetragen; sie erscheinen automatisch deaktiviert und können
trotzdem einen Tooltip besitzen. Live-Seiten verwenden `CustomGuiService.openLive(...)`.

## Grafikdatei

Der aktuelle, von Imagegen erzeugte und technisch eingebundene Platzhalteratlas liegt unter:

`src/main/resources/assets/terranexus/textures/gui/management_atlas.png`

Er enthält Hintergrund, vier Buttonzustände, Panel-/Scroll-Elemente und sechzehn Icons. Eine spätere finale
Grafik darf die Datei ersetzen, muss aber entweder dieselben Bereiche und Abmessungen beibehalten oder die
benannten UV-Werte in `ManagementGuiAtlas` entsprechend aktualisieren. Texte werden absichtlich nicht in die
PNG eingebrannt, sondern über Minecrafts TextRenderer und Sprachschlüssel dargestellt.
