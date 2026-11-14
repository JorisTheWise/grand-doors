"""Write the generated resources of Grand Doors into src/generated/resources.

The list of doors the mod registers (granddoors_doors.json), block states, block and item
models, item definitions, loot tables, recipes, tags and the language file. Everything is
drawn with vanilla textures, so the mod ships no art.

Run from anywhere: python watchtower-doors/gen_assets.py

Which doors exist
-----------------
FAMILIES x SHAPES x HEIGHTS, see doors(). A family is a material with a look (wood panels,
wood with glass above, stone, an iron or copper plate, an iron gate of bars). Copper comes in
its four ages, each also waxed; an unwaxed door weathers into the next age. A shape is a width and whether the door is split
in two leaves. Doors three and four tall also come with an arched top; stone doors that tall
are always arched. The one exception is the single leaf three wide: its cells move when it
opens (see below), so it cannot keep the fixed stone above an arch, and it stays flat.

How a door is drawn
-------------------
A leaf is described once, as a list of boxes in its own coordinates:
    u  along the leaf from its hinge edge, in pixels
    v  up from the floor
    w  through the leaf, from the face on the inside of the building (0) outwards (3)
The boxes are cut on the block grid and each piece goes into the model of the cell that
draws it. Models are made for a door facing north (out is -z, the left column is at -x); the
block state file turns them for the other facings.

A closed leaf lies along the inside face of its cells. An open leaf has turned out around the
inside corner at its hinge, so it lies along the jamb. A model box may reach 16 pixels past
its cell, so a leaf up to 32 long is drawn whole by its hinge cell and the door's other cells
draw nothing. A single leaf three wide (48 long) is too long for that: the block moves its
cells out in front of the hinge and each cell draws its own 16 pixels.

An arch is cut out of the top of the leaves, and (except on a gate) the stone or timber above
it is drawn fixed, in the closed and the open models alike.

A door can also stand on the front face of its cells (front=true), the face it swings
towards. Closed, that is the same door mirrored through the cell. Open, the leaf lies one
whole cell further out, and the block moves every cell that carries leaf one cell forward to
draw it, so those cells use the very same open models; only the fill above an arch differs,
which they then draw one cell behind themselves.
"""
import hashlib
import json
import math
import pathlib
import shutil

from lang import LANGUAGES, language_file

MOD = "granddoors"
THICK = 3
HEIGHTS = (1, 2, 3, 4)
# (width, split)
SHAPES = ((1, False), (2, True), (2, False), (3, True), (3, False), (4, True))
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}

# How high the arch rises over a door of each width, and the width of its steps, in pixels.
ARCH_RISE = {1: 4, 2: 7, 3: 10, 4: 12}
ARCH_STEP = 2


def block(name):
    return "minecraft:block/" + name


def wood(name, label, frame=None):
    return {
        "stem": name, "noun": "door", "style": "wood", "material": name, "label": label,
        "textures": {"base": block(name + "_planks"), "frame": block(frame or f"stripped_{name}_log"), "metal": block("iron_block")},
        "tool": "axe",
        "base_recipe": {"shapeless": [f"minecraft:{name}_door"]},
        "hatch_recipe": {"shapeless": [f"minecraft:{name}_trapdoor"]},
        "unit": f"minecraft:{name}_planks", "arch_item": f"minecraft:{name}_slab",
    }


def glazed(name, label, frame=None, tinted=False):
    """The wooden door with glass in it from one block up; clear glass or tinted."""
    family = wood(name, label, frame)
    glass = "minecraft:tinted_glass" if tinted else "minecraft:glass_pane"
    kind = "tinted" if tinted else "glazed"
    family.update(noun=kind + "_door", style="glazed", kind=kind, tab=kind,
                  base_recipe={"shapeless": [f"minecraft:{name}_door", glass]},
                  hatch_recipe={"shapeless": [f"minecraft:{name}_trapdoor", glass]},
                  unit=glass)
    family["textures"] = dict(family["textures"], glass=block("tinted_glass" if tinted else "glass"))
    return family


# The three looks that only come in the sizes of a house door.
HOUSE_SIZES = {(1, 2, False), (2, 2, True), (2, 2, False)}


