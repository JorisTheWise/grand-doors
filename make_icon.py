"""Draw the mod's icon: an arched double door in a stone wall, 32x32 pixels, saved 8x larger.

Our own pixel art, drawn here so the mod ships no borrowed image.
Run from anywhere: python watchtower-doors/make_icon.py
"""
import math
import pathlib
import random

from PIL import Image

SIZE, SCALE = 32, 8
STONE = [(118, 118, 122), (104, 104, 108), (131, 131, 135)]
MORTAR = (78, 78, 82)
FRAME = (58, 40, 24)
PLANK = [(150, 104, 58), (139, 95, 52), (160, 113, 66)]
SEAM = (88, 60, 34)
IRON = (212, 212, 216)
SHADE = (40, 28, 18)


def main():
    rng = random.Random(3)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()

    # The wall: stone bricks, four pixels tall, laid half a brick apart.
    for y in range(2, SIZE - 1):
        course = (y - 2) // 4
        for x in range(1, SIZE - 1):
            brick = (x + (4 if course % 2 else 0)) // 8
            joint = (y - 2) % 4 == 3 or (x + (4 if course % 2 else 0)) % 8 == 7
            px[x, y] = MORTAR if joint else STONE[(brick * 7 + course * 3) % 3]

    # The doorway: 18 wide, with a round arch on top.
    left, right, floor, spring = 7, 24, SIZE - 2, 14
    centre, radius = (left + right) / 2, (right - left + 1) / 2

    def inside(x, y, grow=0.0):
        if not left - grow <= x <= right + grow or y > floor:
            return False
        if y >= spring:
            return True
        return math.hypot(x - centre, y - spring) <= radius + grow

    for y in range(SIZE):
        for x in range(SIZE):
            if inside(x, y, 1.6) and not inside(x, y):
                px[x, y] = FRAME
            elif inside(x, y):
                board = int(x - left) // 3
                px[x, y] = PLANK[(board + rng.randint(0, 9) // 8) % 3]

    for y in range(SIZE):
        for x in range(SIZE):
            if not inside(x, y):
                continue
            if x in (15, 16):                      # where the two leaves meet
                px[x, y] = SEAM
            elif y in (spring + 2, floor - 3) and inside(x, y - 1):   # two iron bands
                px[x, y] = SHADE
    for x, y in ((13, 21), (13, 22), (18, 21), (18, 22)):             # the pulls
        px[x, y] = IRON

    out = pathlib.Path(__file__).resolve().parent / "src/main/resources/assets/granddoors/icon.png"
    img.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST).save(out)
    print(out)


if __name__ == "__main__":
    main()
