"""Tiny NBT reader/writer (big-endian, gzip) for structure templates. No dependencies.

Types are kept explicit on write: use the helper classes Byte/Short/Int/Long/Float/Double/IntArray/List for anything
that is not a str or dict (dict = compound). Plain Python ints are written as TAG_Int.
"""
import gzip
import io
import struct


class Tag:
    def __init__(self, value):
        self.value = value

    def __repr__(self):
        return f"{type(self).__name__}({self.value!r})"


class Byte(Tag): pass
class Short(Tag): pass
class Int(Tag): pass
class Long(Tag): pass
class Float(Tag): pass
class Double(Tag): pass
class IntArray(Tag): pass


class List(Tag):
    """List of tags; elem_type is the tag id used when the list is empty."""

    def __init__(self, value, elem_type=None):
        super().__init__(value)
        self.elem_type = elem_type


IDS = {Byte: 1, Short: 2, Int: 3, Long: 4, Float: 5, Double: 6, str: 8, List: 9, dict: 10, IntArray: 11}


def _id(value):
    if isinstance(value, bool):
        return 1
    if isinstance(value, int):
        return 3
    if isinstance(value, float):
        return 6
    return IDS[type(value)]


def _write_payload(out, value):
    tid = _id(value)
    if tid == 1:
        out.write(struct.pack(">b", int(value.value if isinstance(value, Tag) else value)))
    elif tid == 2:
        out.write(struct.pack(">h", value.value))
    elif tid == 3:
        out.write(struct.pack(">i", value.value if isinstance(value, Tag) else value))
    elif tid == 4:
        out.write(struct.pack(">q", value.value))
    elif tid == 5:
        out.write(struct.pack(">f", value.value))
    elif tid == 6:
        out.write(struct.pack(">d", value.value if isinstance(value, Tag) else value))
    elif tid == 8:
        data = value.encode("utf-8")
        out.write(struct.pack(">H", len(data)))
        out.write(data)
    elif tid == 9:
        items = value.value
        etype = _id(items[0]) if items else (value.elem_type or 0)
        out.write(struct.pack(">bi", etype, len(items)))
        for item in items:
            _write_payload(out, item)
    elif tid == 10:
        for key, item in value.items():
            out.write(struct.pack(">b", _id(item)))
            _write_payload(out, key)
            _write_payload(out, item)
        out.write(b"\x00")
    elif tid == 11:
        out.write(struct.pack(">i", len(value.value)))
        for v in value.value:
            out.write(struct.pack(">i", v))


def write(path, root):
    buf = io.BytesIO()
    buf.write(b"\x0a")
    _write_payload(buf, "")
    _write_payload(buf, root)
    with gzip.open(path, "wb") as f:
        f.write(buf.getvalue())


def _read_payload(data, tid):
    if tid == 1:
        return Byte(struct.unpack(">b", data.read(1))[0])
    if tid == 2:
        return Short(struct.unpack(">h", data.read(2))[0])
    if tid == 3:
        return struct.unpack(">i", data.read(4))[0]
    if tid == 4:
        return Long(struct.unpack(">q", data.read(8))[0])
    if tid == 5:
        return Float(struct.unpack(">f", data.read(4))[0])
    if tid == 6:
        return struct.unpack(">d", data.read(8))[0]
    if tid == 7:
        n = struct.unpack(">i", data.read(4))[0]
        return list(data.read(n))
    if tid == 8:
        n = struct.unpack(">H", data.read(2))[0]
        return data.read(n).decode("utf-8")
    if tid == 9:
        etype, n = struct.unpack(">bi", data.read(5))
        return List([_read_payload(data, etype) for _ in range(n)], etype)
    if tid == 10:
        out = {}
        while True:
            t = struct.unpack(">b", data.read(1))[0]
            if t == 0:
                return out
            key = _read_payload(data, 8)
            out[key] = _read_payload(data, t)
    if tid == 11:
        n = struct.unpack(">i", data.read(4))[0]
        return IntArray(list(struct.unpack(f">{n}i", data.read(4 * n))))
    if tid == 12:
        n = struct.unpack(">i", data.read(4))[0]
        return Tag(list(struct.unpack(f">{n}q", data.read(8 * n))))
    raise ValueError(f"tag {tid}")


def read(raw):
    data = io.BytesIO(gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw)
    tid = struct.unpack(">b", data.read(1))[0]
    _read_payload(data, 8)
    return _read_payload(data, tid)
