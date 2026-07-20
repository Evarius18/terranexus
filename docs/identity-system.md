# TerraNexus identity system

Identity data is stored server-side in the overworld persistent state. The Minecraft UUID is the
technical owner key; a collision-checked random `TN-########` number is the stable roleplay citizen number.
ID-card item data is only a reference. Using the card always resolves the current server record.

## Commands

Administrative commands require permission level 2.

```text
/identity set <player> <field> <value>
/identity show <player>
/identity issue <player>
/identity approve <player>
/authority grant <player> <civil_registrar|immigration_officer|supporter>
/authority revoke <player> <civil_registrar|immigration_officer|supporter>
```

Every player can use `/identity` or `/identity me`. A valid issued ID card or the citizen management
device opens the same read-only GUI on right-click.

Supported `set` fields are `vorname`, `nachname`, `geburtsdatum`, `geburtsort`, `geburtsland`,
`nationalitaet`, `geschlecht`, and `adresse` (English aliases are supported as well). Values containing
spaces are supported by `set` because the final value consumes the rest of the command.

Example:

```text
/identity set Steve geburtsdatum 23.05.1990
/identity set Steve adresse Hauptstraße 12, TerraNexus
/identity set Steve geschlecht divers
/identity issue Steve
```

New identities cannot be created through a command. Authorized staff must select an unregistered online
player in the immigration GUI and complete the guided form.

## Immigration workflow

New identities start without immigration approval. Only players explicitly assigned the `civil_registrar`,
`immigration_officer`, or `supporter` role can process them. Operator status alone does not grant access.
Operators or the server console are only the bootstrap authority for granting/revoking these roles. Approval must be performed
by a logged-in officer and stores the officer UUID and timestamp. An ID card cannot be issued or validated
before approval.

Right-clicking the management device as an authorized officer opens the immigration GUI. It lists online
citizens by roleplay name and provides buttons for reviewing, approving, and issuing their documents.
Online players without a record are listed separately. Selecting `Bürgerakte anlegen` starts an eight-step
GUI wizard for first name, last name, birth date, birth place, birth country, nationality, gender, and address.
The resulting identity remains pending until a second explicit approval action.

Selecting an existing citizen opens an editable citizen record. Every data tile launches a validated input
form. Staff can change all personal fields, approve or revoke immigration, and issue a replacement ID from
the same screen. Revoking approval immediately invalidates previously issued cards during server validation.

The placeable, horizontally rotatable administration computer opens the same role-aware GUI on right-click.
Authorized staff receive the immigration workstation; citizens receive their personal management hub.

## Currency configuration

The structured files below `config/TerraNexus/` are created on first server start. Currency and immigration
number settings can be
changed there; their defaults are `TerraNexus Euro` and `TN€`.
