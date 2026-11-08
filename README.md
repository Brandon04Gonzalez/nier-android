# NieR:Automata for Android (native port, preview)

An Android app that runs **your own copy** of NieR:Automata The End of YoRHa Edition (Nintendo Switch, update 1.0.3)
natively on an arm64 phone or tablet. The Switch's operating system services, its audio, video and input, and NVN
(the Switch's graphics API) are reimplemented on Android, with Vulkan for the graphics. The game's own code runs
directly on the phone's CPU: the Switch and Android phones share the same CPU architecture (AArch64).

> [!WARNING]
> **Preview: this one needs a PC once.** Unlike the Persona ports, NieR has no on-phone import yet: the game's files
> and its translated shaders are prepared on a Windows PC from your own dump and then copied to the phone (see
> [Preparing the data](#preparing-the-data-on-a-pc)). It is meant for people comfortable with command-line tools.

> [!IMPORTANT]
> **No game files are included, here or in the APK.** This repository and its releases hold no game code, assets,
> shaders, keys or firmware. The APK is only the engine: it reads the game from the data you prepare from your own
> dump. You need your own legally dumped copy of the game (the base game and update 1.0.3) and your own keys. How to
> dump them from your console is **not** covered here. Please do not ask for game files or keys.

NieR:Automata is © SQUARE ENIX. This is an unofficial fan project, not affiliated with or endorsed by SQUARE ENIX,
PlatinumGames, Virtuos or Nintendo.

## Community and support

- **Discord** (English and Spanish channels), for news, help and testing: https://discord.gg/rMgRneBTCe
- **Support the developer (optional):** [GitHub Sponsors](https://github.com/sponsors/Brandon04Gonzalez). These ports
  are free and made for preservation. Donating is entirely voluntary and helps with the costs of making them (test
  devices, hardware, servers). Nothing is locked behind a donation.

## Preparing the data on a PC

You need Windows, [Python 3](https://www.python.org/), the [.NET 10 SDK](https://dotnet.microsoft.com/), CMake and
Ninja (`pip install cmake ninja`), a C++ compiler (Visual Studio Build Tools, run the commands from a *Developer
PowerShell*), your dumps of the **base game** (`.xci` or `.nsp`, `010056B015FE8000`) and **update 1.0.3**
(`010056B015FE8800`, v131072), and your `prod.keys` and `title.keys`. From a clone of this repository:
```powershell
# 1. the importer, then the game's files with the update applied (about 12 GB, a few minutes)
cmake -S android/wonder/importer -B private/build/importer -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build private/build/importer
private\build\importer\nximport.exe --exefs --title 010056B015FE8000 --out private\nier prod.keys title.keys `
  "NieR base game.xci" "NieR update 1.0.3.nsp"
# 2. the shaders: out of the game's CPK, then to SPIR-V with Ryujinx's translator (Ryubing's source first)
python games/p5r/tools/cpk.py private/nier/extract/romfs/data000.cpk --extract private/nier/extract/cpk --grep shader
python games/nier/tools/nier_shaders.py private/nier/extract/cpk private/nier/shaders_extracted/nier
mkdir private\third_party\ryubing
curl.exe -L -o private\third_party\ryubing.tar.gz https://codeberg.org/Software_arch/Ryubing/archive/a82350bb77.tar.gz
tar -xzf private\third_party\ryubing.tar.gz -C private\third_party\ryubing --strip-components=1
dotnet build tools/maxwell/spirv_harness -c Release -p:NxBuildDir=spirv_harness_nier
dotnet private/build/spirv_harness_nier/bin/Release/net10.0/spirv_harness.dll --batch private/nier/shaders_extracted `
  --out private/nier/spirv --jobs 8 --no-val
```
Then copy `private\nier\exefs`, `private\nier\extract\romfs` and `private\nier\spirv` to the phone, into
`Internal storage/NieR Automata/` (so that `NieR Automata/exefs/main`, `NieR Automata/extract/romfs/data000.cpk` and
`NieR Automata/spirv/results.tsv` exist). Keep that data to yourself: it is made from your copy of the game.

## Install

1. Prepare and copy the data as above.
2. Install **NieR** from the [Releases](../../releases) page and open it; allow "All files access".
3. The home screen checks the data folder (and that `exefs/main` is update 1.0.3) and tells you what is missing.
   Press **Play**.

### Options

Resolution (360p to 1080p), fullscreen (stretch), graphics (texture detail), 30 or 60 fps, shadows, minimum dynamic
resolution, bloom (experimental), FPS counter, touch controls (size, opacity, a layout editor with L3/R3), touch stick
and camera sensitivity, gamepad layout (Nintendo or Xbox), text and subtitle language, mods (install a `.zip` with a
`romfs` folder, or a `.cpk`, and turn them on or off), custom GPU drivers (Turnip), save import/export, log export, and
a home screen shortcut with a picture of your own.

## Requirements

- An arm64 Android phone or tablet with **Android 10 or newer** and **Vulkan 1.1**, 8 GB of RAM recommended.
- About 13 GB of storage for the prepared data.

## Status

A preview: it reaches the prologue and plays, and it has been tested on few devices so far. Expect rough edges
on weaker GPUs. Reports are very welcome, on Discord or in [Issues](../../issues): your phone model, Android version,
what happened and the log (**Export logs** on the home screen).

## How it works

- **Native execution (the loader).** The APK reads `main` and `sdk` from the data folder's ExeFS, maps and links them as
  the Switch's loader would, and runs them on the phone's CPU (`runtime/src/nce/`). System calls and the few
  instructions that cannot run on Android as they are get patched into small stubs.
- **The runtime** is the Switch runtime of
  [Super Mario Bros. Wonder Recomp](https://github.com/BelmanteGu/SuperMarioWonderRecomp) (SwitchGamesRecomp, MIT,
  by BelmanteGu / Projeto Belmante), shared with the [Persona 5 Royal port](https://github.com/Brandon04Gonzalez/p5r-android).
  This port adds NieR's needs (`games/nier/`): separate textures and samplers in NVN, several mixed audio outputs
  (Wwise and films), Opus, the system keyboard, and more.
- **Shaders** come from Ryujinx's shader translator (Ryubing, MIT), run on the PC for this preview.

## Building the APK

The same toolchain as the [P5R port](https://github.com/Brandon04Gonzalez/p5r-android#building-the-apk), then:
```powershell
games\nier\tools\build_android.cmd -Nce           # -> private\nier\apk_nce\nier.apk; -Install installs it with adb
```

## En español

Una app de Android que ejecuta **tu propia copia** de NieR:Automata The End of YoRHa Edition (Switch, actualización
1.0.3) de forma nativa en un móvil o tablet arm64. El APK no lleva nada del juego.

- **Discord** (canales en español e inglés), para novedades, ayuda y pruebas: https://discord.gg/rMgRneBTCe
- **Apoyar al desarrollador (opcional):** [GitHub Sponsors](https://github.com/sponsors/Brandon04Gonzalez). Los ports
  son gratis y se hacen para preservación. Donar es totalmente voluntario y ayuda con los gastos que conlleva hacerlos
  (dispositivos de prueba, equipo, servidores). No hay nada de pago.

**Es una versión preview y necesita un PC una vez.** A diferencia de los Persona, todavía no importa el juego en el
móvil: los archivos del juego y sus shaders traducidos se preparan en un PC con Windows a partir de tu dump (juego base
+ actualización 1.0.3, con tus `prod.keys` y `title.keys`) siguiendo los comandos de
[Preparing the data](#preparing-the-data-on-a-pc). Después copia `exefs`, `extract/romfs` y `spirv` a
`Almacenamiento interno/NieR Automata/`, instala **NieR** desde [Releases](../../releases), permite el acceso a todos los
archivos y pulsa **Jugar**: la pantalla de inicio te dice si falta algo. Pensado para quien se maneja con la línea de
comandos; si te atascas, pregunta en Discord. No se proporcionan juegos ni claves.

## Credits and license

- Runtime: [SwitchGamesRecomp / Super Mario Bros. Wonder Recomp](https://github.com/BelmanteGu/SuperMarioWonderRecomp)
  by BelmanteGu (Projeto Belmante), MIT.
- Shader translation: Ryujinx's shader translator (Ryubing), MIT.
- The native-execution idea is the one Switch emulators call NCE; this implementation is written for this runtime.
- Like the upstream project, this port was written with a lot of AI assistance, directed and tested by a human.

MIT License, see [LICENSE](LICENSE). Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
