# Port nativo de NieR:Automata a Android — progreso

Última actualización: 2026-10-31 (sesión 1, nocturna)

## Estado en una línea

En PC arranca, pasa la pantalla de sistema YoRHa, el título, Nueva partida (ranura, nombre, dificultad) y **juega el
prólogo en 3D** (unidades de vuelo sobre las nubes, 2B/12H, rayos) con la imagen correcta. Rendimiento en PC todavía
bajo en el prólogo (~10 fps en una RTX 4060: 86 MB subidos por frame y 20 ms de write-backs). Android: lanzador en
preparación (opciones de P5R), APK aún no probado.

## Cómo funciona el port

Igual que P5R, P4G y P4AU: **recompilación estática** del dump de Switch (AArch64 → C++) con el runtime
**SwitchGamesRecomp**, que reimplementa NVN sobre Vulkan. Lo específico de NieR va en `games/nier/`. No es un emulador.

- Juego: NieR:Automata The End of YoRHa Edition (Switch, USA, `010056B015FE8000`), **actualización 1.0.3**
  (v131072, build id de `main` `3C10EC35C5EF4C36`). Motor de PlatinumGames, versión de Switch de Virtuos. SDK 12.3.9.
- Módulos: `main` (46.363 funciones, acaba en 44 MiB → el sdk va en `0x4010000000` como P5R), `sdk` (38.710),
  `subsdk0` = `nn::movie` (no se recompila: `hle_movie.cpp`).
- `main.npdm`: recurso de sistema `0x1000000` (16 MB) → `kInfo.system_resource_size`.
- RomFS (11,6 GB, 660 archivos): `data0xx/1xx.cpk` (CRI CPK con archivos DAT/DTT de Platinum), `movie/*.usm`
  (CRI USM, VP9 por `movie::Decoder`), `movie_logo/`, `sound/` (Wwise: `.bnk`, `stream/`), `Enlighten/`.
- Shaders: archivos DAT en `data000.cpk` (`shader.dat`, `shader2.dat`, `shadereff.dat`, `shadereffcs.dat`,
  `autoshadereff.dat`). Cada miembro (`.vso/.pso/.gso/.cso`) es una etapa: cabecera de 0x88 bytes, tamaño del código en
  +0x88, código NVN en +0x8C y la sección de control NVN (0x880 bytes) al final. **1.219 bloques distintos, todos
  traducidos** (11 s).
- Sonido: Wwise 2021.1 sobre `AudioOut` (dos salidas a la vez: Wwise y el reproductor de películas) y Opus por el
  decodificador "hardware" (`hwopus`).

## Dónde está todo

| Qué | Ruta |
|---|---|
| Repo de trabajo (worktree, rama local **`nier`** desde `p5r-main` 645c717) | `C:\Users\brand\nier-port\recomp` |
| Código propio del port | `games\nier\` (game.cpp, tools\, java\, AndroidManifest.xml) |
| Datos derivados del juego (nunca se publican) | `private\nier\` (analysis, exefs, extract\romfs, gen, spirv, build, runs) |
| Tus dumps | `C:\Users\brand\Downloads\nierauto port android\` (XCI base, NSP update 1.0.3, NSP DLC) |
| `private\venv`, `third_party`, `keys`, importador y traductor | uniones (junctions) a los de `p5r-port\recomp\private` |
| Scripts de la sesión | `C:\Users\brand\nier-port\br.sh` (compila + prueba), `bench.sh` (A/B en el prólogo) |

> Nada de `private\` ni ningún APK/EXE construido con tus datos debe subirse ni compartirse. La rama `nier` es local.

## Comandos

```bash
cd /c/Users/brand/nier-port/recomp
P=private/venv/Scripts/python
K=C:/Users/brand/AppData/Roaming/Ryujinx/system
D="C:/Users/brand/Downloads/nierauto port android"
# 1. importar XCI base + update (el importador aplica el parche BKTR): ~80 s, 660 archivos, 11,6 GB
private/build/importer/nximport.exe --exefs --title 010056B015FE8000 --out private/nier "$K/prod.keys" "$K/title.keys" \
  "$D/NieRAutomata The End of YoRHa Edition [010056B015FE8000][USA][v0].xci" \
  "$D/NieRAutomata The End of YoRHa Edition [010056B015FE8800][v131072][US][Up v1.0.3].nsp"
