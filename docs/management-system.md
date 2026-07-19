# TerraNexus management system

The citizen management device opens a shared server-authoritative GUI with citizen, bank, institution,
and property sections. Account names are only used for the technical UUID association during initial
immigration; established citizens are displayed by roleplay name and citizen number.

## Development access

An operator can run `/tnadmin test-access` to grant their own player all immigration roles plus the land registrar role and receive
the management device. `/tnadmin test-access <player>` targets another online tester. Use
`/tnadmin remove-test-access` (or its player variant) afterward. These commands require permission level 2
and do not weaken the normal role checks inside any management action.

## Economy

Balances use integer minor units and are stored in `terranexus_economy`. The currency name and symbol are
configured in `config/terranexus.json`. The bank GUI can switch between personal and authorized institution
accounts. Transfers target approved citizens by RP name or institutions by their official name and are applied
atomically. Operators can bootstrap funds with
`/economy deposit <player> <cents>`.

## Institutions

Civil registrars and operators can create institutions through a guided GUI. An institution has a UUID,
type, owner, member-role map, and its own persistent economy account. The current GUI supports creation,
listing, account display, and adding online members.

## Properties

Only players with the explicit `land_registrar` role create and assign new property. The property GUI supports three region types:

- complete chunks;
- 3D cuboids using two current-position points;
- extruded 2D polygons using three or more current-position points, suitable for diagonal roads.

The 3x3 preview shows claimed/free chunks around the player. Every existing shape can be reopened in a GUI
editor. Editors can add/remove vertices, move the nearest vertex to their current position, undo changes, and
expand or shrink the outline. A particle preview renders the exact border in the world before saving. Polygon
overlap is evaluated at block resolution rather than merely rejecting intersecting bounding boxes.

Properties can be assigned to approved citizens or institutions. Block breaking and placement are denied
inside property regions unless the player owns the property, manages the owning institution, or has operator bypass.

The current implementation is the ownership and geometry foundation. Leases, deposits, detailed member
permission matrices, offline recipient browsing, and sale/rent workflows are the next layer.
