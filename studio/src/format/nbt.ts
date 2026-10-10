// Minimal NBT (Minecraft's binary format) reader and writer, plus conversion between the studio format and
// vanilla structure files (.nbt, gzip), the format the mod loads from data/csr/structure/.
import { gunzipSync, gzipSync } from 'fflate';
import {
  AIR_BLOCK,
  VOID_BLOCK,
  blockStateToString,
  fromBlocks,
  parseBlockState,
  toGrid,
  type StructureFile,
  type StructureMeta,
} from './structure.ts';

/** Data version of Minecraft 1.21.1, the mod's target. */
export const DATA_VERSION_1_21_1 = 3955;

// Tag ids
const END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9,
  COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

export type Nbt =
  | { type: 'byte' | 'short' | 'int' | 'float' | 'double'; value: number }
  | { type: 'long'; value: bigint }
  | { type: 'string'; value: string }
  | { type: 'byteArray' | 'intArray'; value: number[] }
  | { type: 'longArray'; value: bigint[] }
  | { type: 'list'; itemType: number; value: Nbt[] }
  | { type: 'compound'; value: Record<string, Nbt> };

export const nbt = {
  int: (value: number): Nbt => ({ type: 'int', value }),
  string: (value: string): Nbt => ({ type: 'string', value }),
  list: (itemType: number, value: Nbt[]): Nbt => ({ type: 'list', itemType: value.length ? itemType : END, value }),
  compound: (value: Record<string, Nbt>): Nbt => ({ type: 'compound', value }),
  INT,
  COMPOUND,
  STRING,
};

class Reader {
  private view: DataView;
  pos = 0;
  constructor(private bytes: Uint8Array) {
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  }
  u8() { return this.view.getUint8(this.pos++); }
  i8() { return this.view.getInt8(this.pos++); }
  i16() { const v = this.view.getInt16(this.pos); this.pos += 2; return v; }
  u16() { const v = this.view.getUint16(this.pos); this.pos += 2; return v; }
  i32() { const v = this.view.getInt32(this.pos); this.pos += 4; return v; }
  i64() { const v = this.view.getBigInt64(this.pos); this.pos += 8; return v; }
  f32() { const v = this.view.getFloat32(this.pos); this.pos += 4; return v; }
  f64() { const v = this.view.getFloat64(this.pos); this.pos += 8; return v; }
  str() {
    const len = this.u16();
    const s = new TextDecoder().decode(this.bytes.subarray(this.pos, this.pos + len));
    this.pos += len;
    return s;
  }
  payload(type: number): Nbt {
    switch (type) {
      case BYTE: return { type: 'byte', value: this.i8() };
      case SHORT: return { type: 'short', value: this.i16() };
      case INT: return { type: 'int', value: this.i32() };
      case LONG: return { type: 'long', value: this.i64() };
      case FLOAT: return { type: 'float', value: this.f32() };
      case DOUBLE: return { type: 'double', value: this.f64() };
      case BYTE_ARRAY: { const n = this.i32(); const a: number[] = []; for (let i = 0; i < n; i++) a.push(this.i8()); return { type: 'byteArray', value: a }; }
      case STRING: return { type: 'string', value: this.str() };
      case LIST: {
        const itemType = this.u8(); const n = this.i32(); const value: Nbt[] = [];
        for (let i = 0; i < n; i++) value.push(this.payload(itemType));
        return { type: 'list', itemType, value };
      }
      case COMPOUND: {
        const value: Record<string, Nbt> = {};
        for (;;) {
          const t = this.u8();
          if (t === END) break;
          const name = this.str();
          value[name] = this.payload(t);
        }
        return { type: 'compound', value };
      }
      case INT_ARRAY: { const n = this.i32(); const a: number[] = []; for (let i = 0; i < n; i++) a.push(this.i32()); return { type: 'intArray', value: a }; }
      case LONG_ARRAY: { const n = this.i32(); const a: bigint[] = []; for (let i = 0; i < n; i++) a.push(this.i64()); return { type: 'longArray', value: a }; }
      default: throw new Error(`unknown NBT tag ${type} at byte ${this.pos}`);
    }
  }
}

class Writer {
  private chunks: number[] = [];
  u8(v: number) { this.chunks.push(v & 0xff); }
  i16(v: number) { this.u8(v >> 8); this.u8(v); }
  i32(v: number) { this.u8(v >> 24); this.u8(v >> 16); this.u8(v >> 8); this.u8(v); }
  i64(v: bigint) { const b = new DataView(new ArrayBuffer(8)); b.setBigInt64(0, v); for (let i = 0; i < 8; i++) this.u8(b.getUint8(i)); }
  f32(v: number) { const b = new DataView(new ArrayBuffer(4)); b.setFloat32(0, v); for (let i = 0; i < 4; i++) this.u8(b.getUint8(i)); }
  f64(v: number) { const b = new DataView(new ArrayBuffer(8)); b.setFloat64(0, v); for (let i = 0; i < 8; i++) this.u8(b.getUint8(i)); }
  str(s: string) { const b = new TextEncoder().encode(s); this.i16(b.length); for (const x of b) this.u8(x); }
  payload(t: Nbt) {
    switch (t.type) {
      case 'byte': this.u8(t.value); break;
      case 'short': this.i16(t.value); break;
      case 'int': this.i32(t.value); break;
      case 'long': this.i64(t.value); break;
      case 'float': this.f32(t.value); break;
      case 'double': this.f64(t.value); break;
      case 'byteArray': this.i32(t.value.length); t.value.forEach((v) => this.u8(v)); break;
      case 'string': this.str(t.value); break;
      case 'list': this.u8(t.itemType); this.i32(t.value.length); t.value.forEach((v) => this.payload(v)); break;
      case 'compound':
        for (const [k, v] of Object.entries(t.value)) { this.u8(tagId(v)); this.str(k); this.payload(v); }
        this.u8(END);
        break;
      case 'intArray': this.i32(t.value.length); t.value.forEach((v) => this.i32(v)); break;
      case 'longArray': this.i32(t.value.length); t.value.forEach((v) => this.i64(v)); break;
    }
  }
  bytes() { return Uint8Array.from(this.chunks); }
}

