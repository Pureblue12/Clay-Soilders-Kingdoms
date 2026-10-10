// Writes a sample's current draft to a file:  npx tsx samples/make.ts <house|keep|wall> <out.json>
import fs from 'node:fs';
import { stringifyStructure } from '../src/format/structure.ts';
import { house } from './house.ts';

const makers: Record<string, () => ReturnType<typeof house>> = { house };
const [which, out] = process.argv.slice(2);
const make = makers[which];
if (!make || !out) {
  console.error(`usage: npx tsx samples/make.ts <${Object.keys(makers).join('|')}> <out.json>`);
  process.exit(1);
}
fs.writeFileSync(out, stringifyStructure(make()));
console.log(`wrote ${out}`);
