# Sharing this with the Doorways author

Doorways is by André Freitas (`itsandrefreitas` on GitHub, itsshoon on CurseForge), MIT.
Its jar points to `github.com/itsandrefreitas/doorways`, but on 2026-11-13 that repository
was not public, so there is nothing to fork or send a pull request to. The way to reach him
is a comment or message on the mod's CurseForge page.

Grand Doors is our own code, MIT, with vanilla textures. It copies nothing from Doorways.
We read the Doorways jar only to learn how its doors work.

## What to tell him

- What we wanted: a 3-wide door that opens in the middle, and 3-wide tall doors (3x3, 3x4).
- Why Doorways cannot do it today: its layout (`DoorLayout`) refuses `SPLIT` on an odd width,
  because every panel is one whole block and a centre split of 3 needs leaves 1.5 blocks wide.
- How we solved it, which he can take over as is: the cells of a split door never move. When
  it opens, each end column draws its whole 1.5-block leaf turned along the jamb, reaching 8
  pixels past its own cell (block models may reach 16 pixels out), with a collision box just
  as long. The middle column draws nothing and has an empty shape. No block entity, no
  renderer, no client code.
- In Doorways' terms that is one new case: `SPLIT` with an odd width, where `openOffset` is
  the closed offset, the two hinge parts get a long open model and the middle part an empty
  one. His stone doors are limited to width 2 by `DoorStyle.allowsWidth`; the block already
  allows heights up to 5.
- Where to look: `GrandDoorBlock.java` (about 400 lines) and the docstring of `gen_assets.py`.

He is welcome to take any of it. If he adds the centre split to Doorways, we can drop this
mod and move the city generator to his block ids.
