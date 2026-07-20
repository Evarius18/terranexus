# TerraNexus management system

The citizen management device opens a shared server-authoritative GUI with citizen, bank, institution,
and property sections. Account names are only used for the technical UUID association during initial
immigration; established citizens are displayed by roleplay name and citizen number.

## Development access

An operator can run `/tnadmin test-access` to grant their own player all immigration roles plus the land registrar role and receive
the management device. `/tnadmin test-access <player>` targets another online tester. Use
`/tnadmin remove-test-access` (or its player variant) afterward. These commands require permission level 2
and do not weaken the normal role checks inside any management action.
The command also grants the explicitly marked `tn_admin_test` development role. It provides full bank,
institution, personnel and finance access for testing and is removed by `/tnadmin remove-test-access`.

## Economy

Balances use integer minor units and are stored in `terranexus_economy`. The currency name and symbol are
configured in `config/TerraNexus/economy.json`. The bank GUI can switch between personal and authorized institution
accounts. Transfers target approved citizens by RP name or institutions by their official name and are applied
atomically. Operators can bootstrap funds with
`/economy deposit <player> <cents>`.

Accounts always start at zero and have persistent random account numbers, freeze status and a transaction
journal. Authorized bank employees can search accounts, inspect balances and postings, perform logged cash
operations and freeze accounts. Every transfer stores purpose, actor, institution, type, status and resulting
balances.

## Institutions

Civil registrars and operators can create institutions through a guided GUI. An institution has a UUID,
type, owner, member-role map, and its own persistent economy account. The current GUI supports creation,
listing, account display, and adding online members. Fixed-value fields such as institution type and citizen
gender use XP-free click-selection menus; the anvil input is reserved for validated free text.

Institutions now have exactly one transferable owner and persistent employee records. The central role matrix
supports Owner, Director, Manager, Auditor, Accountant, HR and Employee. Personnel screens cover hiring,
dismissal, role changes, entry dates, personnel notes, salary configuration and scheduled payroll. Payroll
uses the existing server scheduler and `salary.json`; failed payments are logged and notified.

See `docs/bank-und-institutionen.md` for the German operating and permissions guide.

## Properties

The building authority uses tiered `land_surveyor`, `land_clerk`, and `land_administrator` roles; the legacy
`land_registrar` role remains a full-access compatibility role. The property GUI supports three region types:

- complete chunks;
- 3D cuboids using two current-position points;
- extruded 2D polygons using three or more current-position points, suitable for diagonal roads.

The 3x3 preview shows claimed/free chunks around the player. Every existing shape can be reopened in a GUI
editor. Editors can add/remove vertices, move the nearest vertex to their current position, undo changes, and
expand or shrink the outline. A particle preview renders the exact border in the world before saving. Polygon
overlap is evaluated at block resolution rather than merely rejecting intersecting bounding boxes.

Properties can be assigned to approved citizens or institutions. Block breaking and placement are denied
inside property regions unless the player owns the property, manages the owning institution, or has operator bypass.

The ownership layer includes atomic property sales, tenant-approved leases, deposits, recurring rent,
administrative hierarchies and accounts, detailed public/per-citizen permission matrices, and offline citizen
selection. See `docs/landlord-und-geldsystem.md` for the German user guide.
