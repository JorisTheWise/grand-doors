# For the author of Doorways, and anyone else

Grand Doors began as a wish for two things Doorways 0.6.0 did not have: a door three wide
that opens in the middle, and doors three wide that are three or four tall. It is our own
code under MIT, with vanilla textures; it copies nothing from Doorways. Take any of it.

## The idea that made the rest possible

In Doorways every panel of a leaf is one whole block, so a leaf is a whole number of blocks
long and a door split in two needs an even width (`DoorLayout` refuses `SPLIT` on an odd one).

Here a leaf up to two blocks long never moves its cells. When the door opens, the hinge
column draws the whole leaf turned along the jamb, up to 16 pixels past its own cell (as far
as a block model may reach), with a collision box just as long. The other columns draw
nothing and have an empty shape. So a leaf can be 1.5 blocks long, and a centre split of
three works. No block entity, no renderer, no client code.

In Doorways' terms that is one new case: `SPLIT` with an odd width, where `openOffset` is the
closed offset, the two hinge parts get a long open model and the middle part an empty one.

## The other things here

- Only the single leaf three blocks long still moves its cells, as in Doorways.
- `front=true`: the door stands on the face it swings towards. Open, the leaf lies exactly
  one cell further out, so the cells that carry leaf step one cell forward and reuse the same
  open models.
- Arched tops: the arch is cut out of the leaves and the fill above it is drawn by cells that
  stay put.
- Models are generated (`gen_assets.py`): a leaf is a list of boxes in its own coordinates,
  cut on the block grid and handed to the cell that draws each piece.
- Villagers: one small mixin into the path finder plus a tick handler, since vanilla's door
  behaviour is tied to its own door class.

Where to look: `GrandDoorBlock.java` and the docstring of `gen_assets.py`.
