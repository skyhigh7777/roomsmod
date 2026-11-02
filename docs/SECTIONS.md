> The old section A was renamed to **B** (id `b`, rooms `B-001`, entities B-20/B-40). Entity registry ids stay `roomsmod:a20` / `roomsmod:a40` for save compatibility.
> The id `a` now belongs to the new section **A (Abyss)**; the old `a` → `b` alias was removed (saves older than section-table version 2 still map `a` to `b` on load).
> Section **GK** was renamed to **S** (id `s`, rooms `S-001`, entities S-10 … S-300, spawn ids `s10` … `s300`). `gk` is still accepted as a hidden alias (old saves / commands).
> Java class / constant names (`GKRooms`, `Sections.GK`, `entity/gk/*`, `RoomsMod.GK_*`) and entity registry ids (`roomsmod:gk_*`) stay unchanged for save compatibility.

# Sections (themes)

A *section* is a run of rooms that share a look and a rusher table. It's defined in Java as a
`SectionTheme` record (`world/section/SectionTheme.java`) and registered in `world/section/Sections.java`.
Registered sections: `B` (`Sections.B`), holding the values that used to be hardcoded in
`RoomGenerator` / `DoorEventHandler`, and `V` (`Sections.V`, Village, trial: no rushers, B's room geometry).

Records were chosen over JSON because a theme references blocks, entity spawners and code
(`RusherSpawn.spawn`), and this way the B output stays bit-for-bit identical with no codec/registry-order issues.
Moving to data-driven JSON later just means adding a codec and loading themes in a reload listener.

## What a theme holds
| Field | Meaning |
|---|---|
| `id`, `prefix` | internal id and room-number prefix (`B` → `B-020`, number is section-local) |
| `palette` | floor, wall, ceiling, door block, light |
| `shapeWeights` | weights for `BOX, CORRIDOR, HALL, TALL, L_SHAPE` (all 1 = uniform) |
| `furniture` | wall shelves, desks, pillars and their counts |
| `cover` | cover chance/count, per-shape kind weights (`BOOKSHELF_ROW, PILLAR_2X2, PARTITION, CRATES`), blocks |
| `hidingMix`, `hidingMin/Extra` | hiding spot types with weights (cumulative, in list order) and count |
| `rushers` | `RusherSpawn(name, minRoom, chance, alive, spawn)` (`minRoom` = section-local number), rolled independently in list order |
| `sign` | wall sign block, text color, glow |
| `lightFadeStart/Step` | progressive darkness: tier = (localRoom - start) / step, see `RoomLighting` (lanterns → fewer lanterns → soul lanterns → candles) |

## Section switching
`RoomsState.sections` is a list of `(themeId, startRoom)` (saved with the world).

### Fixed length (500 rooms)
Every section is `Sections.SECTION_LENGTH` = 500 rooms, in the order `Sections.ORDER` = `v, b, u, s, h, f, a` (H and F are locked by default and skipped while locked, see below).
The default table is `Sections.fixedChain("v")` = `[(v,0), (b,501), (u,1001), (s,1501), (a,2001)]`, so `V-500` is followed by `B-001`, and so on.
`fixedChain(id)` starts `id` at global room 1 (the first section of `ORDER` at 0, the lobby) and chains the rest of `ORDER` every 500 rooms;
`chainFrom(id, start)` does the same from an arbitrary global room (used by `/rooms section`).
After `A-500` the last section simply continues (`A-501`, `A-502`, …).
Saves with `sectionsVersion` < 2 (the old B → V → GK → U order) are rebuilt on load the same way as below.
Saves without the `sectionsFixed` flag are migrated on load: the section that starts at global room ≤ 1 becomes the start of a fixed chain
(manual mid-run switches from older saves are dropped; a debug log line shows old → new table).
`Sections.forRoom(state, room)` picks the last entry with `startRoom <= room`; room numbers on signs and in
debug use that section's prefix.

### Section-local numbering
Room numbers restart at 1 in every section: `Sections.localRoom(state, room) = room - max(startRoom, 1) + 1`.
The first section of `ORDER` (V) starts at 0 where room 0 is the lobby, so its first numbered room (global 1) is `V-001`.
Room 0 (the lobby) uses that section's door/sign style; any other start section starts at global room 1. A section added with `addSection("v", 100)` shows global room 100 as `V-001`, 101 as `V-002`, ...
`Sections.formatRoom` (door number signs, `RoomTracker`, debug logs, `/rooms debug section`) uses the local
number; `/rooms debug section` prints both `global` and `local`. Internals (`currentRoom`, seeds, light fade,
rusher `startRoom` waypoint indices, `/rooms test gen N`) still use global indices.

**Rusher thresholds are section-local:** `RusherSpawn.minRoom` is compared against the local number, so
B-20 (`minRoom 20`) and B-40 (`minRoom 40`) can roll from `X-020` / `X-040` of whichever section owns them.
For B this is identical to before (local == global). Add a switch with `state.addSection("v", 100)`.
Debug: `/rooms debug section` prints the current section and table; generation logs `section change at room N`.

## Commands (op)
- `/rooms section <id>` or `/rooms section <id> now`: the next room to be generated (`currentRoom + 1`) starts section `<id>`,
  followed by the next sections of `ORDER` every 500 rooms (`RoomsState.chainFrom`); entries from that room on are replaced.
  The already placed number sign over the next exit door is rewritten (e.g. `V-001`).
- `/rooms section <id> <startRoom>`: same, starting at global room `startRoom`.
- `/rooms section reset`: back to the default fixed chain (V → B → U → S → A). The table is saved with the world and is **not** cleared by `/rooms enter`.
- Try V from scratch: `/rooms enter`, `/rooms section v now`, open the lobby door.

Light fade (`lightFadeStart/Step`) uses the section-local number (identical for B).

## V (Village), as registered
```java
// in Sections.java
public static final SectionTheme V = register(new SectionTheme(
        "v", "V",
        new SectionTheme.Palette(
                Blocks.DIRT_PATH::defaultBlockState, Blocks.OAK_PLANKS::defaultBlockState, Blocks.SPRUCE_PLANKS::defaultBlockState,
                () -> Blocks.SPRUCE_DOOR,
                () -> Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)),
        new int[]{3, 0, 2, 1, 2},                       // no corridors, more halls/L rooms
        new SectionTheme.Furniture(
                () -> Blocks.BARREL, 1, 3,
                () -> Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), () -> Blocks.FLOWER_POT, 3,
                () -> Blocks.STRIPPED_OAK_LOG, () -> Blocks.OAK_LOG),
        new SectionTheme.Cover(0.8f, 1, 3, Map.of(), new int[]{1, 2, 1, 3},
                Blocks.BOOKSHELF::defaultBlockState, Blocks.OAK_LOG::defaultBlockState, Blocks.OAK_PLANKS::defaultBlockState,
                Blocks.OAK_PLANKS::defaultBlockState, Blocks.HAY_BLOCK::defaultBlockState, Blocks.BARREL::defaultBlockState),
        List.of(new SectionTheme.Weighted<>(SectionTheme.HidingType.TABLE, 0.6f),
                new SectionTheme.Weighted<>(SectionTheme.HidingType.LOCKER, 0.4f)),
        3, 2,
        List.of(/* new SectionTheme.RusherSpawn("V-xx", minRoom, chance, alive, spawn) */),
        new SectionTheme.SignStyle(() -> Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false),
        0, 5));
// rushers: List.of() for now. Start it with /rooms section v now, or RoomsState.get(level).addSection("v", 100);
```
Checklist for a new section:
1. Add the theme to `Sections` (unique `id`).
2. New hiding type: add it to `SectionTheme.HidingType` and handle it in `RoomGenerator.placeHiding`.
3. New rusher: entity + `spawnXxx`/`isXxxAlive` in `DoorEventHandler`, then a `RusherSpawn` entry.
4. Decide where the section starts and call `addSection`.
5. Run the harness: `/rooms test gen N` must report `check RESULT PASS`.

Not themed yet: room geometry (sizes per shape), the lobby decor, and the locker/table blocks themselves.

## V (Village) rooms
V uses its own generator (`world/VillageRooms.java`), not B's shapes: small houses (interiors 5-9),
layouts SINGLE / TWO_ROOM (front+back, inner door) / SIDE_ROOM (side room via inner door) and
TWO_STORY (~22%, stairs; ascending at base height -> exit on 2nd floor, descending when already raised
-> enter 2nd floor, exit ground floor, so floor height alternates base / base+4). The next room origin
is the exit door's floor (door y - 1). Path waypoints include every stair step (rise, then advance).

## Lobby
`RoomGenerator.buildLobbyInterior` holds the lobby interior (skylights, sea lanterns). Keep the centre path x=9..11 clear.
The lobby is rebuilt on every lobby entry (`/rooms enter`, first join, respawn, `/rooms to`, lobby GO), which also resets the run.

### Lobby panels (`world/LobbyPanels.java`)
- Left wall (+X wall, x=19, facing the exit door): SECTION / − / ＋ / GO buttons set into the wall at y=2 with waxed glowing signs above;
  the selection sign shows e.g. `S-150`. Sections cycle over `Sections.lobbyIds()` (`ORDER` first, then other registered non-test sections);
  room steps 1, 50 … 300, 400, 500. Selection is shared server-wide (in memory). GO = `RoomsState.setChain(s)` (s from room 1, then the following sections every 500 rooms) + `/rooms to n`.
- Door wall (x=14, z=31): deepest generated room (`roomPaths.size()-1`), refreshed by `RoomGenerator.generateNextRoom`; right-click teleports (no reset).
- Panels are exempt from the rooms-dimension block-use rule and cannot be broken (`DoorEventHandler`).
- `/rooms to` and GO generate rooms over several ticks (`world/RoomsTravel.java`, 4 rooms/tick, max 500), one job at a time.

## S (School, formerly GK) rooms
S uses its own generator (`world/GKRooms.java`): interiors between B and V (closer to B), height 5.
Layouts: CLASSROOM (11-15 x 11-17, desk grid, blackboard), HALLWAY (5-7 x 15-21, locker-heavy),
PREP_ROOM (9-12 classroom + 5-7 side room through an inner door; exit in the side room).
Every S room gets at least one locker and one hiding table (forced placement as a fallback; a WARN is logged if
that ever fails). Paths are kept 3 wide; the exit is always on the max-Z wall; overlapping plans fall back to a 9x9 classroom.
Progression: after U. Start it with `/rooms section s now` (or `/rooms section s <globalRoom>`), rooms read `S-001`...
S has 15 entities in `entity/gk`; at most 2 entities spawn per door roll in S / B / U / A (`DoorEventHandler.GK_MAX_PER_ROLL`).
S entities live in `entity/gk` and despawn after at most 100 s (`GKEntity.MAX_LIFE_TICKS`, below the 120 s hard cap).

## A (Abyss) rooms
A uses `world/AbyssRooms.java`. There are no doors: the player falls down a single vertical shaft and is looped.
- The first A room builds, past the previous exit door, a small vestibule (11x11, floor hole) over a shaft with a 5x5 inner area
  and 3-block walls. The shaft is `AbyssRooms.SEGMENTS` = 4 layers of `L` = 12 blocks plus a 4-block gap and a black floor
  (fits below y=1 in the -64 world floor). The shaft position is saved in `RoomsState.abyss` (cleared by a lobby rebuild).
- Loop: when a player's feet enter layer 2, the server teleports them `L` blocks up with a fully relative teleport
  (position, rotation and velocity kept) and the player advances one room. If that room is new, `RoomGenerator.generateNextRoom`
  appends an A room (same shaft box, path = straight down through layer 1) and the normal spawn roll runs
  (`DoorEventHandler.onRoomAdvanced`, also hard-mode notice). Each layer k shows the content of room `lead + k - 1`, so on every
  loop the content shifts up by one layer; only the changed blocks are rewritten and they are sent to nearby clients before the teleport.
- Alcoves: every 2-3 rooms (seeded), on a random wall at a random height inside the layer: 3 wide, 2 deep, 3 high, a 1-block ledge
  into the shaft, two lockers and a lantern. Each layer has a room number sign (`A-002`, …) and four soul torches.
- One way: after the first loop the vestibule hole is sealed.
- No fall damage inside the shaft footprint (`LivingFallEvent` cancelled, fall distance reset every tick).
  Fall speed is capped client-side (`client/AbyssClient`, 0.6 blocks/tick ≈ 12 blocks/s, 0.3 while sneaking); the server tells the
  client when it is inside the shaft with `AbyssRooms.AbyssPayload`.
- Spawn table: only A-native entities (`entity/a`, `VEntity.isAbyssNative`): A-25, A-50, A-90, A-130, A-180, A-200, A-250, A-300, A-350,
  A-450, A-525, A-600, A-666, A-750, A-800, A-1000 (minRoom = section-local number). Hard mode in A uses A's own table (with the
  hard-mode buffs), not the combined table. Pairs whose avoidance rules clash never coexist (`AbyssEnemy.incompatible`, checked both ways).
