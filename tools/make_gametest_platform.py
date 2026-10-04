"""Generates a GameTest template with a smooth-stone floor at y=0 (DataVersion 3955 = MC 1.21.1).
Default size 16x8x16 (gametest/platform.nbt); the terrain tests use 32x16x32 (gametest/terrain.nbt).

Usage: python3 tools/make_gametest_platform.py neoforge/src/main/resources/data/csr/structure/gametest/platform.nbt
       python3 tools/make_gametest_platform.py neoforge/src/main/resources/data/csr/structure/gametest/terrain.nbt 32 16 32
"""
import gzip, struct, sys
def s(x): b=x.encode(); return struct.pack('>H',len(b))+b
def named(t,name,payload): return bytes([t])+s(name)+payload
def i(v): return struct.pack('>i',v)
def lst_int(vals): return bytes([3])+i(len(vals))+b''.join(i(v) for v in vals)
def compound(entries): return b''.join(entries)+b'\x00'
SIZE=tuple(int(v) for v in sys.argv[2:5]) if len(sys.argv) >= 5 else (16,8,16)
blocks=[]
for x in range(SIZE[0]):
    for z in range(SIZE[2]):
        blocks.append(compound([named(9,'pos',lst_int([x,0,z])), named(3,'state',i(0))]))
palette=[compound([named(8,'Name',s('minecraft:smooth_stone'))])]
root=compound([
  named(3,'DataVersion',i(3955)),
  named(9,'size',lst_int(list(SIZE))),
  named(9,'palette',bytes([10])+i(len(palette))+b''.join(palette)),
  named(9,'blocks',bytes([10])+i(len(blocks))+b''.join(blocks)),
  named(9,'entities',bytes([10])+i(0)),
])
data=named(10,'',root)
with gzip.open(sys.argv[1],'wb') as f: f.write(data)
