// Headless render page used by `npm run studio -- render`. Query parameters:
//   name, version | src (URL of a structure JSON), views=iso,front,right,top, w, h,
//   terrain=flat|hills|slope|none, size, trees=1, layer=all|upto|single, y, markers=1
// Sets window.__result = { views: { front: "data:image/png;base64,..." }, placeholders, assets } when done.
import type { StructureFile } from './format/structure.ts';
import { BlockLibrary, HttpAssets } from './render/blocks.ts';
import { DEFAULT_SCENE, showStructure, type SceneOptions } from './render/scene.ts';
import { PRESETS, Viewer, type CameraPreset } from './render/viewer.ts';
import type { LayerMode, TerrainKind } from './render/world.ts';

declare global {
  interface Window { __result?: unknown; __error?: string }
}

async function main() {
  const q = new URLSearchParams(location.search);
  const w = Number(q.get('w') ?? 1280), h = Number(q.get('h') ?? 800);
  const src = q.get('src') ?? `/api/structures/${q.get('name')}/${q.get('version')}`;
  const s = (await (await fetch(src)).json()) as StructureFile;
  const assets = new HttpAssets();
  const info = await assets.init();
  const lib = new BlockLibrary(assets);
  const canvas = document.getElementById('c') as HTMLCanvasElement;
  canvas.width = w;
  canvas.height = h;
  const viewer = new Viewer({ canvas, width: w, height: h, preserve: true });
  viewer.renderer.setPixelRatio(1);
  viewer.setSize(w, h);
  const opts: SceneOptions = {
    ...DEFAULT_SCENE,
    terrain: (q.get('terrain') ?? 'flat') as TerrainKind,
    terrainSize: Number(q.get('size') ?? DEFAULT_SCENE.terrainSize),
    trees: q.get('trees') === '1',
    layer: { mode: (q.get('layer') ?? 'all') as LayerMode, y: Number(q.get('y') ?? 0) },
    markers: q.get('markers') === '1',
  };
  await showStructure(viewer, lib, s, opts);
  // "cam_<azimuth>_<elevation>_<distance multiple>_<target height fraction>" = free camera (hero shots)
  const views = (q.get('views') ?? 'iso,front,right,top').split(',').filter((v) => PRESETS.includes(v as CameraPreset) || v.startsWith('cam_'));
  const out: Record<string, string> = {};
  for (const v of views) {
    if (v.startsWith('cam_')) {
      const [, az, el, dm, tf] = v.split('_');
      viewer.custom(Number(az), Number(el), Number(dm), Number(tf));
    } else viewer.preset(v as CameraPreset);
    viewer.render();
    out[v] = canvas.toDataURL('image/png');
  }
  window.__result = { views: out, placeholders: lib.placeholderCount, assets: info.kind };
}

main().catch((e) => {
  window.__error = String(e?.stack ?? e);
  console.error(e);
});
