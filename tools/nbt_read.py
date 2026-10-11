"""Minimal reader for gzipped NBT files (vanilla structure files). Standard library only.

  from nbt_read import read_nbt
  root = read_nbt("x.nbt")   # dict / list / int / float / str / bytes, like the NBT tree
"""
import gzip
import struct


class _Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, n):
        chunk = self.data[self.pos:self.pos + n]
        self.pos += n
        return chunk

    def unpack(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))[0]

    def string(self):
        return self.take(self.unpack(">H")).decode("utf-8", errors="replace")

    def payload(self, tag):
        if tag == 1:
            return self.unpack(">b")
        if tag == 2:
            return self.unpack(">h")
        if tag == 3:
            return self.unpack(">i")
        if tag == 4:
            return self.unpack(">q")
        if tag == 5:
            return self.unpack(">f")
        if tag == 6:
            return self.unpack(">d")
        if tag == 7:
            return self.take(self.unpack(">i"))
        if tag == 8:
            return self.string()
        if tag == 9:
            inner = self.unpack(">b")
            return [self.payload(inner) for _ in range(self.unpack(">i"))]
        if tag == 10:
            out = {}
            while True:
                inner = self.unpack(">b")
                if inner == 0:
                    return out
                name = self.string()
                out[name] = self.payload(inner)
        if tag == 11:
            n = self.unpack(">i")
            return list(struct.unpack(f">{n}i", self.take(4 * n)))
        if tag == 12:
            n = self.unpack(">i")
            return list(struct.unpack(f">{n}q", self.take(8 * n)))
        raise ValueError(f"unknown NBT tag {tag} at byte {self.pos}")


def read_nbt(path):
    with gzip.open(path, "rb") as f:
        reader = _Reader(f.read())
    tag = reader.unpack(">b")
    reader.string()
    return reader.payload(tag)
