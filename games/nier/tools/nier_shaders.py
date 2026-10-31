#!/usr/bin/env python3
"""NieR:Automata keeps its NVN shaders in DAT archives of romfs data000.cpk (shader.dat, shader2.dat, shadereff.dat,
shadereffcs.dat, autoshadereff.dat; debug/DebugAutoShaderEffInfo.dat holds none). Every member (.vso/.pso/.gso/.cso)
is one stage:

    +0x00  u32 0x88 (header size)          +0x04..+0x87  0xFF (unused binding slots)
    +0x88  u32 code size                   +0x8C         code block (0x30 NVN header, SPH, program, constants)
    then   the NVN control section (0x880 bytes, magic 0x98761234), 16-byte aligned, to the end of the file

Extract all of them into one group, like containers.py --extract --unique, with a single manifest.tsv.

Usage:
  python games/p5r/tools/cpk.py private/nier/extract/romfs/data000.cpk --extract private/nier/extract/cpk --grep shader
  python games/nier/tools/nier_shaders.py private/nier/extract/cpk private/nier/shaders_extracted/nier
"""
import hashlib
import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "tools", "maxwell"))
sys.path.insert(0, HERE)
import containers as C  # noqa: E402
import dat  # noqa: E402

CODE_OFF = 0x8C
KIND = {".vso": "vertex", ".pso": "pixel", ".gso": "geometry", ".cso": "compute"}


def stage_of(sid, name, data):
    kind = KIND.get(os.path.splitext(name)[1].lower())
    if not kind or len(data) < CODE_OFF or struct.unpack_from("<I", data, 0)[0] != 0x88:
        return None
    code_size = struct.unpack_from("<I", data, 0x88)[0]
    ctrl_off = data.find(struct.pack("<I", C.CONTROL_MAGIC), CODE_OFF + code_size)
    if ctrl_off < 0:
        return None
    return C.Stage(sid, kind, CODE_OFF, code_size, ctrl_off, len(data) - ctrl_off, data)


def main():
    src, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    archives = sorted(os.path.relpath(os.path.join(d, f), src).replace("\\", "/")
                      for d, _, fs in os.walk(src) for f in fs if f.lower().endswith(".dat"))
    written, n_stages, bad = set(), 0, 0
    with open(os.path.join(out, "manifest.tsv"), "w", newline="\n") as m:
        m.write("id\tstage\tcode_off\tcode_size\tctrl_off\tctrl_size\tprogram_start\tprogram_bytes\tconst_off\t"
                "const_size\tcode_sha1_16\tcode_file\tctrl_file\n")
        for arc in archives:
            try:
                members = dat.read_dat(os.path.join(src, arc))
            except SystemExit:
                continue
            for name, _, _, data in members:
                sid = f"{arc}/{name}"
                s = stage_of(sid, name, data)
                if s is None:
                    continue
                if s.problems:
                    print(f"{sid}: {', '.join(s.problems)}")
                    bad += 1
                    continue
                code = data[s.code_off:s.code_off + s.code_size]
                ctrl = data[s.ctrl_off:s.ctrl_off + s.ctrl_size]
                code_file = f"code_{s.hash}.bin"
                ctrl_file = f"ctrl_{hashlib.sha1(bytes(ctrl)).hexdigest()[:16]}.bin"
                for fn, blob in ((code_file, code), (ctrl_file, ctrl)):
                    if fn not in written:
                        written.add(fn)
                        open(os.path.join(out, fn), "wb").write(blob)
                m.write(f"{s.id}\t{s.kind}\t{s.code_off:#x}\t{s.code_size:#x}\t{s.ctrl_off:#x}\t{s.ctrl_size:#x}\t"
                        f"{s.start:#x}\t{s.length:#x}\t{s.const_off:#x}\t{s.const_size:#x}\t{s.hash}\t{code_file}\t"
                        f"{ctrl_file}\n")
                n_stages += 1
    print(f"{n_stages} stages, {len(written)} files, {bad} stages rejected -> {out}")


if __name__ == "__main__":
    main()
