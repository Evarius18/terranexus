# TerraNexus-Assetprüfung

Stand: 01.08.2026, Zielversion: Fabric / Minecraft 1.21.8.

## Integrierte Dateien

- `computer_monitor_buero_keys.json`: als Blockmodell des Verwaltungs-PCs eingebunden. Die beiden vollständigen Abhängigkeiten `rgb_tablet` und `tasten` zeigen auf TerraNexus-Ressourcen. Die Blockkollision wurde an die Modellgrenzen angepasst.
- `handy_grun.json`: als `terranexus:mobile_phone_green` eingebunden.
- `handy_rot.json`: als `terranexus:mobile_phone_red` eingebunden.
- `rgb_tablet.png`: für Verwaltungs-PC und alle Handyvarianten aktualisiert.
- `rgb_sheet.png`: für Bauamt-Tablet und Landvermessungsgerät aktualisiert.
- `tasten.png`: für Verwaltungs-PC, Handyvarianten und Bauamt-Tablet aktualisiert.

Beim Verwaltungs-PC lag die gelbe Darstellung nicht an Parent-Modell, UV-Pfad oder Resource-Pack-Reihenfolge: Seine lokale `tasten.png` enthielt 1.779 vollständig deckende Neon-Gelb-Pixel. Nur in der PC-spezifischen Texturkopie wurden diese Tastenglyphen neutral weiß gesetzt. UVs, Modellgeometrie und die farbigen Sondertasten blieben unverändert.

Beide neuen Handys verwenden dieselbe Java-Implementierung und dieselbe Telefon-/GUI-Integration wie das bestehende Handy. Die Farbe wird ausschließlich durch das jeweilige Itemmodell bestimmt.

## Stempeluhr-Integration

`terranexus:time_clock_terminal` ist bereits als horizontal ausrichtbarer, platzierbarer Block mit Blockitem, Blockstate, Itemmodell, Loot-Tabelle, Werkzeug-Tags und Creative-Tab-Eintrag registriert. Die Interaktion prüft den Mitarbeiterchip und öffnet das bestehende persistente Dienst-/Arbeitszeitsystem. Es wurde kein paralleles Stempeluhrsystem angelegt.

Das gelieferte Blockbench-Modell ist jetzt aktiv. Die Texturzuordnung lautet exakt:

- `0` → `terranexus:block/time_clock_terminal/tasten`
- `1` → `terranexus:block/time_clock_terminal/rgb_tablet`
- `2` → `terranexus:block/time_clock_terminal/rgb_sheet`
- `3` → `terranexus:block/time_clock_terminal/stempeluhr`
- `particle` → `terranexus:block/time_clock_terminal/tasten`

Der Export markiert zusätzlich 219 nicht belegte Flächen mit `#missing`, ohne dafür eine fünfte Textur zu definieren. Diese nicht belegten Flächen werden beim Build ausgelassen; es wird keine Ersatztextur erfunden. Mehrdimensionale Blockbench-Rotationen werden im erzeugten Ressourcenmodell auf Vanilla-kompatible Quader gebacken. Die Quelldatei bleibt dabei unverändert.

Es fehlen keine weiteren Dateien. Externe Resource-Packs, Parent-Modelle, Animationen oder zusätzliche Libraries werden von den integrierten Modellen nicht referenziert.