def house_style(kind, name, label, frame=None):
    """Another look for the house-door sizes: boarded, with a small window, or glass in a lattice."""
    family = wood(name, label, frame)
    extra = {"boarded": ["minecraft:stick"], "windowed": ["minecraft:glass"],
             "lattice": ["minecraft:glass_pane", "minecraft:stick"]}[kind]
    family.update(noun=kind + "_door", style=kind, kind=kind, tab="styles",
                  base_recipe={"shapeless": [f"minecraft:{name}_door"] + extra},
                  sizes=HOUSE_SIZES | ({(1, 1, False)} if kind == "boarded" else set()),
                  hatch_recipe={"shapeless": [f"minecraft:{name}_trapdoor"] + extra})
    family["textures"] = dict(family["textures"], glass=block("glass"))
    return family


WOODS = (("oak", "Oak", None), ("spruce", "Spruce", None), ("birch", "Birch", None), ("jungle", "Jungle", None),
         ("acacia", "Acacia", None), ("dark_oak", "Dark Oak", None), ("mangrove", "Mangrove", None),
         ("cherry", "Cherry", None), ("pale_oak", "Pale Oak", None), ("bamboo", "Bamboo", "stripped_bamboo_block"),
         ("crimson", "Crimson", "stripped_crimson_stem"), ("warped", "Warped", "stripped_warped_stem"))


COPPER_AGES = (("copper", "Copper", "copper_block", "cut_copper"),
               ("exposed_copper", "Exposed Copper", "exposed_copper", "exposed_cut_copper"),
               ("weathered_copper", "Weathered Copper", "weathered_copper", "weathered_cut_copper"),
               ("oxidized_copper", "Oxidized Copper", "oxidized_copper", "oxidized_cut_copper"))


def copper(age, waxed):
    name, label, base, frame = COPPER_AGES[age]
    stem = ("waxed_" if waxed else "") + name
    return {
        "stem": stem, "noun": "door", "style": "iron", "material": name, "label": ("Waxed " if waxed else "") + label,
        "word": stem,
        "textures": {"base": block(base), "frame": block(frame), "metal": block(frame)},
        "tool": "pickaxe",
        "base_recipe": {"shapeless": [f"minecraft:{stem}_door"]},
        "hatch_recipe": {"shapeless": [f"minecraft:{stem}_trapdoor"]},
        "unit": "minecraft:copper_ingot", "arch_item": "minecraft:copper_nugget",
        # What an unwaxed door weathers to, and the waxed twin of each: stems of other families.
        "weathers_to": None if waxed or age == 3 else COPPER_AGES[age + 1][0],
        "fresh": age == 0,
        "waxed": None if waxed else "waxed_" + name,
    }


def stone(name, label, base, frame):
    return {
        "stem": name, "noun": "door", "style": "stone", "material": name, "label": label,
        "textures": {"base": block(base), "frame": block(frame), "metal": block("iron_block")},
        "tool": "pickaxe",
        "base_recipe": {"shaped": ["MM", "MM", "MM"], "key": {"M": "minecraft:" + base}},
        "hatch_recipe": {"shaped": ["MM", "MM"], "key": {"M": "minecraft:" + base}},
        "unit": "minecraft:" + base, "arch_item": None,
    }


