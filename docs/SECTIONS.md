# Sections (themes)

A *section* is a run of rooms that share a look and a rusher table. It's defined in Java as a
`SectionTheme` record (`world/section/SectionTheme.java`) and registered in `world/section/Sections.java`.
Right now there is one section, `A` (`Sections.A`), holding the values that used to be hardcoded in
`RoomGenerator` / `DoorEventHandler`.

Records were chosen over JSON because a theme references blocks, entity spawners and code
(`RusherSpawn.spawn`), and this way the A output stays bit-for-bit identical with no codec/registry-order issues.
Moving to data-driven JSON later just means adding a codec and loading themes in a reload listener.

## What a theme holds
| Field | Meaning |
|---|---|
| `id`, `prefix` | internal id and room-number prefix (`A` → `A-020`) |
| `palette` | floor, wall, ceiling, door block, light |
| `shapeWeights` | weights for `BOX, CORRIDOR, HALL, TALL, L_SHAPE` (all 1 = uniform) |
| `furniture` | wall shelves, desks, pillars and their counts |
| `cover` | cover chance/count, per-shape kind weights (`BOOKSHELF_ROW, PILLAR_2X2, PARTITION, CRATES`), blocks |
| `hidingMix`, `hidingMin/Extra` | hiding spot types with weights (cumulative, in list order) and count |
| `rushers` | `RusherSpawn(name, minRoom, chance, alive, spawn)`, rolled independently in list order |
| `sign` | wall sign block, text color, glow |
| `lightFadeStart/Step` | rooms after which lights dim |

## Section switching
`RoomsState.sections` is a list of `(themeId, startRoom)` (saved with the world, default `[(a, 0)]`).
`Sections.forRoom(state, room)` picks the last entry with `startRoom <= room`; room numbers on signs and in
debug use that section's prefix. Add a switch with `state.addSection("v", 100)`.
Debug: `/rooms debug section` prints the current section and table; generation logs `section change at room N`.

## Example: V (Village) skeleton (NOT registered)
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
// and somewhere when a run starts / reaches the section: RoomsState.get(level).addSection("v", 100);
```
Checklist for a new section:
1. Add the theme to `Sections` (unique `id`).
2. New hiding type: add it to `SectionTheme.HidingType` and handle it in `RoomGenerator.placeHiding`.
3. New rusher: entity + `spawnXxx`/`isXxxAlive` in `DoorEventHandler`, then a `RusherSpawn` entry.
4. Decide where the section starts and call `addSection`.
5. Run the harness: `/rooms test gen N` must report `check RESULT PASS`.

Not themed yet: room geometry (sizes per shape), the lobby decor, and the locker/table blocks themselves.