- Later A enemies (details in each class javadoc; damage normal / hard):
  - A-525 blackout (from A-525, 3%): a bell, then the screen goes black except glowing particles (`AbyssFx.DarkPayload`,
    `client/AbyssFxClient`); an orange ring of particles marks a strike zone around where you were; leave it (6 / 9). Only coexists with A-1000.
  - A-600 updraft (A-600, 3%): wind sound + rising particles, then the fall is pinned to 0.06 blocks/tick (`AbyssRooms.hold`) and a thing rises
    from below; hide in an alcove/locker (one is required within reach to spawn) or out-run it sideways until the updraft ends (6 / 10). Solo.
  - A-666 counting voice (A-666, 3.5%): titles 3-2-1 with sounds; at 0, average horizontal speed over 6 ticks > 0.04 hurts (5 / 8). Falling is fine.
  - A-750 repeat (A-750, 3.5%): signs of the next 8 (10) rooms show the same number (`AbyssRooms.setSignOverrides`, `Shaft.signOver`);
    one room with a real alcove shows its real number in red; enter that alcove to break the loop. Passing it by a room or timing out hurts (6 / 9).
  - A-800 floors (A-800, 4%): a slab floor across the 5x5 column 13 (11) blocks below; cobbled-deepslate (cracked) tiles crumble just before you
    reach them, smooth-stone tiles hurt and then shatter (5 / 8). Always one 2x2 cracked patch. Blocks are tracked in virtual coordinates,
    re-placed after each loop and always removed (also after a reload, from the saved positions).
  - A-1000 the bottom (A-1000, 4%): a shaft-wide mass falls from above at 0.235 (0.24) blocks/tick, a bit slower than full-speed falling;
    sneaking, standing or hiding lets it catch up (10 / 14). Cannot be hidden from. Distance on the action bar.
