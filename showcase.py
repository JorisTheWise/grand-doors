"""Print the commands for a showcase of every Grand Doors door: a floating deck with one
brick wall per family (a wood, a stone, the iron door, the iron gate) and a door in each
bay, all closed and facing north.

    python watchtower-doors/showcase.py X Y Z > showcase.mcfunction
    python watchtower-doors/showcase.py size        # the box it needs

X Y Z is the north-west corner of the deck (its floor is at Y). Pick a spot with air in the
whole box. Along a row the flat doors come first, by height and then width, then the arched
ones. Single doors have their hinge on the left. Run the commands over RCON or as a function,
with the area loaded.
"""
import sys

from gen_assets import MOD, door_name, doors

GAP = 1        # air between two bays
ROW = 7        # rows are this far apart; a single leaf three wide swings two blocks out
FRONT = 3      # deck in front of the first wall
TOP = 6        # the tallest wall, above the floor


def rows():
    """[(family, [door, ...])] in the order of the door list."""
    out = []
    for door in doors():
        if not out or out[-1][0] is not door[0]:
            out.append((door[0], []))
        out[-1][1].append(door)
    return out


def size():
    """(x, y, z) of the box the deck takes."""
    widest = max(sum(width + 2 + GAP for _, width, *_ in bays) for _, bays in rows())
    return widest, TOP + 1, FRONT + ROW * len(rows())


def commands(x0, y0, z0):
    size_x, _, size_z = size()
    out = [f"fill {x0} {y0} {z0} {x0 + size_x - 1} {y0} {z0 + size_z - 1} minecraft:smooth_stone"]
    for index, (family, bays) in enumerate(rows()):
        z = z0 + FRONT + ROW * index
        x = x0
        for _, width, height, split, arched in bays:
            # A wall with one block each side of the doorway and one above it.
            out.append(f"fill {x} {y0 + 1} {z} {x + width + 1} {y0 + height + 1} {z} minecraft:bricks")
            name = f"{MOD}:{door_name(family, width, height, split, arched)}"
            for part in range(width):
                for row in range(height):
                    state = [f"facing=north", f"row={row}"]
                    if width > 1:
                        state.append(f"part={part}")
                    if not split:
                        state.append("hinge=left")
                    out.append(f"setblock {x + 1 + part} {y0 + 1 + row} {z} {name}[{','.join(state)}]")
            x += width + 2 + GAP
    return out


if __name__ == "__main__":
    if sys.argv[1:] == ["size"]:
        print(*size())
    else:
        print("\n".join(commands(*map(int, sys.argv[1:4]))))
