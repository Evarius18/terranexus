# TerraNexus management system

The citizen management device opens a shared server-authoritative GUI with citizen, bank, institution,
and property sections. Account names are only used for the technical UUID association during initial
immigration; established citizens are displayed by roleplay name and citizen number.

## Economy

Balances use integer minor units and are stored in `terranexus_economy`. The currency name and symbol are
configured in `config/terranexus.json`. Players select an online approved citizen by RP name in the bank GUI,
enter an amount, and receive an atomic success/failure result. Operators can bootstrap funds with
`/economy deposit <player> <cents>`.

## Institutions

Civil registrars and operators can create institutions through a guided GUI. An institution has a UUID,
type, owner, member-role map, and its own persistent economy account. The current GUI supports creation,
listing, account display, and adding online members.

## Properties

The property GUI supports three region types:

- complete chunks;
- 3D cuboids using two current-position points;
- extruded 2D polygons using three or more current-position points, suitable for diagonal roads.

The top-left 3x3 preview shows claimed/free chunks around the player. Polygon overlap is evaluated at block
resolution rather than merely rejecting intersecting bounding boxes. Block breaking and placement are denied
inside property regions unless the player owns the property or has operator bypass.

The current implementation is the ownership foundation. Leases, deposits, institution-owned properties,
member permission matrices, offline recipient browsing, and sale/rent workflows are the next layer.