- No batteries / bandages are placed in A rooms. `SafeSpot.find` returns the vestibule (first room) or the top of layer 1 (others),
  so `/rooms to`, lobby GO and the deepest-room panel work.
- If the section table is changed so that the room after an A room is not A (`/rooms section <id> now` while falling),
  the next loop builds that room beside the shaft and moves the player into it.
- Debug: every loop logs `abyss loop player=… loops=… room=… depth=…` (chat every 10 loops); layer refreshes log `abyss refresh`.
- Multiplayer: the shaft layout follows the deepest player; players behind still loop and count their own rooms, but see the leader's layout.

## U (corridor) rooms
U uses its own generator (`world/CorridorRooms.java`): straight corridors only, inner width 5 (7 with walls), height 5,
entrance and exit always on the centre line x=10 (exit on the max-Z wall), so rooms never overlap.
- Lengths (inner depth): SHORT 24 (25%), STD 36 (45%), LONG 52 (25%), VLONG 72 (5%, only from `U-250`; earlier it becomes LONG).
- Variants: PLAIN, PILLARED (pillars on both sides every 8 blocks, cover pockets), ALCOVE (hiding every 8 blocks), DIM (half the lights).
- Hiding: lockers set into the wall (niche) or tables against the wall; the centre 3 lanes (x=9..11) stay clear.
  Spacing ≤ 16 blocks (`MAX_HIDE_GAP`, also to the exit), at least 2 per room (`MIN_HIDES`), at least one locker.
