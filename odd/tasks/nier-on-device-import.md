# nier-on-device-import

## Objective
NieR:Automata with a ready APK like P5R, no PC needed: pick the base game (`.xci`/`.nsp`), update 1.0.3 (`.nsp`) and the keys on the phone, press Prepare, play.

## Problem
v0.1.0 (published as a pre-release) needs a PC: the game data (RomFS with update 1.0.3 applied, ExeFS) and the translated shaders are prepared with nximport, cpk.py, nier_shaders.py and the .NET translator.
- The runtime's in-place reader (nxgame.h) does not apply updates.
- NieR needs 1.0.3 (BKTR).

## Approach
- Import on the phone with the existing importer (android/wonder/importer, `nxi::run_import` with `keep_exefs`). It writes `<data>/extract/romfs` with the BKTR update applied and `<data>/exefs/{main,sdk}`, exactly the layout the NieR NCE build reads. Cost: about 12 GB of storage.
- Shaders on the phone: games/nier/import/nier_shaders.cpp, a C++ port of cpk.py (as games/p5r/import/p5r_shaders.cpp does), dat.py, nier_shaders.py and containers.Stage. data000.cpk → `*shader*.dat` → `.vso/.pso/.gso/.cso` stages → manifest.tsv plus code and control files, in the same layout as the PC pipeline.
- Translation on the phone: libspirvharness.so built from the nier branch (NieR's translator reports host subgroup size 128), with Docker, into private/build/spirv_harness_android_nier (P5R's 32-wide build stays untouched).
- Launcher: pickers for the base game, update and keys, a "Prepare the game" button opening ImportActivity (its own `:import` process, as in P5R), then Play.

## Tasks
- [x] N1 nier_shaders.cpp + PC CLI: parity with the Python output, identical manifest (1371 rows), 2445 files with identical contents, 1219 distinct code blocks, 1.3 s (zig c++ build of shaders_cli.cpp) — route: inline
- [ ] N2 nier_import_jni.cpp + CMake target nierimport + build_apk.ps1 block — route: inline
- [ ] N3 Android translator (Docker, NieR variant)
- [ ] N4 Java: Importer, ImportActivity, Launcher pickers and Prepare flow, manifest
- [ ] N5 Tablet test (HA269JK2): copy the XCI + update + keys, import, translate, play (the existing 11 GB data folder gets overwritten in place; `user/` saves are kept)
- [ ] N6 Docs (README without a PC), release v0.2.0 on nier-android, NieR Discord announcement (held until now)

## Checks
- N1: manifest rows and file set identical to the Python output.
- Build: `games\nier\tools\build_android.cmd -Nce`.
- Device: the import completes, Play reaches the title screen.

## Delivery
ask-on-risk; forecast about 900 authored lines (C++ about 400, Java about 350, build about 50, docs about 100).

## Progress
- Created 2026-11-07. The user approved: NieR first, then the Persona Q repo; test on the tablet.
