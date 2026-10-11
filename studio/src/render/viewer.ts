// The 3D view: sky, sun with soft shadows, chunk meshes in three passes (solid, cutout, translucent), orbit and
// free-fly cameras, camera presets and scale markers. One Viewer = one canvas.
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { BlockLibrary, LAYER_CUTOUT, LAYER_SOLID, LAYER_TRANSLUCENT } from './blocks.ts';
import { meshRegion, meshTerrain, type ChunkMeshData } from './mesher.ts';
import type { World } from './world.ts';

export type CameraPreset = 'front' | 'back' | 'left' | 'right' | 'top' | 'iso' | 'iso_back';
export const PRESETS: CameraPreset[] = ['iso', 'front', 'right', 'back', 'left', 'top', 'iso_back'];

const SKY_TOP = new THREE.Color('#5d8de3');
const SKY_HORIZON = new THREE.Color('#bcd6f5');

export interface ViewerOptions {
  canvas?: HTMLCanvasElement;
  width?: number;
  height?: number;
  /** keep the drawing buffer so the canvas can be read back (screenshots, thumbnails) */
  preserve?: boolean;
  shadowMapSize?: number;
}

export class Viewer {
  readonly renderer: THREE.WebGLRenderer;
  readonly scene = new THREE.Scene();
  readonly camera: THREE.PerspectiveCamera;
  readonly controls: OrbitControls;
  readonly sun: THREE.DirectionalLight;
  private materials: THREE.Material[];
  private placementGroup = new THREE.Group();
  private terrainGroup = new THREE.Group();
  private markers = new THREE.Group();
  private world: World | null = null;
  /** Fog distances measured from the point the camera looks at (render() adds the camera's distance to it). */
  private fogBase = { near: 120, far: 400 };
  private lib: BlockLibrary | null = null;
  /** Box the camera presets and the sun's shadow frame around (the structures). */
  focus = new THREE.Box3(new THREE.Vector3(0, 0, 0), new THREE.Vector3(8, 8, 8));
  private fly: FlyControls;
  private running = false;
  onFrame: (() => void) | null = null;