function tagId(t: Nbt): number {
  return { byte: BYTE, short: SHORT, int: INT, long: LONG, float: FLOAT, double: DOUBLE, byteArray: BYTE_ARRAY,
    string: STRING, list: LIST, compound: COMPOUND, intArray: INT_ARRAY, longArray: LONG_ARRAY }[t.type];
}

export function readNbt(bytes: Uint8Array): Nbt {
  const raw = bytes[0] === 0x1f && bytes[1] === 0x8b ? gunzipSync(bytes) : bytes;
  const r = new Reader(raw);
  const type = r.u8();
  r.str();
  return r.payload(type);
}

export function writeNbt(root: Nbt, gzip = true): Uint8Array {
  const w = new Writer();
  w.u8(tagId(root));
  w.str('');
  w.payload(root);
  return gzip ? gzipSync(w.bytes()) : w.bytes();
}

/** Studio structure -> vanilla structure NBT. Structure void cells are left out, like vanilla's structure block. */
export function structureToNbt(s: StructureFile, dataVersion = DATA_VERSION_1_21_1): Uint8Array {
  const g = toGrid(s);
  const [sx, sy, sz] = g.size;
  const palette: Nbt[] = g.states.map((st) => {
    const c: Record<string, Nbt> = { Name: nbt.string(st.name) };
    const keys = Object.keys(st.properties);
    if (keys.length) c.Properties = nbt.compound(Object.fromEntries(keys.map((k) => [k, nbt.string(st.properties[k])])));
    return nbt.compound(c);
  });
  const blocks: Nbt[] = [];
  for (let y = 0; y < sy; y++)
    for (let z = 0; z < sz; z++)
      for (let x = 0; x < sx; x++) {
        const idx = g.cells[(y * sz + z) * sx + x];
        if (g.states[idx].name === VOID_BLOCK) continue;
        blocks.push(nbt.compound({ pos: nbt.list(INT, [nbt.int(x), nbt.int(y), nbt.int(z)]), state: nbt.int(idx) }));
      }
  return writeNbt(
    nbt.compound({
      DataVersion: nbt.int(dataVersion),
      size: nbt.list(INT, [nbt.int(sx), nbt.int(sy), nbt.int(sz)]),
      palette: nbt.list(COMPOUND, palette),
      blocks: nbt.list(COMPOUND, blocks),
      entities: nbt.list(COMPOUND, []),
    }),
  );
}

function asCompound(t: Nbt | undefined): Record<string, Nbt> {
  if (!t || t.type !== 'compound') throw new Error('expected a compound tag');
  return t.value;
}
function asList(t: Nbt | undefined): Nbt[] {
  if (!t || t.type !== 'list') throw new Error('expected a list tag');
  return t.value;
}
function num(t: Nbt): number {
  if (t.type === 'int' || t.type === 'short' || t.type === 'byte') return t.value;
  throw new Error('expected a number tag');
}

export interface NbtImport {
  structure: StructureFile;
  /** Blocks whose block-entity data (chest contents, sign text) was dropped. */
  droppedBlockEntities: number;
  entities: number;
}

/** Vanilla structure NBT -> studio structure. Positions with no block are structure void. */
export function nbtToStructure(bytes: Uint8Array, name: string, meta: StructureMeta): NbtImport {
  const root = asCompound(readNbt(bytes));
  const size = asList(root.size).map(num) as [number, number, number];
  // Some files use "palettes" (several random palettes): take the first.
  const paletteTag = root.palette ?? (root.palettes ? asList(root.palettes)[0] : undefined);
  const palette = asList(paletteTag).map((p) => {
    const c = asCompound(p);
    const nameTag = c.Name;
    const props: Record<string, string> = {};
    if (c.Properties) for (const [k, v] of Object.entries(asCompound(c.Properties))) props[k] = v.type === 'string' ? v.value : '';
    return blockStateToString({ name: nameTag?.type === 'string' ? nameTag.value : AIR_BLOCK, properties: props });
  });
  const [sx, sy, sz] = size;
  const cells: string[] = new Array(sx * sy * sz).fill(VOID_BLOCK);
  let dropped = 0;
  for (const b of asList(root.blocks)) {
    const c = asCompound(b);
    const [x, y, z] = asList(c.pos).map(num);
    cells[(y * sz + z) * sx + x] = palette[num(c.state)];
    if (c.nbt) dropped++;
  }
  const structure = fromBlocks(name, 1, size, (x, y, z) => cells[(y * sz + z) * sx + x], meta);
  // Normalise the namespace in case the palette had odd spacing.
  for (const k of Object.keys(structure.palette)) structure.palette[k] = blockStateToString(parseBlockState(structure.palette[k]));
  return { structure, droppedBlockEntities: dropped, entities: root.entities ? asList(root.entities).length : 0 };
}
