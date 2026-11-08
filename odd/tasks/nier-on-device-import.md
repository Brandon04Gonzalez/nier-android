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
- [x] N2 nier_import_jni.cpp (run_import with keep_exefs, the build ID check 3C10EC35C5EF4C36, shaders, harness) + CMake target nierimport + build_apk.ps1 block — route: inline
- [x] N3 Android translator: games/nier/tools/build_harness_android.sh, Docker with p5r-port's third_party mounted at /src/private/third_party (a junction does not resolve in the container) → private/build/spirv_harness_android_nier/libspirvharness.so, 4.58 MB; P5R's copy untouched
- [x] N4 Java: Importer, ImportActivity (:import), Launcher (base/update/keys pickers, play button walks pick→prepare, status texts), manifest, v0.2.0 (versionCode 2). nier.apk builds: 3.6 MB with libnierimport.so and libspirvharness.so
- [x] N5 Tablet test passed (HA269JK2, Lenovo Tab M11):
  - Files picked through DocumentsUI. The adb-pushed files needed a media scan to show up in Downloads.
  - Preparation took about 2.5 min: import of 11105 MB, build 3C10EC35C5EF4C36, 1219 shaders.
  - Play reaches the intro and 3D gameplay at 30 fps.
  - The importer's up-front free-space check (11.71 GB) does not count the existing files it would overwrite, so the old `extract/` (8.6 GB) was removed for the test; `user/` was kept.
- [x] N6 Published:
  - README v0.2.0 (2569182), pushed to nier-android main.
  - Release v0.2.0 (latest) with nier.apk (3.78 MB); repo description updated.
  - Profile README line updated.
  - Bilingual Discord post in #announcements-anuncios.
  - Left on the tablet for the user to decide: /sdcard/Download/nier_test (the XCI, update and keys pushed for the test).

## Checks
- N1: manifest rows and file set identical to the Python output.
- Build: `games\nier\tools\build_android.cmd -Nce`.
- Device: the import completes, Play reaches the title screen.

## Delivery
ask-on-risk; forecast about 900 authored lines (C++ about 400, Java about 350, build about 50, docs about 100).

## Progress
- Commits: e1a23d7 (N1 shaders), ae279fb (N2–N4 import, translator, Java, v0.2.0). README for v0.2.0 edited, not committed yet (after the device test).
- N5 setup on the tablet:
  - Deleted only `NieR Automata/extract/romfs/data014.cpk` (2.4 GB; the importer writes `<file>.part` and then renames, so it needs room for the largest file).
  - Renamed `spirv/results.tsv` to `results.tsv.bak` so the launcher asks to prepare.
  - Pushed the XCI, the update and the keys to /sdcard/Download/nier_test; installed nier.apk 0.2.0.
- Created 2026-11-07. The user approved: NieR first, then the Persona Q repo; test on the tablet.