  constructor(opts: ViewerOptions = {}) {
    const canvas = opts.canvas ?? document.createElement('canvas');
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: !!opts.preserve, alpha: false });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.NoToneMapping;
    const w = opts.width ?? (canvas.clientWidth || 800), h = opts.height ?? (canvas.clientHeight || 600);
    this.renderer.setSize(w, h, false);

    this.camera = new THREE.PerspectiveCamera(45, w / h, 0.05, 2000);
    this.camera.position.set(20, 15, -20);
    this.controls = new OrbitControls(this.camera, canvas);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.12;
    this.controls.screenSpacePanning = true;
    this.controls.maxPolarAngle = Math.PI * 0.499;
    this.fly = new FlyControls(this.camera, canvas, this.controls);

    // Sky dome with a vertical gradient, and fog of the horizon colour.
    const skyGeo = new THREE.SphereGeometry(1000, 32, 16);
    const skyMat = new THREE.ShaderMaterial({
      side: THREE.BackSide,
      depthWrite: false,
      fog: false,
      uniforms: { top: { value: SKY_TOP }, horizon: { value: SKY_HORIZON } },
      vertexShader: 'varying vec3 vP; void main(){ vP = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
      fragmentShader:
        'uniform vec3 top; uniform vec3 horizon; varying vec3 vP; void main(){ float t = pow(clamp(vP.y,0.0,1.0),0.55); vec3 c = mix(horizon, top, t); if (vP.y < 0.0) c = horizon * 0.92; gl_FragColor = vec4(c,1.0); \n#include <colorspace_fragment>\n }',
    });
    const sky = new THREE.Mesh(skyGeo, skyMat);
    sky.renderOrder = -1;
    sky.frustumCulled = false;
    this.scene.add(sky);
    this.scene.background = SKY_HORIZON;
    this.scene.fog = new THREE.Fog(SKY_HORIZON, 120, 400);
    sky.onBeforeRender = (_r, _s, cam) => sky.position.copy(cam.position);

    this.scene.add(new THREE.HemisphereLight(0xdfeaff, 0x8a7a60, 1.35));
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.35));
    this.sun = new THREE.DirectionalLight(0xfff3dd, 2.1);
    this.sun.castShadow = true;
    const sm = opts.shadowMapSize ?? 4096;
    this.sun.shadow.mapSize.set(sm, sm);
    this.sun.shadow.bias = -0.0004;
    this.sun.shadow.normalBias = 0.03;
    this.sun.shadow.radius = 3;
    this.scene.add(this.sun, this.sun.target);

    this.materials = [];
    this.scene.add(this.terrainGroup, this.placementGroup, this.markers);
  }

  private makeMaterials(atlas: THREE.Texture) {
    for (const m of this.materials) m.dispose();
    const solid = new THREE.MeshLambertMaterial({ map: atlas, vertexColors: true });
    const cutout = new THREE.MeshLambertMaterial({ map: atlas, vertexColors: true, alphaTest: 0.5, side: THREE.DoubleSide });
    cutout.shadowSide = THREE.DoubleSide;
    const translucent = new THREE.MeshLambertMaterial({
      map: atlas, vertexColors: true, transparent: true, depthWrite: false, side: THREE.DoubleSide,
    });
    this.materials = [solid, cutout, translucent];
  }

  setSize(w: number, h: number) {
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
  }

  /** Shows a world: meshes the structures and the terrain. Call again after the world or its filters change. */
  setWorld(world: World, lib: BlockLibrary) {
    this.world = world;
    if (this.lib !== lib || !this.materials.length || (this.materials[0] as THREE.MeshLambertMaterial).map !== lib.atlas) {
      this.lib = lib;
      this.makeMaterials(lib.atlas!);
    }
    this.rebuildTerrain();
    this.rebuildPlacements();
    // fade the far terrain edge into the horizon
    const reach = world.terrain ? world.terrain.size / 2 : 200;
    this.fogBase = { near: Math.max(40, reach * 0.55), far: Math.max(80, reach * 1.05) };
  }

  private addChunks(group: THREE.Group, chunks: ChunkMeshData[]) {
    for (const c of chunks) {
      c.layers.forEach((g, layer) => {
        if (!g) return;
        const mesh = new THREE.Mesh(g, this.materials[layer]);
        mesh.castShadow = layer !== LAYER_TRANSLUCENT;
        mesh.receiveShadow = true;
        if (layer === LAYER_SOLID) mesh.renderOrder = 0;
        else if (layer === LAYER_CUTOUT) mesh.renderOrder = 1;
        else mesh.renderOrder = 2;
        group.add(mesh);
      });
    }
  }

  private clear(group: THREE.Group) {
    for (const c of [...group.children]) {
      (c as THREE.Mesh).geometry?.dispose();
      group.remove(c);
    }
  }

  rebuildPlacements() {
    if (!this.world || !this.lib) return;
    this.clear(this.placementGroup);
    const box = new THREE.Box3();
    for (const p of this.world.placements) {
      this.addChunks(this.placementGroup, meshRegion(this.world, this.lib, 'placements', p.min, p.max));
      box.union(new THREE.Box3(new THREE.Vector3(...p.min), new THREE.Vector3(...p.max)));
    }
    if (!box.isEmpty()) this.focus = box;
    this.fitSun();
  }

  rebuildTerrain() {
    if (!this.world || !this.lib) return;
    this.clear(this.terrainGroup);
    this.addChunks(this.terrainGroup, meshTerrain(this.world, this.lib));
  }

  /** Sun from the south-east, high; shadow frame fitted around the structures plus a margin. */
  fitSun() {
    const c = this.focus.getCenter(new THREE.Vector3());
    const size = this.focus.getSize(new THREE.Vector3());
    const r = Math.max(size.x, size.y, size.z) * 0.9 + 10;
    const dir = new THREE.Vector3(0.55, 1.0, 0.75).normalize();
    this.sun.position.copy(c).addScaledVector(dir, r * 2);
    this.sun.target.position.copy(c);
    const cam = this.sun.shadow.camera;
    cam.left = -r; cam.right = r; cam.top = r; cam.bottom = -r;
    cam.near = 0.5; cam.far = r * 4;
    cam.updateProjectionMatrix();
    this.sun.shadow.needsUpdate = true;
  }

  /** Puts the camera at a preset angle around the focus box. Front = the structure's z = 0 side (north). */
  preset(p: CameraPreset) {
    if (this.fly.active) this.fly.exit();
    const c = this.focus.getCenter(new THREE.Vector3());
    const size = this.focus.getSize(new THREE.Vector3());
    const radius = size.length() / 2;
    const fov = (this.camera.fov * Math.PI) / 180;
    const fitH = radius / Math.sin(fov / 2);
    const fitW = radius / Math.sin(Math.atan(Math.tan(fov / 2) * this.camera.aspect));
    const dist = Math.max(fitH, fitW) * 0.92 + 1;
    const dirs: Record<CameraPreset, [number, number, number]> = {
      front: [0, 0.32, -1],
      back: [0, 0.32, 1],
      left: [-1, 0.32, 0],
      right: [1, 0.32, 0],
      top: [0, 1, -0.0001], // front (z = 0) at the bottom of the screen, like a floor plan
      iso: [0.85, 0.75, -1],
      iso_back: [-0.85, 0.75, 1],
    };
    const d = new THREE.Vector3(...dirs[p]).normalize();
    const target = c.clone();
    if (p !== 'top') target.y = this.focus.min.y + size.y * 0.42;
    this.controls.target.copy(target);
    this.camera.position.copy(target).addScaledVector(d, dist);
    this.camera.up.set(0, 1, 0);
    this.camera.lookAt(target);
    this.controls.update();
  }

  /** Free camera for hero shots: azimuth (degrees, 0 = front/north side, 90 = east), elevation (degrees above the
   *  horizon), distance as a multiple of the fit distance, and the look-at height as a fraction of the structure. */
  custom(az: number, el: number, distMul: number, targetFrac: number) {
    if (this.fly.active) this.fly.exit();
    const c = this.focus.getCenter(new THREE.Vector3());
    const size = this.focus.getSize(new THREE.Vector3());
    const radius = size.length() / 2;
    const fov = (this.camera.fov * Math.PI) / 180;
    const fitH = radius / Math.sin(fov / 2);
    const fitW = radius / Math.sin(Math.atan(Math.tan(fov / 2) * this.camera.aspect));
    const dist = (Math.max(fitH, fitW) * 0.92 + 1) * distMul;
    const a = (az * Math.PI) / 180, e = (el * Math.PI) / 180;
    const d = new THREE.Vector3(Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e));
    const target = c.clone();
    target.y = this.focus.min.y + size.y * targetFrac;
    this.controls.target.copy(target);
    this.camera.position.copy(target).addScaledVector(d, dist);
    this.camera.up.set(0, 1, 0);
    this.camera.lookAt(target);
    this.controls.update();
  }

  copyCameraFrom(other: Viewer) {
    this.camera.position.copy(other.camera.position);
    this.camera.quaternion.copy(other.camera.quaternion);
    this.controls.target.copy(other.controls.target);
  }

  /** Scale references next to the structure: a player (1.8 blocks) and a clay soldier (about 0.5 blocks). */
  setMarkers(show: boolean) {
    this.clear(this.markers);
    if (!show) return;
    const f = this.focus;
    const groundY = this.world?.terrain ? this.world.terrain.height(Math.floor(f.min.x) - 2, Math.floor(f.min.z) - 2) + 1 : f.min.y;
    const player = makePlayer();
    player.position.set(f.min.x - 1.5, groundY, f.min.z - 1.5);
    const soldier = makeSoldier();
    soldier.position.set(f.min.x - 0.5, groundY, f.min.z - 1.5);
    this.markers.add(player, soldier);
  }

  enterFly() { this.fly.enter(); }
  get flying() { return this.fly.active; }

  render() {
    if (this.fly.active) this.fly.update();
    else this.controls.update();
    this.onFrame?.();
    // fog starts beyond what the camera looks at, so big structures (seen from far away) stay clear
    const fog = this.scene.fog as THREE.Fog;
    const d = this.camera.position.distanceTo(this.controls.target);
    fog.near = this.fogBase.near + d;
    fog.far = this.fogBase.far + d;
    this.renderer.render(this.scene, this.camera);
  }

  start() {
    if (this.running) return;
    this.running = true;
    const loop = () => {
      if (!this.running) return;
      this.render();
      requestAnimationFrame(loop);
    };
    requestAnimationFrame(loop);
  }
  stop() { this.running = false; }

  dispose() {
    this.stop();
    this.clear(this.placementGroup);
    this.clear(this.terrainGroup);
    this.controls.dispose();
    this.fly.dispose();
    this.renderer.dispose();
  }
}

