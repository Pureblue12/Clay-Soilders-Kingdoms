# Your Minecraft assets go here (never committed)

The studio draws blocks with the real Minecraft textures and block models, read from your own copy of the game.
Put ONE of these in this folder:

- the client jar, e.g. copy `.minecraft/versions/1.21.1/1.21.1.jar` here (the studio unpacks the block assets
  once into `studio/.cache/`), or
- an extracted assets folder, so that `minecraft/assets/minecraft/blockstates/` exists.

Then reload the studio page. Without assets everything still runs with labelled placeholder textures.
Everything in this folder except this README is git-ignored.