- Light fade: from U-001, one tier every 10 rooms.
U has 5 entities in `entity/u` (U-20, U-60, U-120, U-200, U-300). They are movement types (no gimmicks) built on `URusher`
(`VPathWalker` with acceleration/deceleration). When one spawns outside U (hard mode's combined table) it is softened
(speed x0.85, grace x1.25, higher minimum speed so it never crawls through corners).

## Movement: acceleration / deceleration
`VPathWalker` supports optional `accel()` / `decel()` (blocks/tick²; default 0 = constant speed as before).
With `accel() > 0` the walker ramps up from `minMoveSpeed()` to `maxSpeed()`; with `decel() > 0` and `brakeAtPathEnd()`
it brakes before the path end (a turnaround point) so it can stop there. Corners are never braked for.
`setPath(level, from, to)` re-targets the path without teleporting (used for turnarounds of N rooms).

## Hard mode (all sections)
Constants in `world/HardMode.java`: max 3 per door roll, spawn chance x1.5 (capped at 1), path walkers' grace x0.8 and speed x1.1
(B-20/B-40 too), entities that turn around get one more turnaround (B-40, BRusher rebounds, S-300, U-60, U-300), and when a rusher
finishes its run there is a 25% chance of another roll on the next tick (`HardMode.chainReroll`). The buffs are fixed at spawn time
(`hardSpawn`, saved). Category/exclusion rules (`SpawnCategories`, e.g. S-270 KEEP_MOVING) are unchanged.

## PLACEHOLDER (test only)
`Sections.PLACEHOLDER` (id `placeholder`, rooms `PLACEHOLDER-001`) is listed in `Sections.TEST_ONLY`: it is not in `ORDER`
(no fixed chain), not shown on the lobby panel (`Sections.lobbyIds()`), and its spawn table is empty, so it never
contributes to normal or hard-mode rolls. Start it with `/rooms section placeholder now`; it then continues forever.
Rooms (`world/PlaceholderRooms.java`): an 11x11 floor with no walls and no ceiling, one hiding table and two lockers.
Progression still uses the normal exit door: a free-standing door frame (two pillars + lintel, for the number sign) stands just past the +Z edge.
Fall protection: invisible barriers (3 high) around the floor edge, skipping cells occupied by the previous room's door/wall
and the door frame. Light comes from invisible `minecraft:light` blocks.
Entities `PLACEHOLDER-32767`, `PLACEHOLDER-65535`, `PLACEHOLDER-2147483648` (`entity/placeholder/PlaceholderEntity`) only spawn via
`/rooms spawn placeholder32767|placeholder65535|placeholder2147483648`; `PLACEHOLDER-65535` and `PLACEHOLDER-2147483648` stand still in the middle of the player's room
(no AI, no damage, no sound) and despawn after the usual 2-minute cap. `PLACEHOLDER-32767` has its own behaviour
(`Placeholder32767Entity`, see the class javadoc; state is saved in NBT, positions snap without client interpolation). Graphics: single-colour squares (red / green / blue).


## H (Hospital ward)
`Sections.H` (id `h`, rooms `H-001` … `H-500`), generator `world/HospitalRooms.java`. Position in `ORDER`: after S, before A.
- **Locked by default** (`Sections.LOCKED_BY_DEFAULT` = `placeholder`, `h`, `f`). While it is locked, H is also left out of the
  progression (`RoomsState.outOfProgression`), so the default table stays `V → B → U → S → A` (`[v0, b501, u801, s1301, a1801]`).
  `/rooms admin section h unlock` relinks the part of the table after the current section (`RoomsState.relinkProgression`)
  to `… S → H → A` (`h1801`, `a2301`); locking it again removes it. Rooms that are already generated never change their numbers
  (the relink is skipped and logged if they would). Other sections keep the old behaviour: locking them only hides them from the panel / `section`.
- Old 500-length tables are migrated with the pre-H order (`LEGACY_ORDER`), so the length migration never inserts H.
- Rooms: straight wards (entrance and exit on x=10, exit on the max-Z wall). Inner width 13 (x=4..16), height 4.
  White tile floor (white concrete / smooth quartz checker, some dull tiles), tiled walls (quartz bricks up to y=2, light blue terracotta stripe, white above).
  Bays of 4 blocks along both walls: a curtain partition (wool, 3 long, 3 high, with tears and cobwebs) and a chain curtain rail,
  bedside cabinet (sometimes an unlit candle), white / light gray bed with the head at the wall, IV stand (iron bars + chain).
  Some bays have the aisle-side curtain drawn, some beds are removed. Lights are end rods over the aisle (x=8..12), with the usual light fade (from H-001, one tier every 8 rooms).
- Lengths (bays): SHORT 4 (25%), STD 6 (45%), LONG 8 (25%), VLONG 10 (5%, only from `H-250`). Variants: WARD, CURTAINED (more drawn curtains), DIM (half the lights, more tears and removed beds).
- Hiding: wall-niche lockers next to the bed (foot side) or a hiding table in place of a bed, alternating sides every 2–3 bays;
  gap ≤ 16 blocks, at least 2 per room, at least one locker. If the ward would overlap an older room, the bed row on that side is dropped (wall at x=7 / x=13).
- Items: bandages lie in 30% of H rooms (`RoomGenerator.HOSPITAL_BANDAGE_ROOM_CHANCE`, 12% elsewhere). Batteries as usual.
- Spawns (normal mode): **only the 4 H entities** (`entity/h`, below). The number in an entity's name is not its start room here.
  Table (minRoom, chance): H-30 (5, .22), H-90 (35, .25), H-150 (100, .30), H-260 (200, .40).
  P(at least one) by depth: H-1..4 0, H-5 0.22, H-35 0.42, H-100 0.59, H-200 and deeper 0.75
  (measured with 400 natural rolls each: H-5 0.24, H-35 0.44, H-100 0.61, H-200 0.72, H-300 0.76, H-450 0.75).
  Per-roll cap `GK_MAX_PER_ROLL` (2); H-90 and H-150 share the exclusion group `h_ward` (at most one of them).
  Hard mode: like every non-A section, H rolls the combined `Sections.hardTable()` (all sections' entities incl. the H ones with the minRooms above, chance x1.2, cap 2),
  so B / S / U / V entities do appear in H in hard mode only. The H entities can also appear in other sections in hard mode (`homeSection` is `h`); 3!3!2 can imitate their sounds.

### H entities (`entity/h`)
All are `VEntity` (no physics, invulnerable, max 100 s like S), billboard textures `textures/entity/h30|h90|h150|h260.png`, sounds `h30_drag`, `h90_step`, `h90_check`,
`h150_rustle`, `h260_rattle`. Radar / scanner list them like every other MONSTER of this mod (name = `vName()`). `/rooms admin spawn h30|h90|h150|h260`.
- **H-30** (`HPatientEntity`, wandering patient): bandaged patient in a pale blue gown with an IV stand. `VPathWalker` from one room behind the player to the newest room
  (follows into new rooms), 0.15 blocks/tick (slower than walking), sometimes stops for 2 s (no movement, no hits). Hits (25) players it can see within 5 blocks
  (`RusherHits`: hiding or cover saves you). Get ahead of it or hide until it has passed. Category HIDE_REQUIRED. Hard: speed / grace like other walkers.
- **H-90** (`HNurseEntity`, night nurse): white uniform and cap, dark face. Enters the player's room, walks the path of that room and the next at 0.11 blocks/tick,
  stopping every 7 blocks for 1.5 s (cart sound), then leaves. Players that are not hiding and visible within 10 blocks are hit (12) and slowed.
  Hidden players within 9 blocks who keep looking at her (view vector within ~16°) build up a gaze counter (actionbar "The nurse notices your gaze.");
  at 30 ticks (hard 20) she pulls them out of the hiding spot (`LockerManager.exit`) and hits (30), then leaves. Looking away lowers the counter.
  Hide and look away. Category HIDE_REQUIRED, exclusion group `h_ward` (never together with H-150).
- **H-150** (`HCurtainEntity`, behind the curtain): lurks inside a curtain (white / light gray wool, 2 high, open on one side, within 3 blocks of the path) of the newest room
  (or the player's room); outside H, on a free floor spot near the path. Invisible until it notices you; the curtain rustles now and then.
  A player who is not hiding within 3 blocks: sprinting = noticed at once, walking = noticed after 0.5 s, crouching = never.
  Once noticed it shows itself (actionbar "The curtain moves.") and lunges after 1 s (hard 0.7 s): hit (35) + slowness for players within 4.5 blocks it can see from the curtain's open side,
  unless they are hiding. Players hiding within 2.5 blocks of it are dragged out after 3 s (rustling) and hit (35). Crouch past rustling curtains and do not hide next to them.
  Uncategorized, exclusion group `h_ward`. 60 s lifetime.
- **H-260** (`HGurneyEntity`, runaway gurney): sheet-covered stretcher. `URusher` (home section `h`): starts 4 rooms behind with a rattling wheel loop,
  1.3 blocks/tick with acceleration, bounces once 1 room ahead and runs 3 rooms back (hard +1 bounce). Hide (hit range 20, damage 40). Category HIDE_REQUIRED.
- Start it: `/rooms admin section h unlock`, then the lobby panel (`H-001` … `H-500`, GO) or `/rooms admin tp h <n>`.

## F (Flooded area)
`Sections.F` (id `f`, rooms `F-001` … `F-500`), generator `world/FloodedRooms.java`. Position in `ORDER`: after H, before A.
- **Locked by default** with the same handling as H (`outOfProgression` / `relinkProgression`): the default table stays `V → B → U → S → A`.
  `/rooms admin section f unlock` gives `… S → F → A` (`f1801`, `a2301`); with H also unlocked `… S → H → F → A` (`h1801`, `f2301`, `a2801`).
  Locking it again removes it. Old 500-length tables are migrated with `LEGACY_ORDER` (F is never inserted by the migration).
- Water: the inner floor (y=0) is **waterlogged stone brick bottom slabs**, so the water surface is ankle deep (player stands at y=0.5).
  No water is placed at y ≥ 1, so the path, doors and hiding spots are never blocked and water never flows. The outer floor ring,
  pillar bases and the floor under candle lights are full blocks. `SafeSpot.isSafe` accepts a bottom slab below.
  Air supply is reset every 5 ticks inside F rooms (`FloodedRooms.onPlayerTick`), so drowning is impossible; occasional drip sounds.
- Stamina: sprinting drains `Stamina.DRAIN × FloodedRooms.STAMINA_DRAIN_MUL` (1.5) inside F rooms (≈ 5.3 s instead of 8 s from full). Regeneration is unchanged.
- Rooms: straight (entrance / exit on x=10). Shapes CULVERT (inner width 5), HALL (9), CISTERN (11, pillars every 5 blocks), narrower fallback on overlap.
  Height 4. Mossy / cracked stone brick walls, cracked stone brick ceiling with chains and glow lichen (off the path), stone brick pillars in HALL / CISTERN.
  Lights: hanging lanterns and candle fixtures with the usual light fade (one tier every 8 rooms).
- Lengths: SHORT 20, STD 30, LONG 42, VLONG 56 (only from `F-250`).
- Hiding: lockers 0.65 / tables 0.35 (tables are never waterlogged), U-style spacing, at least 2 per room, at least one locker.
- Items: bandages and batteries as usual.
- Spawns (normal mode): **only the 4 F entities** (`entity/f`, below). The number in an F entity's name is its start room.
  Table (minRoom, chance): F-5 (5, .22), F-35 (35, .25), F-100 (100, .30), F-200 (200, .40) — the same curve as H.
  P(at least one) by depth: F-1..4 0, F-5 0.22, F-35 0.42, F-100 0.59, F-200 and deeper 0.75
  (measured with 400 natural rolls each: F-5 0.21, F-20 0.22, F-35 0.44, F-60 0.38, F-100 0.61, F-150 0.60, F-200 0.76, F-300 0.75, F-450 0.76).
  Per-roll cap `GK_MAX_PER_ROLL` (2); F-5 and F-35 share the exclusion group `f_water` (stand still vs keep moving: at most one of them).
  Hard mode: like every non-A section, F rolls the combined `Sections.hardTable()` (so B / S / U / V / H entities appear in F in hard mode only),
  and the F entities can appear in other sections (`homeSection` is `f`); 3!3!2 can imitate their sounds.

### F entities (`entity/f`)
All are `VEntity` (no physics, invulnerable, max 100 s), billboard textures `textures/entity/f5|f35|f100|f200.png`, sounds `f5_hum`, `f35_bubble`,
`f100_gurgle`, `f200_wave`. Radar / scanner list them like every other MONSTER of this mod (name = `vName()`). `/rooms admin spawn f5|f35|f100|f200`.
- **F-5** (`FListenerEntity`, the listener): a grey bald head with huge ears sticking out of the water. `GKRoomWatcher` in the opened room:
  low hum for 2.5 s (hard 1.75 s), then listens for 7 s (actionbar "Something is listening to the water."), then sinks.
  While it listens, players in that room or the room before it (or within 16 blocks) who are not hiding make noise:
  sprinting or jumping = every tick, walking = every other tick, crouch-walking or standing still = none. At 24 noise (hard 18) it strikes (30) and leaves
  (≈ 1.2 s of sprinting or 2.4 s of walking). Stop, crouch, or hide. Uncategorized, exclusion group `f_water`.
- **F-35** (`FSurfacerEntity`, the surfacer): long black wet hair, a pale face and two hands. Hidden under the water (drawn only while surfaced).
  After 2 s the water bubbles at the target's feet (actionbar "The water bubbles at your feet." the first time); 1.5 s later (hard 1.1 s) it bursts out at that spot:
  players within 1.8 blocks of the bubbles who are not hiding are hit (20) and slowed. It sinks, then bubbles again under the target: 5 times (hard 6), then leaves.
  While the target hides it waits; after 10 s of hiding it leaves. Keep moving when bubbles appear, or hide. Uncategorized, exclusion group `f_water`.
- **F-100** (`FDrownedEntity`, the drowned): a bloated blue-grey figure wrapped in weed, dripping. `VPathWalker` **from the front**: from the next room's exit
  back through the player's room to the entrance of the room 2 behind, 0.13 blocks/tick, then leaves. Hits (30) players it can see within 4 blocks (`RusherHits`).
  Players within 6 blocks who are not hiding get slowness II (actionbar "The water drags at your feet."), so slipping past it is hard. Hide. Category HIDE_REQUIRED.
- **F-200** (`FUndertowEntity`, the undertow): a dark wall of water with a foam crest and a gaping face. `URusher` (home section `f`): starts 5 rooms behind with a
  rushing-water loop, 1.5 blocks/tick with acceleration, runs to 1 room ahead (no bounce). Hit range 22, damage 40.
  **Tables do not protect** from it: water floods under them (`HidingRules` exposure `FUndertowEntity.floodsTable`, within 6 blocks even without line of sight). Hide in a locker.
  Category HIDE_REQUIRED.
- Start it: `/rooms admin section f unlock`, then the lobby panel (`F-001` … `F-500`, GO) or `/rooms admin tp f <n>`.

## Player records (`economy/RoomsRecords.java`)

Per-player progress records, stored in the overworld SavedData `roomsmod_records` keyed by UUID (with last known name, so offline
players can be listed). Normal and hard mode are separate. Each mode keeps: deepest room (section + local label, ORDER depth from
`Sections.absoluteDepth`, global index at the time, epoch ms), deepest per section, run count, and deepest no-hit survived run.
Depth is compared by `absoluteDepth` (stable across section tables); sections not in ORDER (PLACEHOLDER) are not recorded.
Only legit progress counts: `Wallet.runFurthest > runStart` (WALK_TOLERANCE jumps restart the run) and not `runNoPay`
(admin `to` / `tp` / `deepest`). Updated every 5 ticks from `RoomsRun.onTick`; runs and no-hit counted in `RoomsRun.settle`.
"自己ベスト更新：X-NNN" shows on the actionbar once per new section entered beyond the previous best, and in chat at settle.
From-001 records (`Mode.fromOne`, per ORDER section): only runs whose start room (lobby start = room 1) is that section's local 001.
Keeps the deepest local room reached inside that section (a run that leaves the section counts as its last room) and `clears`,
the number of runs that reached the section's last room (counted once per run at settle). A is endless, so A keeps only the
deepest, with no clear count. A run from V-001 that continues into B counts for V only.
Longest streak (`Mode.streak`, any start): the most rooms in one run (`runFurthest - runStart`) with from/to labels
("LOBBY" when started from the lobby), e.g. `A-500 → A-1199（699部屋）`. Same legitimacy rules; normal and hard separate.
Older saves have no `fromOne` / `streak` keys; those start empty and the existing records are kept.
Commands: `/rooms record`, `/rooms record <player>`, `/rooms record top`, `/rooms record top <v|b|u|s|h|f|a>` (from-001 ranking),
`/rooms record top streak` (streak ranking), `/rooms admin record reset <player>`.
