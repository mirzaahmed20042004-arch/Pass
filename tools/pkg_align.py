#!/usr/bin/env python3
"""Pack aapt2 output + classes.dex into one zip, and (re)align zip entries.

Usage:
  pkg_align.py pack  <base.apk> <classes.dex> <out.apk>
  pkg_align.py align <in.apk> <out.apk>

Alignment rules: resources.arsc is stored uncompressed and 4-byte aligned
(required by Android 11+); every other STORED entry is 4-byte aligned;
DEFLATED entries need no alignment.
"""
import io
import struct
import sys
import zipfile
import zlib


def read_entries(path):
    out = []
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            out.append([info.filename, info.compress_type, z.read(info)])
    return out


def align_of(name):
    return 4


def write_zip(entries, path, force):
    """entries: [name, method, data]; force: dict name->method overrides."""
    buf = io.BytesIO()
    central = []
    offset = 0
    for name, method, data in entries:
        method = force.get(name, method)
        name_b = name.encode("utf-8")
        crc = zlib.crc32(data) & 0xFFFFFFFF
        if method == zipfile.ZIP_STORED:
            comp = data
        else:
            comp = zlib.compress(data, 9)[2:-4]
        extra = b""
        if method == zipfile.ZIP_STORED:
            pad = (-(offset + 30 + len(name_b))) % align_of(name)
            extra = b"\x00" * pad
        local = struct.pack(
            "<IHHHHHIIIHH",
            0x04034B50, 20, 0, method, 0, 0x21,
            crc, len(comp), len(data), len(name_b), len(extra),
        )
        buf.write(local)
        buf.write(name_b)
        buf.write(extra)
        buf.write(comp)
        central.append((name_b, method, crc, len(comp), len(data), offset))
        offset = buf.tell()
    cd_off = offset
    cd = io.BytesIO()
    for name_b, method, crc, csz, usz, off in central:
        cd.write(struct.pack(
            "<IHHHHHHIIIHHHHHII",
            0x02014B50, 20, 20, 0, method, 0, 0x21,
            crc, csz, usz, len(name_b), 0, 0, 0, 0, 0, off,
        ))
        cd.write(name_b)
    cd_bytes = cd.getvalue()
    buf.write(cd_bytes)
    buf.write(struct.pack(
        "<IHHHHIIH",
        0x06054B50, 0, 0, len(central), len(central), len(cd_bytes), cd_off, 0,
    ))
    with open(path, "wb") as f:
        f.write(buf.getvalue())


def main():
    cmd = sys.argv[1]
    if cmd == "pack":
        base, dex, out = sys.argv[2], sys.argv[3], sys.argv[4]
        entries = read_entries(base)
        with open(dex, "rb") as f:
            dex_bytes = f.read()
        entries.append(["classes.dex", zipfile.ZIP_DEFLATED, dex_bytes])
        force = {"resources.arsc": zipfile.ZIP_STORED}
        write_zip(entries, out, force)
    elif cmd == "align":
        src, out = sys.argv[2], sys.argv[3]
        entries = read_entries(src)
        write_zip(entries, out, {})
    else:
        raise SystemExit("unknown command " + cmd)
    print("wrote", out)


if __name__ == "__main__":
    main()
