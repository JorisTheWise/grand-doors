# Grand Doors

A Fabric mod for Minecraft 26.3 with doors one to four blocks wide and two to four blocks
tall: wooden (plain or glazed), stone, iron and copper doors and an iron gate of bars, flat or
with an arched top, as one leaf or as two leaves that open in the middle. Mod id `granddoors`, MIT. It is needed on
the server and on every client.

It stands on its own and needs only Fabric API. All doors are drawn with vanilla textures, so
they follow your resource pack and the mod ships no art.

## Install

Take `granddoors-<version>.jar` from the [releases](https://github.com/JorisTheWise/grand-doors/releases)
and put it in the `mods` folder of the server and of every client, next to Fabric API.
Minecraft 26.3, Fabric Loader 0.19 or newer, Java 25.

## Thanks

The idea of doors built from one block per cell, and of cells that move when a long leaf
opens, comes from [Doorways](https://www.curseforge.com/minecraft/mc-mods/doorways) by André
Freitas (MIT). Grand Doors shares no code or files with it. `SHARING.md` describes what is
different here, for him or anyone else who wants to take it over.

## Blocks

`granddoors:<family>[_arched][_split]_<door|gate|glazed_door>_<w>x<h>`, 1,024 in all.

| Family | Ids start with | Looks like |
| --- | --- | --- |
| the 12 vanilla woods | `oak_`, `spruce_`, `birch_`, `jungle_`, `acacia_`, `dark_oak_`, `mangrove_`, `cherry_`, `pale_oak_`, `bamboo_`, `crimson_`, `warped_` ... `door` | planks in a stripped-log frame |
| 4 stones | `cobblestone_`, `stone_brick_`, `cobbled_deepslate_`, `deepslate_brick_` ... `door` | the stone in a smooth frame |
| iron door | `iron_` ... `door` | an iron plate in a dark frame |
| iron gate | `iron_` ... `gate` | dark bars you can see through, for a graveyard or a park |
| copper, 4 ages | `copper_`, `exposed_copper_`, `weathered_copper_`, `oxidized_copper_` ... `door` | a copper plate in a cut-copper frame; weathers to the next age |
| waxed copper, 4 ages | `waxed_copper_`, `waxed_exposed_copper_`, ... `door` | the same, and it stays as it is |
| glazed wood, the 12 woods | `oak_` ... `glazed_door` | the wooden door with glass from one block up |

| Shape | In the id | Leaves |
| --- | --- | --- |
| 1 wide | `door_1x<h>` | one |
| 2 wide | `split_door_2x<h>` or `door_2x<h>` | two of 1 block, or one of 2 |
| 3 wide | `split_door_3x<h>` or `door_3x<h>` | two of 1.5 blocks, or one of 3 |
| 4 wide | `split_door_4x<h>` | two of 2 blocks |

Heights are 2, 3 and 4. Doors 3 and 4 tall also come with an arched top, `_arched` in the id
(`oak_arched_split_door_3x3`, `iron_arched_gate_1x4`). Stone doors that tall are always
arched and have no `_arched` in their id. The single leaf three wide (`door_3x<h>`) has no
arched form, in any family.

## Block states

A door is one block per cell: `w` columns by `h` rows.

| State | Values | Meaning |
| --- | --- | --- |
| `facing` | `north`, `east`, `south`, `west` | the way the door swings: out of the building |
| `part` | `0`..`w-1` | the column, counted from the left as you look along `facing`. A door 1 wide has no `part` |
| `row` | `0`..`h-1` | the row, counted up from the floor |
| `open` | `false`, `true` | |
| `powered` | `false`, `true` | the door has a redstone signal |
| `hinge` | `left`, `right` | single doors only: the end the leaf turns on |
| `front` | `false`, `true` | which face of its cells the closed door stands on: `false` the back face (away from `facing`), `true` the face it swings towards |

For a door facing north, `part=0` is the west column; facing east, the north column;
facing south, the east column; facing west, the south column.

### Back face or front face

With `front=false` the closed door stands on the back face of its cells and its leaves swing
out through those cells. In a wall one block thick it sits flush with the inside and the open
leaves start inside the doorway.

With `front=true` the closed door stands on the face it swings towards. The leaves turn on
the outer corners of the doorway and lie wholly outside the wall when open, one block further
out than the same door with `front=false`.

Placing by hand: the door always goes on the near side of the cell you click. Placed
normally it swings away from you (`front=false`); placed while sneaking it swings towards you
(`front=true`).

### Placing a door by command

Set every cell closed, with the same `facing` (and `hinge`) on all of them:

```
setblock X Y Z granddoors:oak_split_door_3x2[facing=north,part=0,row=0]
```

Never set a cell with `open=true`: an open `door_3x<h>`, and any open door with `front=true`,
has cells in other places than when closed, so a hand-made open door is a broken door. Replacing or removing one cell takes
the whole door away. `showcase.py` prints the commands for a deck with every door on it.

### Room a door needs

An open leaf lies along its jamb and reaches out past the wall by its own length less one
block. The cell in front of its hinge column has to be free of anything you can bump into,
on every row.

| Door | Reaches out | Needs free in front of |
| --- | --- | --- |
| `door_1x`, `split_door_2x` | nothing | nothing |
| `split_door_3x` | half a block | both end columns |
| `split_door_4x` | one block | both end columns |
| `door_2x` | one block | the hinge column |
| `door_3x` | two blocks | the hinge column, two cells deep, and they must be air or replaceable: this door moves its cells there |

The table is for `front=false`. With `front=true` every door needs one cell more: the cells
in front of each hinge column must be air or replaceable on every row (the door moves its
hinge cells there, and the doorway itself is then empty), and behind them the same reach as
in the table must be free. So a `split_door_3x` with `front=true` needs one air cell and half
a free block in front of both end columns.

A door that has no room does not open and makes the locked-chest sound.

## Use

Right-click opens and closes any of them, the iron ones too. A redstone signal on any cell
opens the door and its end closes it: a lever, button or pressure plate beside the door, or
dust or a powered block against any of its cells. An observer looking at a cell sees the door
open and close. The doors are in six creative tabs (wood, arched wood, stone, iron, copper,
glazed). Names and tooltips are in English, Dutch, Vlaams, German, French and Spanish
(`lang.py`), and each item's tooltip gives its size. Breaking any cell breaks the whole door and drops one
item (stone, iron and copper need a pickaxe).

Copper doors weather like vanilla copper, the whole door at once. Sneak and use an axe to
scrape a door back one age or to take the wax off; sneak and use honeycomb to wax it.

Villagers open and close the wooden doors (plain and glazed) as they do vanilla's, also the
wide ones and the ones with `front=true`. They leave stone, iron and copper shut.

Recipes, all shapeless unless said:

| Door | Made from |
| --- | --- |
| wood `door_1x2` | the vanilla door of that wood |
| stone `door_1x2` | six of the stone, shaped 2 wide by 3 tall |
| `iron_door_1x2` | the vanilla iron door |
| copper `door_1x2` | the vanilla copper door of the same age, waxed or not |
| wood `glazed_door_1x2` | the vanilla door of that wood and a glass pane |
| `iron_gate_1x2` | six iron bars, shaped 2 wide by 3 tall |
| `door_1x3`, `door_1x4` | the `1x2` and one or two more planks, stones, iron ingots, iron bars, copper ingots or (glazed) glass panes |
| arched `1x3`, `1x4` | the same and a slab of the wood, or an iron or copper nugget (stone needs nothing extra) |
| any wider door | that many doors 1 wide of the same height and kind, and a stick to make it split |

## Cost

The mod adds about 344,000 block states (1,024 doors, each with all its facings, cells and
positions). Measured on a bare 26.3 server that is about 130 MB of heap: 262 MB in use after
start with the mod, 129 MB with a single door registered. `MemoryProbe` in `src/gametest`
logs the numbers on every `runGameTest`. A family of doors in every shape is about 9,400
states, some 4 MB; the 12 glazed woods together are a third of the total.

## Build

Java 25 and Fabric Loom, in a container (the workstation has no JDK 25):

```
python gen_assets.py
docker run --rm -v <this folder>:/w -v wtp-gradle:/root/.gradle -w /w eclipse-temurin:25-jdk sh ./gradlew build runGameTest
```

That is slow from a Windows folder (half an hour: Gradle reads the thousands of generated
files through the mount). Much faster is to copy the sources into a volume and build there:

```
tar -cf - --exclude=./build --exclude=./.gradle . | docker run --rm -i -v gd-work:/w -v wtp-gradle:/root/.gradle -v <this folder>/build/libs:/out eclipse-temurin:25-jdk sh -c 'mkdir -p /w/p && cd /w/p && find . -mindepth 1 -maxdepth 1 ! -name build ! -name .gradle -exec rm -rf {} + && tar -xf - && sh ./gradlew build runGameTest && cp build/libs/granddoors-*.jar /out/'
```

The jar is `build/libs/granddoors-<version>.jar`. `runGameTest` runs the server game tests in
`src/gametest` (hand, redstone, blocked, drops; no client needed). `gen_assets.py` decides
which doors exist: it writes the list the mod registers, and the block states, models, loot
tables, recipes, tags and language file, into `src/generated/resources` (not committed). Its
docstring explains how a leaf is drawn.

## How it works

- `GrandDoorBlock` holds everything: the cell geometry, the shapes, opening and closing,
  redstone, placing and breaking.
- A leaf up to two blocks long stays in place. Open, its hinge column draws the whole leaf
  turned along the jamb, up to 16 pixels past its own cell (as far as a block model may
  reach), and its collision box reaches as far. The other columns draw nothing and have no
  shape. No block entity, no renderer, no client code.
- A single leaf three blocks long is too long to draw from one cell. So, as in Doorways, the
  cells move when it opens: the hinge column stays, the other two go in front of it.
- A door with `front=true` is the same door mirrored through its cells when closed. Open, its
  leaf lies exactly one cell further out, so every cell that carries leaf steps one cell
  forward and draws the very same open model.
- Mobs: vanilla's path finder and villager behaviour only know vanilla's door block. A small
  mixin (`WalkNodeEvaluatorMixin`) tells the path finder that a closed wooden Grand Door can
  be opened, and `VillagerDoors` opens a door on a villager's path and closes it when no
  villager is near.
- Copper: when one cell turns into the same cell of another door of the same build (by time,
  axe or honeycomb), the rest of the door follows it instead of falling apart.
- An arch is cut out of the leaves, and the stone or timber above it is drawn by the top
  cells whether the door is open or not. That is why the door whose cells move has no arch.
