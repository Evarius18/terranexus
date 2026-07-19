# TerraNexus identity system

Identity data is stored server-side in the overworld persistent state. The Minecraft UUID is the
technical owner key; a collision-checked random `TN-########` number is the stable roleplay citizen number.
ID-card item data is only a reference. Using the card always resolves the current server record.

## Commands

Administrative commands require permission level 2.

```text
/identity create <player> <firstName> <lastName> <birthDate> <birthPlace> <birthCountry> <nationality>
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
/identity create Steve Max Mustermann 1990-05-23 Berlin Deutschland deutsch
/identity set Steve adresse Hauptstraße 12, TerraNexus
/identity set Steve geschlecht divers
/identity issue Steve
```

The initial `create` arguments are single words. Multi-word places, countries, and names can be corrected
afterward with `/identity set`.

## Immigration workflow

New identities start without immigration approval. Only operators or players assigned the
`civil_registrar`, `immigration_officer`, or `supporter` role can process them. Approval must be performed
by a logged-in officer and stores the officer UUID and timestamp. An ID card cannot be issued or validated
before approval.

Right-clicking the management device as an authorized officer opens the immigration GUI. It lists online
citizens by roleplay name and provides buttons for reviewing, approving, and issuing their documents.
Online players without a record are listed separately. Selecting `Bürgerakte anlegen` starts an eight-step
GUI wizard for first name, last name, birth date, birth place, birth country, nationality, gender, and address.
The resulting identity remains pending until a second explicit approval action.

## Currency configuration

`config/terranexus.json` is created on first server start. `currencyName` and `currencySymbol` can be
changed there; their defaults are `TerraNexus Euro` and `TN€`.