# 2. recompilar la CPU (~95 s main, ~40 s sdk)
$P -m recomp private/nier/exefs/main private/nier/gen/main --name main --base 0x4000000000
$P -m recomp private/nier/exefs/sdk  private/nier/gen/sdk  --name sdk  --base 0x4010000000
# 3. shaders
$P games/p5r/tools/cpk.py private/nier/extract/romfs/data000.cpk --extract private/nier/extract/cpk --grep shader
$P games/nier/tools/nier_shaders.py private/nier/extract/cpk private/nier/shaders_extracted/nier
dotnet private/build/spirv_harness/bin/Release/net10.0/spirv_harness.dll --batch private/nier/shaders_extracted --out private/nier/spirv --jobs 8 --no-val
```

```powershell
# PC: compilar y probar (log en private\nier\runs\<nombre>\nx.log, capturas en frames\)
cmd /c C:\Users\brand\nier-port\recomp\games\nier\tools\build_pc.cmd -j14
C:\Users\brand\nier-port\recomp\games\nier\tools\run_pc.ps1 -Name prueba -Seconds 90 -Headless -DumpEvery 60
```

Ruta de teclas hasta el prólogo (PC, sin ventana): A cada 4 s desde los 58 s; desde los 86 s IZQUIERDA + A (el diálogo
"¿Crear datos guardados?" deja el cursor en **No**); luego A cada 5 s. El prólogo en vuelo empieza hacia los 170 s.
Está en `C:\Users\brand\nier-port\bench.sh`.

## Hecho ✅

### Sesión 1 (2026-10-31)

1. Importación (XCI + update 1.0.3), recompilación de `main`/`sdk`, extractor de DAT (`games/nier/tools/dat.py`) y de
   shaders (`nier_shaders.py`), 1.219 shaders a SPIR-V sin fallos.
2. **Recompilador**: `sqshl` (registro, vectorial; 8 usos en `main`).
3. **hid**: `GetTouchScreenState<1>` (NieR pide 1 toque; el runtime solo tenía `<16>`).
4. **NVN: texturas y samplers separados** (NieR los usa en casi todos los shaders):
   `nvnDeviceGetSeparateTextureHandle/SamplerHandle`, `nvnCommandBufferBindSeparateTexture(s)/Sampler(s)`. El
   traductor empaqueta los dos handles en el nombre (`t_cb196610_1056F25C`: Ryujinx `PackSlots`/`PackOffsets`); el
   runtime lo tomaba como banco 196610 e indexaba fuera de un array (caída). Medido en NieR: en `c[2]` las texturas
   separadas están en las palabras **90 + 2n** y los samplers separados en **346 + 2n** (128 texturas: 90 + 256 = 346).
   Con 88/344 el texto salía como bloques grises (se leía la ranura de al lado). `NX_LOG_SEPARATE=1` registra los binds.
5. **Audio: varias salidas `AudioOut` mezcladas** (Wwise + películas) y el `OpenDefaultAudioOut` de 2 argumentos del
   SDK 12. Con una sola salida se mantiene el camino de siempre (P5R/P4G).
6. **Opus**: `hwopus` sobre libopus, traído de la rama `p4au` (`runtime/src/hle_opus.cpp` + bloque de CMake).
7. **Teclado del sistema (swkbd)**: devuelve `NX_SWKBD_TEXT` o el apodo de la cuenta (antes, texto vacío → "Debes
   escribir un nombre de dos o más caracteres" en bucle).
8. **En PC llega al prólogo jugable** (`runs/ng3`): sistema YoRHa ("CARGANDO – INICIANDO SISTEMA", en español),
   título, Nueva partida, nombre, dificultad, vuelo con la escuadra, etiquetas IFF, rayos. Películas en negro/verde en
   PC (sin decodificador VP9 en Windows; en Android, `AMediaCodec`).

## Falta ⚠️

1. Rendimiento: 86 MB subidos por frame, write-backs de 5 MB/frame (0 % cambiado, 20 ms), ~10 fps en PC en el vuelo.
2. APK de Android (lanzador con las opciones de P5R) y prueba en un dispositivo.
3. Fusionar los arreglos de P5R de `GUIA_ARQUITECTURA_PORTS_ANDROID.md` (Xclipse BC, Mali R11G11B10F, Adreno 8xx,
   Android 10, mods sin mayúsculas, L3/R3, SixAxis, mando Xbox, exportar logs) cuando estén confirmados en `p5r-main`.
4. Preparación de datos en el teléfono sin PC (como P5R v0.4): extractor de shaders DAT en C++.
