"""Print the commands for a showcase of every Grand Doors door: a floating deck with one
brick wall per family (a wood, a stone, the iron door, the iron gate) and a door in each
bay, all closed and facing north.

    python watchtower-doors/showcase.py X Y Z > showcase.mcfunction
    python watchtower-doors/showcase.py size        # the box it needs
    python watchtower-doors/showcase.py X Y Z tinted styles 1x1     # only some of the doors
    python watchtower-doors/showcase.py size tinted styles 1x1

Words after the corner pick what goes on the deck: the names of creative tabs (wood,
arched_wood, stone, iron, copper, glazed, tinted, styles) and "1x1" for the doors of one
block of every family. With none, every door.

X Y Z is the north-west corner of the deck (its floor is at Y). Pick a spot with air in the
whole box. Along a row the flat doors come first, by height and then width, then the arched
ones. Single doors have their hinge on the left. Run the commands over RCON or as a function,
with the area loaded.
"""
import sys

from gen_assets import MOD, door_name, doors, tab_of

PICK = set()   # filled from the command line; empty means every door


def picked(door):
    family, width, height, _, arched = door
    return not PICK or tab_of(family, arched) in PICK or ("1x1" in PICK and width == 1 and height == 1)

GAP = 1        # air between two bays
ROW = 7        # rows are this far apart; a single leaf three wide swings two blocks out
FRONT = 3      # deck in front of the first wall
TOP = 6        # the tallest wall, above the floor


def rows():
    """[[door, ...]]: one row per family, in the order of the door list. Doors picked only as
    "1x1" (their family's tab was not asked for) share rows of their own at the end.
    """
    out, families, loose = [], [], []
    for door in filter(picked, doors()):
        family, _, _, _, arched = door
        if PICK and tab_of(family, arched) not in PICK:
            loose.append(door)
        elif families and families[-1] is family:
            out[-1].append(door)
        else:
            families.append(family)
            out.append([door])
    per_row = 30
    return out + [loose[i:i + per_row] for i in range(0, len(loose), per_row)]


def size():
    """(x, y, z) of the box the deck takes."""
    widest = max(sum(width + 2 + GAP for _, width, *_ in bays) for bays in rows())
    return widest, TOP + 1, FRONT + ROW * len(rows())


def commands(x0, y0, z0):
    size_x, _, size_z = size()
    out = [f"fill {x0} {y0} {z0} {x0 + size_x - 1} {y0} {z0 + size_z - 1} minecraft:smooth_stone"]
    for index, bays in enumerate(rows()):
        z = z0 + FRONT + ROW * index
        x = x0
        for family, width, height, split, arched in bays:
            # A wall with one block each side of the doorway and one above it.
            out.append(f"fill {x} {y0 + 1} {z} {x + width + 1} {y0 + height + 1} {z} minecraft:bricks")
            name = f"{MOD}:{door_name(family, width, height, split, arched)}"
            for part in range(width):
                for row in range(height):
                    state = ["facing=north"]
                    if height > 1:
                        state.append(f"row={row}")
                    if width > 1:
                        state.append(f"part={part}")
                    if not split:
                        state.append("hinge=left")
                    out.append(f"setblock {x + 1 + part} {y0 + 1 + row} {z} {name}[{','.join(state)}]")
            x += width + 2 + GAP
    return out


if __name__ == "__main__":
    if sys.argv[1] == "size":
        PICK.update(sys.argv[2:])
        print(*size())
    else:
        PICK.update(sys.argv[4:])
        print("\n".join(commands(*map(int, sys.argv[1:4]))))