FAMILIES = [
    wood("oak", "Oak"), wood("spruce", "Spruce"), wood("birch", "Birch"), wood("jungle", "Jungle"),
    wood("acacia", "Acacia"), wood("dark_oak", "Dark Oak"), wood("mangrove", "Mangrove"),
    wood("cherry", "Cherry"), wood("pale_oak", "Pale Oak"),
    wood("bamboo", "Bamboo", "stripped_bamboo_block"),
    wood("crimson", "Crimson", "stripped_crimson_stem"), wood("warped", "Warped", "stripped_warped_stem"),
    stone("cobblestone", "Cobblestone", "cobblestone", "stone"),
    stone("stone_brick", "Stone Brick", "stone_bricks", "smooth_stone"),
    stone("cobbled_deepslate", "Cobbled Deepslate", "cobbled_deepslate", "polished_deepslate"),
    stone("deepslate_brick", "Deepslate Brick", "deepslate_bricks", "polished_deepslate"),
    {
        "stem": "iron", "noun": "door", "style": "iron", "material": "iron", "label": "Iron",
        "textures": {"base": block("iron_block"), "frame": block("anvil"), "metal": block("anvil")},
        "tool": "pickaxe",
        "base_recipe": {"shapeless": ["minecraft:iron_door"]},
        "hatch_recipe": {"shapeless": ["minecraft:iron_trapdoor"]},
        "unit": "minecraft:iron_ingot", "arch_item": "minecraft:iron_nugget",
    },
    {
        "stem": "iron", "noun": "gate", "style": "gate", "material": "iron", "label": "Iron",
        "textures": {"base": block("anvil"), "frame": block("anvil"), "metal": block("anvil")},
        "tool": "pickaxe",
        "base_recipe": {"shaped": ["BB", "BB", "BB"], "key": {"B": "minecraft:iron_bars"}},
        "hatch_recipe": {"shaped": ["BB", "BB"], "key": {"B": "minecraft:iron_bars"}},
        "unit": "minecraft:iron_bars", "arch_item": "minecraft:iron_nugget",
    },
    *(copper(age, False) for age in range(4)),
    *(copper(age, True) for age in range(4)),
    glazed("oak", "Oak"), glazed("spruce", "Spruce"), glazed("birch", "Birch"), glazed("jungle", "Jungle"),
    glazed("acacia", "Acacia"), glazed("dark_oak", "Dark Oak"), glazed("mangrove", "Mangrove"),
    glazed("cherry", "Cherry"), glazed("pale_oak", "Pale Oak"),
    glazed("bamboo", "Bamboo", "stripped_bamboo_block"),
    glazed("crimson", "Crimson", "stripped_crimson_stem"), glazed("warped", "Warped", "stripped_warped_stem"),
    *(glazed(name, label, frame, tinted=True) for name, label, frame in WOODS),
    *(house_style(kind, name, label, frame) for kind in ("boarded", "windowed", "lattice") for name, label, frame in WOODS),
]


def moving(width, split):
    """The door whose cells move when it opens: one leaf longer than two blocks."""
    return not split and width > 2


def door_name(family, width, height, split, arched):
    """The block id. A stone door's arch is not in its name: it has no flat twin."""
    named = arched and family["style"] != "stone"
    return f"{family['stem']}{'_arched' if named else ''}{'_split' if split else ''}_{family['noun']}_{width}x{height}"


# Creative tabs and their English titles (lang.py has the others). tab_of() sorts every door
# into one.
TABS = {
    "wood": "Grand Doors: Wood",
    "arched_wood": "Grand Doors: Arched Wood",
    "stone": "Grand Doors: Stone",
    "iron": "Grand Doors: Iron",
    "copper": "Grand Doors: Copper",
    "glazed": "Grand Doors: Glazed",
    "tinted": "Grand Doors: Tinted Glass",
    "styles": "Grand Doors: House Doors",
}


def tab_of(family, arched):
    if "tab" in family:
        return family["tab"]
    if family["style"] == "wood":
        return "arched_wood" if arched else "wood"
    if family["style"] == "glazed":
        return "glazed"
    if "copper" in family["material"]:
        return "copper"
    return "stone" if family["style"] == "stone" else "iron"


def doors():
    """Every door, in the order of the creative tab: (family, width, height, split, arched)."""
    out = []
    for family in FAMILIES:
        for arched in (False, True):
            for height in HEIGHTS:
                for width, split in SHAPES:
                    if height == 1 and width > 1:
                        continue  # one block tall only as the door of one block
                    if "sizes" in family and (width, height, split) not in family["sizes"]:
                        continue
                    can_arch = height >= 3 and not moving(width, split)
                    if family["style"] == "stone":
                        if arched != can_arch:
                            continue
                    elif arched and not can_arch:
                        continue
                    out.append((family, width, height, split, arched))
    return out


# ---- the arch ------------------------------------------------------------------------------

def arch_drop(width, x):
    """Pixels the arch has come down at door coordinate x (the step that x lies in)."""
    span = width * 16
    step = math.floor(x / ARCH_STEP) * ARCH_STEP
    from_jamb = min(step, span - step - ARCH_STEP) + ARCH_STEP / 2
    return round(ARCH_RISE[width] * (1 - math.sqrt(1 - (1 - from_jamb / (span / 2)) ** 2)))


