# TerraNexus – Bank- und Institutionsmanagement

## Konten und Startguthaben

Personen-, Institutions- und Gebietskonten werden im bestehenden `terranexus_economy`-PersistentState geführt.
Neue Konten beginnen ausdrücklich mit `0` Guthaben; weder beim Login noch bei der Bürger- oder
Institutionserstellung wird Startkapital vergeben. Jedes Konto erhält eine dauerhaft gespeicherte, zufällige
Kontonummer und kann durch berechtigte Bankmitarbeiter gesperrt oder entsperrt werden.

Jede Buchung speichert Zeitpunkt, Sender, Empfänger, Betrag, Verwendungszweck, ausführende UUID,
Institutionsbezug, Buchungstyp, Erfolgsstatus sowie die resultierenden Salden. Das Journal umfasst auch
abgelehnte Gehalts-, Miet- und Überweisungsversuche. Der vollständige Buchungsverlauf bleibt persistent
erhalten; die Anzeige liest ihn in umgekehrter Speicherreihenfolge und paginiert ihn ohne erneutes Sortieren.

## Bankverwaltung

Die Bankverwaltung steht ausschließlich Beschäftigten einer Institution vom Typ `Bank/Finanzinstitut` mit
passender Rolle sowie dem expliziten TNAdmin-Testzugriff offen. Sie bietet:

- vollständige, paginierte Kontenübersicht;
- Suche nach RP-Name, Bürgernummer, technischer Kontozuordnung oder Kontonummer;
- Kontostand, Sperrstatus und Kontobewegungen;
- globales Revisionsjournal;
- protokollierte Schalter-Ein- und -Auszahlungen;
- Kontosperren und Entsperrungen.

Normale Spieler können ausschließlich eigene Konten verwenden. Auditoren besitzen Lesezugriff, aber keine
Buchungsrechte. Eingefrorene Konten können keine ausgehenden Zahlungen oder Barauszahlungen ausführen.

## Institutionsrollen

| Rolle | Wesentliche Rechte |
| --- | --- |
| Owner | Vollzugriff und Eigentumsübertragung |
| Director | Personal, Rollen, Finanzen, Gehälter und Einstellungen |
| Manager | Mitarbeiterverwaltung, operative Aufgaben und Finanzsicht |
| Auditor | Buchungen und Berichte ausschließlich lesen |
| Accountant | Überweisungen, Buchhaltung, Rechnungsrechte und Gehälter |
| HR | Einstellungen, Entlassungen, Personalakten und Rollen innerhalb der zulässigen Hierarchie |
| Employee | Standardzugriff und Mitarbeiterübersicht |

Jede Institution besitzt genau einen Owner. Beim Anlegen wird der Ersteller Owner. Eine Übertragung ist nur
durch den Owner beziehungsweise TNAdmin möglich und erfordert einen Bestätigungsdialog; der bisherige Owner
wird anschließend Director. Alte `owner`-, `manager`- und `member`-Einträge werden beim Laden automatisch in
das neue Mitarbeiterformat migriert.

## Personal und Gehälter

Mitarbeiterakten enthalten Bürger-UUID, Rolle, Eintrittsdatum, Gehalt, nächste Auszahlung und einen internen
Personalvermerk. Einstellungen erfolgen aus freigegebenen Bürgerakten. Entlassungen, Rollen- und
Gehaltsänderungen werden serverseitig erneut gegen Rolle und Hierarchie geprüft.

Gehälter werden aus dem Institutionskonto bezahlt. Die Periode wird über `paymentIntervalMinutes` in
`config/TerraNexus/salary.json` gesteuert und beträgt standardmäßig 10.080 Minuten (eine Woche). Bei fehlender
Kontodeckung erfolgt keine Auszahlung; der Fehlversuch wird protokolliert und der Mitarbeiter sowie online
anwesende Owner, Directors und Accountants werden informiert.

## Stempeluhr und Besetzungsregeln

Jeder Beschäftigte erreicht die Stempeluhr über den Institutionsdesktop und kann sich dort selbst ein- oder
ausstempeln. Beginn, Ende, kumulierte Dienstzeit und eine begrenzte Schichthistorie werden im
`terranexus_time_clock`-PersistentState gespeichert. Auch die je Institution berechnete Zahl der aktuell
eingestempelten Mitarbeiter ist persistent; beim Weltstart wird sie aus den Dienstakten geprüft und bei Bedarf
repariert. Eine Entlassung beendet eine noch laufende Schicht atomar.

Owner, Directors und Manager sehen eine paginierte Dienstübersicht. Auditor, Accountant und HR besitzen
Lesezugriff. Schwellenwerte dürfen nur Rollen mit `MANAGE_TIME_CLOCK_SETTINGS` ändern; einfache Mitarbeiter
können ausschließlich den eigenen Status und die eigenen Zeiten bedienen. Der Hauptbildschirm aktualisiert
Zähler und Warnung live, ohne das Menü neu öffnen zu müssen. Aktive Mitarbeiter erhalten zusätzlich eine
konfigurierbare Actionbar-Anzeige.

Die Regeln aus `config/TerraNexus/timeclock.json` sind über stabile IDs erweiterbar. Der mitgelieferte
Feuerwehrwert kann beispielsweise mit
`TimeClockService.ruleSatisfied(server, institutionId, "fire_spread_staffing")` von einer späteren
Feuersimulation abgefragt werden. Institutionsspezifische Abweichungen werden im Einstellungsmenü gespeichert,
ohne den globalen Standard zu verändern.

## TNAdmin-Testzugriff

`/tnadmin test-access` vergibt zusätzlich die klar gekennzeichnete Rolle `tn_admin_test`. Nur diese
Entwicklungsrolle umgeht Bank- und Institutionsberechtigungen. `/tnadmin remove-test-access` entfernt sie
wieder. Ein regulärer Operatorstatus allein wird in den GUIs nicht als Bankbeschäftigung behandelt.

## Technische Sicherheit

Alle Oberflächen sind serverautoritativ und lesen PersistentStates beim Öffnen beziehungsweise beim Ausführen
einer Aktion erneut. Die zentrale Rollenmatrix liegt in `InstitutionRole` und `InstitutionPermission`; die
Prüfung erfolgt über `InstitutionAccess`. Bestehende APIs wie `EconomyState.transfer`, Institutionskontoschlüssel
und `InstitutionState.mayManage` bleiben kompatibel.