function boxMesh(w: number, h: number, d: number, color: number, y: number) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color }));
  m.position.y = y + h / 2;
  m.castShadow = true;
  m.receiveShadow = true;
  return m;
}

function makePlayer() {
  const g = new THREE.Group();
  g.add(boxMesh(0.5, 0.75, 0.25, 0x2d3a8c, 0)); // legs
  g.add(boxMesh(0.5, 0.75, 0.25, 0x2fa8a8, 0.75)); // shirt
  g.add(boxMesh(0.5, 0.5, 0.5, 0xc69c6d, 1.3)); // head (top at 1.8)
  const arms = boxMesh(1.0, 0.75, 0.25, 0xc69c6d, 0.75);
  arms.scale.x = 1;
  g.add(arms);
  g.userData.label = 'player (1.8 blocks)';
  return g;
}

function makeSoldier() {
  const g = new THREE.Group();
  g.add(boxMesh(0.14, 0.2, 0.08, 0xb03030, 0));
  g.add(boxMesh(0.14, 0.18, 0.08, 0xc03a3a, 0.2));
  g.add(boxMesh(0.13, 0.13, 0.13, 0xc84444, 0.38));
  g.userData.label = 'clay soldier (about 0.5 blocks)';
  return g;
}

/** Free-fly camera: click the view, then WASD to move, Space/Shift up/down, mouse to look, Esc to leave. */
class FlyControls {
  active = false;
  private keys = new Set<string>();
  private yaw = 0;
  private pitch = 0;
  private last = performance.now();
  private onKey = (e: KeyboardEvent) => {
    if (!this.active) return;
    if (e.type === 'keydown') this.keys.add(e.code);
    else this.keys.delete(e.code);
  };
  private onMouse = (e: MouseEvent) => {
    if (!this.active || document.pointerLockElement !== this.canvas) return;
    this.yaw -= e.movementX * 0.0025;
    this.pitch = Math.max(-1.55, Math.min(1.55, this.pitch - e.movementY * 0.0025));
  };
  private onLock = () => {
    if (document.pointerLockElement !== this.canvas && this.active) this.exit();
  };
  constructor(private camera: THREE.PerspectiveCamera, private canvas: HTMLCanvasElement, private orbit: OrbitControls) {
    window.addEventListener('keydown', this.onKey);
    window.addEventListener('keyup', this.onKey);
    document.addEventListener('mousemove', this.onMouse);
    document.addEventListener('pointerlockchange', this.onLock);
  }
  enter() {
    const e = new THREE.Euler().setFromQuaternion(this.camera.quaternion, 'YXZ');
    this.yaw = e.y;
    this.pitch = e.x;
    this.active = true;
    this.orbit.enabled = false;
    this.last = performance.now();
    this.canvas.requestPointerLock?.();
  }
  exit() {
    this.active = false;
    this.keys.clear();
    this.orbit.enabled = true;
    // keep orbiting around a point in front of the camera
    const fwd = new THREE.Vector3(0, 0, -1).applyQuaternion(this.camera.quaternion);
    this.orbit.target.copy(this.camera.position).addScaledVector(fwd, 10);
    if (document.pointerLockElement === this.canvas) document.exitPointerLock();
  }
  update() {
    const now = performance.now();
    const dt = Math.min(0.1, (now - this.last) / 1000);
    this.last = now;
    this.camera.quaternion.setFromEuler(new THREE.Euler(this.pitch, this.yaw, 0, 'YXZ'));
    const speed = (this.keys.has('ControlLeft') ? 30 : 10) * dt;
    const fwd = new THREE.Vector3(-Math.sin(this.yaw), 0, -Math.cos(this.yaw));
    const right = new THREE.Vector3(Math.cos(this.yaw), 0, -Math.sin(this.yaw));
    const v = new THREE.Vector3();
    if (this.keys.has('KeyW')) v.add(fwd);
    if (this.keys.has('KeyS')) v.sub(fwd);
    if (this.keys.has('KeyD')) v.add(right);
    if (this.keys.has('KeyA')) v.sub(right);
    if (this.keys.has('Space')) v.y += 1;
    if (this.keys.has('ShiftLeft')) v.y -= 1;
    if (v.lengthSq()) this.camera.position.addScaledVector(v.normalize(), speed);
  }
  dispose() {
    window.removeEventListener('keydown', this.onKey);
    window.removeEventListener('keyup', this.onKey);
    document.removeEventListener('mousemove', this.onMouse);
    document.removeEventListener('pointerlockchange', this.onLock);
  }
}