def arch_marks(width):
    """Door coordinates where the arch changes height."""
    span = width * 16
    return {x for x in range(ARCH_STEP, span, ARCH_STEP) if arch_drop(width, x) != arch_drop(width, x - ARCH_STEP)}


# ---- the leaf ------------------------------------------------------------------------------

def top(offset):
    """A height measured down from the top of the leaf, which an arch lowers."""
    return ("top", offset)


def leaf_boxes(length, height, style):
    """Boxes (u0, u1, v0, v1, w0, w1, texture) of one leaf, before cutting."""
    if style == "gate":
        boxes = [(0, 2, 0, top(-1), 0.5, 2.5, "frame"), (length - 2, length, 0, top(-1), 0.5, 2.5, "frame")]
        rails = [(2, 4), (top(-5), top(-3))]
        if height >= 3:
            rails.append((16 * (height // 2) - 1, 16 * (height // 2) + 1))
        for v0, v1 in rails:
            boxes.append((0, length, v0, v1, 0.5, 2.5, "frame"))
        for u in range(4, int(length) - 2, 4):
            boxes.append((u - 0.5, u + 0.5, 1, top(0), 1, 2, "base"))
        if height > 1:
            boxes.append((length - 5, length - 2, 17, 22, 0.5, 2.5, "frame"))  # the lock
        return boxes

    blocks = range(int(length) // 16)
    pull = (length - 1.5, length - 0.5, 19, 23, -1, THICK + 1, "metal") if height > 1 else \
        (length - 1.5, length - 0.5, 6, 10, -1, THICK + 1, "metal")

    if style == "boarded":
        # A plain plank leaf held together by three ledges, on two strap hinges.
        boxes = [(0, length, 0, top(0), 0.5, THICK - 0.5, "base")]
        ledges = [(3, 6), (top(-6), top(-3))] + ([(14.5, 17.5)] if height > 1 else [])
        for v0, v1 in ledges:
            boxes.append((0, length, v0, v1, 0, THICK, "frame"))
            boxes.append((0, 7, v0 if isinstance(v0, tuple) else v0 + 1, v1 if isinstance(v1, tuple) else v1 - 1,
                          -0.5, THICK + 0.5, "metal"))
        return boxes + [pull]

    if style in ("glazed", "lattice"):
        # Timber up to the first rail, a thin pane of glass above it. One block tall: all glass.
        sill = 16 if height > 1 else 0
        boxes = [(0, length, 0, sill, 0.5, THICK - 0.5, "base"), (0, length, sill, top(0), 1, 2, "glass")] if sill else \
            [(0, length, 0, top(0), 1, 2, "glass")]
        if style == "lattice":
            # Glazing bars: four small panes to a block.
            for k in blocks:
                boxes.append((16 * k + 7.5, 16 * k + 8.5, 16, top(0), 0.5, THICK - 0.5, "frame"))
            for row in range(1, height):
                boxes.append((0, length, 16 * row + 7.5, 16 * row + 8.5, 0.5, THICK - 0.5, "frame"))
    elif style == "windowed":
        # The panelled door with one small window to a block, at eye height.
        boxes = [(0, length, 0, 20, 0.5, THICK - 0.5, "base"), (0, length, 28, top(0), 0.5, THICK - 0.5, "base")]
        edge = 0
        for k in blocks:
            u0, u1 = 16 * k + 4, 16 * k + 12
            boxes.append((edge, u0, 20, 28, 0.5, THICK - 0.5, "base"))
            boxes.append((u0, u1, 20, 28, 1, 2, "glass"))
            for a, b, c, d in ((u0 - 1, u1 + 1, 19, 20), (u0 - 1, u1 + 1, 28, 29), (u0 - 1, u0, 20, 28), (u1, u1 + 1, 20, 28)):
                boxes.append((a, b, c, d, 0, THICK, "frame"))
            edge = u1
        boxes.append((edge, length, 20, 28, 0.5, THICK - 0.5, "base"))
    else:
        boxes = [(0, length, 0, top(0), 0.5, THICK - 0.5, "base")]
    stiles = [(0, 2), (length - 2, length)]
    if length % 16 == 0:  # a leaf of whole blocks reads as one panel per block
        stiles += [(u - 1, u + 1) for u in range(16, int(length) - 2, 16)]
    for u0, u1 in stiles:
        boxes.append((u0, u1, 0, top(0), 0, THICK, "frame"))
    rails = [(0, 2), (top(-2), top(0))] + [(16 * k - 1, 16 * k + 1) for k in range(1, height)]
    for v0, v1 in rails:
        boxes.append((0, length, v0, v1, 0, THICK, "frame"))
    # A pull on both faces, on the stile at the free edge.
    boxes.append(pull)
    return boxes


def cut(boxes, width, height, arched, to_door):
    """Cut boxes on the block grid, and under the arch, so each piece lies in one cell.

    to_door turns a leaf's u into a door coordinate, to find the arch above it.
    """
    full = height * 16
    marks = set(range(0, width * 16 + 1, 16))
    if arched:
        marks |= {m for m in (abs(to_door(0) - x) for x in arch_marks(width))}
    out = []
    for u0, u1, v0, v1, w0, w1, tex in boxes:
        us = [u0] + sorted(m for m in marks if u0 < m < u1) + [u1]
        for ua, ub in zip(us, us[1:]):
            limit = full - (arch_drop(width, to_door((ua + ub) / 2)) if arched else 0)
            va = limit + v0[1] if isinstance(v0, tuple) else v0
            vb = limit + v1[1] if isinstance(v1, tuple) else min(v1, limit)
            if vb <= va:
                continue
            vs = [va] + [m for m in range(0, full + 1, 16) if va < m < vb] + [vb]
            for a, b in zip(vs, vs[1:]):
                out.append((ua, ub, a, b, w0, w1, tex))
    return out


def spandrel(width, height):
    """The fixed fill above the arch, in door coordinates: (x0, x1, y0, y1), one cell each."""
    full = height * 16
    xs = sorted({0, width * 16} | arch_marks(width) | set(range(0, width * 16, 16)))
    out = []
    for x0, x1 in zip(xs, xs[1:]):
        drop = arch_drop(width, x0)
        if drop:
            out.append((x0, x1, full - drop, full))
    return out


# ---- from leaf to model --------------------------------------------------------------------

def tile(lo, hi):
    """A range moved into one 16-pixel texture tile."""
    base = math.floor(lo / 16) * 16
    return max(0, lo - base), min(16, hi - base)


def element(x, y, z, tx, ty, tz, tex):
    """One model box. x, y, z are (lo, hi); tx, ty, tz the texture ranges along each axis."""
    def side(h, v):
        return {"texture": "#" + tex, "uv": [h[0], 16 - v[1], h[1], 16 - v[0]]}

    flat = {"texture": "#" + tex, "uv": [tx[0], tz[0], tx[1], tz[1]]}
    return {
        "from": [x[0], y[0], z[0]],
        "to": [x[1], y[1], z[1]],
        "faces": {
            "north": side(tx, ty), "south": side(tx, ty),
            "east": side(tz, ty), "west": side(tz, ty),
            "up": flat, "down": flat,
        },
    }


def door_cells(width, height, split, style, arched, opened, hinge_right=False, front=False):
    """{(part, row): [elements]} for one position of one door."""
    span = width * 16
    cells = {(p, r): [] for p in range(width) for r in range(height)}
    length = span // 2 if split else span
    moves = moving(width, split)
    for right in ((False, True) if split else (hinge_right,)):
        def to_door(u, right=right):
            return span - u if right else u

        for u0, u1, v0, v1, w0, w1, tex in cut(leaf_boxes(length, height, style), width, height, arched, to_door):
            tu, tv, tw = tile(u0, u1), tile(v0, v1), tile(w0, w1)
            row = int(v0 // 16)
            y = (v0 - 16 * row, v1 - 16 * row)
            if not opened:
                x = (span - u1, span - u0) if right else (u0, u1)
                part = int(x[0] // 16)
                z = (w0, w1) if front else (16 - w1, 16 - w0)
                cells[(part, row)].append(element((x[0] - 16 * part, x[1] - 16 * part), y, z, tu, tv, tw, tex))
                continue
            # Turned out around the hinge: the leaf runs along -z from the inside face.
            x = (16 - w1, 16 - w0) if right else (w0, w1)
            z = (16 - u1, 16 - u0)
            hinge = width - 1 if right else 0
            if moves:
                # The cell that draws this piece has moved out in front of the hinge.
                steps = int(u0 // 16)
                part = hinge - steps if right else hinge + steps
                z = (z[0] + 16 * steps, z[1] + 16 * steps)
            else:
                part = hinge
            cells[(part, row)].append(element(x, y, z, tw, tv, tu, tex))
    if arched and style != "gate":
        tex = "base" if style == "stone" else "frame"
        for x0, x1, y0, y1 in spandrel(width, height):
            part, row = int(x0 // 16), height - 1
            z = (0, 5) if front else (11, 16)
            if front and opened:
                # The cells of this column may have stepped forward; the nearest cell that
                # carries a leaf draws the fill, one cell behind itself.
                part = (width - 1 if x0 >= span / 2 else 0) if split else (width - 1 if hinge_right else 0)
                z = (16, 21)
            cells[(part, row)].append(element(
                (x0 - 16 * part, x1 - 16 * part), (y0 - 16 * row, y1 - 16 * row), z,
                tile(x0, x1), tile(y0, y1), (0, 5), tex))
    return cells


def item_model(width, height, split, style, arched, textures):
    """The closed door, shrunk to fit one block."""
    span = width * 16
    scale = 16 / max(span, height * 16)
    lift_x = (16 - span * scale) / 2
    elements = []
    for (part, row), cell in door_cells(width, height, split, style, arched, False).items():
        for e in cell:
            f, t = e["from"], e["to"]
            elements.append({
                "from": [round((f[0] + 16 * part) * scale + lift_x, 3), round((f[1] + 16 * row) * scale, 3), round(8 + (f[2] - 14.5) * scale, 3)],
                "to": [round((t[0] + 16 * part) * scale + lift_x, 3), round((t[1] + 16 * row) * scale, 3), round(8 + (t[2] - 14.5) * scale, 3)],
                "faces": e["faces"],
            })
    # In a slot the door is seen straight on, filling it, rather than as a thin slab at an angle.
    return {
        "parent": "minecraft:block/block", "gui_light": "front",
        "display": {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]}},
        "textures": textures, "elements": elements,
    }


# ---- recipes -------------------------------------------------------------------------------

def recipe(family, width, height, split, arched):
    """A door one wide is made from the vanilla door (or six blocks) and grows upwards one
    block or bar at a time; a wider door is that many doors one wide, with a stick to split it.
    """
    def one(h):
        return f"{MOD}:{door_name(family, 1, h, False, arched and h >= 3)}"

    if width > 1:
        return {"shapeless": [one(height)] * width + (["minecraft:stick"] if split else [])}
    if height == 1:
        return family["hatch_recipe"]
    if height == 2:
        return family["base_recipe"]
    extra = [family["arch_item"]] if arched and family["arch_item"] else []
    return {"shapeless": [one(2)] + [family["unit"]] * (height - 2) + extra}


# ---- writing -------------------------------------------------------------------------------

def main():
    here = pathlib.Path(__file__).resolve().parent
    out = here / "src" / "generated" / "resources"
    if out.exists():
        shutil.rmtree(out)
    assets = out / "assets" / MOD
    data = out / "data" / MOD
    written = set()

    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, separators=(",", ":"), ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")

    def block_model(elements, textures):
        """Many cells look alike (every middle row, and the same cell in every wood), so the
        shape is written once, named by what is in it, and each material is a small child
        model that only fills in the textures.
        """
        def named(prefix, value):
            name = prefix + hashlib.sha1(json.dumps(value, sort_keys=True).encode()).hexdigest()[:14]
            if name not in written:
                written.add(name)
                write(assets / f"models/block/{name}.json", value)
            return f"{MOD}:block/{name}"

        shape = named("shape_", {"ambientocclusion": False, "elements": elements})
        return named("cell_", {"parent": shape, "textures": textures})

    lang = {
        "tooltip.granddoors.size": "%s wide, %s tall, %s",
        "tooltip.granddoors.one_leaf": "one leaf",
        "tooltip.granddoors.two_leaves": "two leaves that open in the middle",
        "tooltip.granddoors.sneak": "Sneak while placing: swings towards you",
    }
    for tab, title in TABS.items():
        lang[f"itemGroup.{MOD}.{tab}"] = title
    door_rows = []
    mineable = {"axe": [], "pickaxe": []}
    door_list = []
    write(assets / "models/block/empty.json", {"textures": {"particle": block("oak_planks")}})

    for family, width, height, split, arched in doors():
        name = door_name(family, width, height, split, arched)
        block_id = f"{MOD}:{name}"
        style = family["style"]
        textures = dict(family["textures"], particle=family["textures"]["base"])
        entry = {"tab": tab_of(family, arched), "name": name, "material": family["material"],
                 "width": width, "height": height, "split": split}
        if family.get("weathers_to"):
            entry["weathers_to"] = door_name(dict(family, stem=family["weathers_to"]), width, height, split, arched)
            entry["fresh"] = family["fresh"]
        if family.get("waxed"):
            entry["waxed"] = door_name(dict(family, stem=family["waxed"]), width, height, split, arched)
        door_list.append(entry)

        variants = {}
        positions = [(False, None), (True, None)] if split else [(False, False), (False, True), (True, False), (True, True)]
        for opened, hinge_right, front in [(o, h, f) for o, h in positions for f in (False, True)]:
            cells = door_cells(width, height, split, style, arched, opened, bool(hinge_right), front)
            for (part, row), elements in cells.items():
                ref = block_model(elements, textures) if elements else f"{MOD}:block/empty"
                for facing, turn in FACINGS.items():
                    key = f"facing={facing},front={str(front).lower()},open={str(opened).lower()}"
                    if height > 1:
                        key += f",row={row}"
                    if width > 1:
                        key += f",part={part}"
                    if hinge_right is not None:
                        key += f",hinge={'right' if hinge_right else 'left'}"
                    variants[key] = {"model": ref, "y": turn} if turn else {"model": ref}
        write(assets / f"blockstates/{name}.json", {"variants": variants})
        write(assets / f"models/item/{name}.json", item_model(width, height, split, style, arched, textures))
        write(assets / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}"}})
        named_arched = arched and style != "stone"
        lang[f"block.{MOD}.{name}"] = (f"{family['label']} {'Arched ' if named_arched else ''}{'Split ' if split else ''}"
                                       f"{family['noun'].replace('_', ' ').title()} ({width}x{height})")
        door_rows.append((name, family.get("word", family["material"]), family.get("kind", family["noun"]),
                          split, named_arched, width, height))
        mineable[family["tool"]].append(block_id)

        anchor = {}
        if height > 1:
            anchor["row"] = "0"
        if width > 1:
            anchor["part"] = "0"
        write(data / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "random_sequence": f"{MOD}:blocks/{name}",
            "pools": [{
                "rolls": 1,
                "condition": {"type": "minecraft:survives_explosion"},
                "entries": [{
                    "type": "minecraft:item",
                    "name": block_id,
                    **({"condition": {"type": "minecraft:match_block", "blocks": block_id, "state": anchor}} if anchor else {}),
                }],
            }],
        })

        made = recipe(family, width, height, split, arched)
        result = {"count": 1, "id": block_id}
        if "shaped" in made:
            write(data / f"recipe/{name}.json", {
                "type": "minecraft:crafting_shaped", "category": "building",
                "key": made["key"], "pattern": made["shaped"], "result": result})
        else:
            write(data / f"recipe/{name}.json", {
                "type": "minecraft:crafting_shapeless", "category": "building",
                "ingredients": made["shapeless"], "result": result})

    write(out / f"{MOD}_doors.json", door_list)
    write(assets / "lang/en_us.json", lang)
    for code, language in LANGUAGES.items():
        write(assets / f"lang/{code}.json", language_file(MOD, language, door_rows))
    for tool, blocks in mineable.items():
        write(out / f"data/minecraft/tags/block/mineable/{tool}.json", {"values": blocks})
    print(out, len(door_list), "doors,", len(written), "block models,", sum(1 for _ in out.rglob("*.json")), "files")


if __name__ == "__main__":
    main()
