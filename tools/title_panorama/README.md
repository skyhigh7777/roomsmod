# Title screen panorama generator

Ray-traces the B section corridor (stone bricks, stone floor/ceiling, oak doors, hanging lanterns, MC-style block light and fog)
into the 6 cube-map faces used by vanilla `CubeMap` (face orientation derived from `CubeMap.render`).

1. Extract vanilla textures into `tex/` (from the 1.21.1 client jar `assets/minecraft/textures/block/`):
   stone, stone_bricks, mossy_stone_bricks, cracked_stone_bricks, oak_door_top, oak_door_bottom, lantern, chain.
2. `mkdir out && python3 render.py 1024 2` (1024px faces, 2x2 supersampling), save each `out/panorama_N.png` as RGB optimized `out/opt_N.png`, then `python3 preview.py`
   (preview to /workspace/title_preview.png).
3. Copy faces to `src/main/resources/assets/minecraft/textures/gui/title/background/panorama_N.png`.
