> Section A was renamed to **B** (id `b`, rooms `B-001`, entities B-20/B-40). `a`, `a20`, `a40` remain as deprecated aliases; saved worlds with `a` load as `b`. Entity registry ids stay `roomsmod:a20` / `roomsmod:a40` for save compatibility.

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
`RoomsState.sections` is a list of `(themeId, startRoom)` (saved with the world, default `[(b, 0)]`).
`Sections.forRoom(state, room)` picks the last entry with `startRoom <= room`; room numbers on signs and in
debug use that section's prefix.

### Section-local numbering
Room numbers restart at 1 in every section: `Sections.localRoom(state, room) = room - max(startRoom, 1) + 1`.
Section B starts at 0 where room 0 is the lobby, so its first numbered room (global 1) is `B-001` — unchanged
from before. A section added with `addSection("v", 100)` shows global room 100 as `V-001`, 101 as `V-002`, ...
`Sections.formatRoom` (door number signs, `RoomTracker`, debug logs, `/rooms debug section`) uses the local
number; `/rooms debug section` prints both `global` and `local`. Internals (`currentRoom`, seeds, light fade,
rusher `startRoom` waypoint indices, `/rooms test gen N`) still use global indices.

**Rusher thresholds are section-local:** `RusherSpawn.minRoom` is compared against the local number, so
B-20 (`minRoom 20`) and B-40 (`minRoom 40`) can roll from `X-020` / `X-040` of whichever section owns them.
For B this is identical to before (local == global). Add a switch with `state.addSection("v", 100)`.
Debug: `/rooms debug section` prints the current section and table; generation logs `section change at room N`.

## Commands (op)
- `/rooms section <id>` or `/rooms section <id> now`: the next room to be generated (`currentRoom + 1`) starts section `<id>`;
  the already placed number sign over the next exit door is rewritten (e.g. `V-001`).
- `/rooms section <id> <startRoom>`: section `<id>` starts at global room `startRoom`.
- `/rooms section reset`: back to B only. The table is saved with the world and is **not** cleared by `/rooms enter`.
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
  the selection sign shows e.g. `GK-150`. Sections cycle over `Sections.ids()` (new sections appear automatically);
  room steps 1, 50 … 300, 400, 500. Selection is shared server-wide (in memory). GO = section table `[(b,0),(s,1)]` + `/rooms to n`.
- Door wall (x=14, z=31): deepest generated room (`roomPaths.size()-1`), refreshed by `RoomGenerator.generateNextRoom`; right-click teleports (no reset).
- Panels are exempt from the rooms-dimension block-use rule and cannot be broken (`DoorEventHandler`).
- `/rooms to` and GO generate rooms over several ticks (`world/RoomsTravel.java`, 4 rooms/tick, max 500), one job at a time.

## GK (Gakkou / school) rooms
GK uses its own generator (`world/GKRooms.java`): interiors between B and V (closer to B), height 5.
Layouts: CLASSROOM (11-15 x 11-17, desk grid, blackboard), HALLWAY (5-7 x 15-21, locker-heavy),
PREP_ROOM (9-12 classroom + 5-7 side room through an inner door; exit in the side room).
Every GK room gets at least one locker and one hiding table (forced placement as a fallback; a WARN is logged if
that ever fails). Paths are kept 3 wide; the exit is always on the max-Z wall; overlapping plans fall back to a 9x9 classroom.
Progression: after V. Start it with `/rooms section gk now` (or `/rooms section gk <globalRoom>`), rooms read `GK-001`...
GK has 15 entities in `entity/gk`; at most 2 GK entities spawn per door roll (`DoorEventHandler.GK_MAX_PER_ROLL`).
GK entities live in `entity/gk` and despawn after at most 100 s (`GKEntity.MAX_LIFE_TICKS`, below the 120 s hard cap).
