"""PlatinumGames DAT/DTT archives (NieR:Automata): list or extract.

    python dat.py <x.dat> [--list] [--extract <dir>]

Header: "DAT\0", count, offsets table, extensions table, names table (u32 name length, then fixed-size names),
sizes table, hash map (unused here).
"""
import argparse
import os
import struct


def read_dat(path):
    d = open(path, "rb").read()
    if d[:4] != b"DAT\0":
        raise SystemExit(f"{path}: not a DAT archive")
    n, o_off, o_ext, o_names, o_sizes = struct.unpack_from("<5I", d, 4)
    offs = struct.unpack_from(f"<{n}I", d, o_off)
    sizes = struct.unpack_from(f"<{n}I", d, o_sizes)
    nlen = struct.unpack_from("<I", d, o_names)[0]
    names = []
    for i in range(n):
        raw = d[o_names + 4 + i * nlen: o_names + 4 + (i + 1) * nlen]
        names.append(raw.split(b"\0")[0].decode("ascii", "replace"))
    return [(names[i], offs[i], sizes[i], d[offs[i]:offs[i] + sizes[i]]) for i in range(n)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dat")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--extract")
    a = ap.parse_args()
    files = read_dat(a.dat)
    if a.list or not a.extract:
        for name, off, size, data in files:
            print(f"0x{off:08x} {size:9d} {data[:4].hex()} {name}")
    if a.extract:
        os.makedirs(a.extract, exist_ok=True)
        for name, off, size, data in files:
            open(os.path.join(a.extract, name), "wb").write(data)
        print(f"extracted {len(files)} files")


if __name__ == "__main__":
    main()
