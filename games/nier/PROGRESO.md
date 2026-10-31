# Port nativo de NieR:Automata a Android — progreso

Última actualización: 2026-10-31 (sesión 1, nocturna)

## Estado en una línea

En PC arranca, pasa la pantalla de sistema YoRHa, el título, Nueva partida (ranura, nombre, dificultad) y **juega el
prólogo en 3D** (unidades de vuelo sobre las nubes, 2B/12H, rayos) con la imagen correcta. Rendimiento en PC todavía
bajo en el prólogo (~10 fps en una RTX 4060: ~80 MB de vértices subidos por frame y 20 ms de write-backs). Android:
lanzador con las opciones de P5R v0.5.0 + mods; APK arm64 (`private/nier/apk_arm64/nier.apk`) y x86_64. **En el
emulador Android (x86_64) funciona**: lanzador ("Data ready"), arranque YoRHa con los controles táctiles, título y
pantalla de Nueva partida (~9,5 fps en el emulador). Falta probarlo en un teléfono/tablet ARM real.

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

9. **Lanzador Android** (`games/nier/java/io/github/nierrecomp`, paquete `io.github.nierrecomp`, datos en
   `/storage/emulated/0/NieR Automata`): resolución 360p/480p/**720p**/1080p, pantalla completa (estirar), gráficos,
   sombras, **30**/60 fps, contador de FPS (apagado), controles táctiles (tamaño, opacidad, editor de posiciones con
   L3/R3), sensibilidad del stick y de la cámara táctil, mando Nintendo/Xbox, exportar registros
   (`Download/nier-log.txt`), bloom experimental (apagado), partidas (importar/exportar `user/save`), idiomas, atajo con
   imagen propia y **Mods** (instalar .zip/.cpk en `mods/<nombre>/romfs`, activar/desactivar renombrando a `_nombre`).
   Comprueba `extract/romfs/data000.cpk`, `spirv/results.tsv` y `analysis/main.bin`. Icono neutro de texto.
10. **Fusión de `p5r-main` v0.5.0** (los arreglos de `GUIA_ARQUITECTURA_PORTS_ANDROID.md`: Xclipse BC por CPU, Mali
    R11G11B10F a RGBA16F, Adreno 8xx sin trocear envíos, Android 10, mods sin distinguir mayúsculas, L3/R3,
    sensibilidad, SixAxis, mando Xbox, exportar logs). Sin conflictos; NieR sigue arrancando en PC.
11. Controles táctiles: NieR usa la tabla de P5R (`touch_layout.cpp`, ahora con L3/R3).
12. `android/build_apk.ps1` de la rama `p4g` (opción `-Abis`, para compilar también x86_64 para el emulador), y las
    banderas de CMake y el manejador de muestreo de `platform_android.cpp` para x86_64 (también de `p4g`).
13. **Arreglo de compilación Android**: la v0.5.0 de P5R (commit `254b1a0` de `p5r-main`) perdió un `switch (k) {` en
    `platform_android_input.cpp` al añadir el mapeo Xbox/Nintendo: el APK no compilaba. Arreglado en `nier` (la rama
    `p4g` ya lo tenía bien). **Ojo: `p5r-main` sigue con el fallo** (no lo toqué: es la zona de la otra sesión).
14. **Prueba en el emulador** (AVD propio **`NieR_Dev`**, Android 15 x86_64, 32 GB, `emulator-5580`; script
    `C:\Users\brand\nier-port\emu_run.sh <nombre> <s> <cada>`, capturas en `nier-port\emu_runs`): datos copiados a
    `/sdcard/NieR Automata` (analysis, spirv, extract/romfs), APK x86_64 instalado, `appops ... MANAGE_EXTERNAL_STORAGE
    allow`. El lanzador ve los datos; el juego arranca y llega a la pantalla NEW GAME (en inglés: el idioma del emulador).
    Enter = A y las flechas = cruceta sirven para manejarlo con `adb shell input keyevent`.

### Pruebas de rendimiento (PC, RTX 4060, sin ventana, vuelo del prólogo, `nier-port/bench.sh`)

| Variante | fps | Notas |
|---|---|---|
| Base | **9,8** | replay 26 ms, espera de GPU 36 ms, write-backs 20,7 ms (6/frame, 5,16 MB comparados, 0 % cambiado), ~79 MB subidos/frame |
| `NX_BUFFER_CACHE=1` | 9,8 | sin cambio: las subidas son vértices que cambian (1 % igual a la copia anterior) |
| `NX_VERTEX_TRIM=1` (nuevo: solo el rango de vértices que lee cada draw) | 9,1 | los bytes no bajan (~76 MB/frame); había otra carga en el PC. Queda apagado |
| **`NX_HANDHELD=1` (720p, lo que usa Android por defecto)** | **16,5** (20 al final) | write-backs a la mitad (2,35 MB, 9 ms): dependen de la resolución (búferes de cómputo del tamaño de la pantalla); replay 13 ms, GPU 22 ms |
| `NX_DEFER_WRITE_BACKS=0` | 11,1 | +13 % con una sola muestra (14 write-backs/frame, 1 % cambiado): repetir antes de cambiar el valor por defecto |
| Emulador Android x86_64 (menú, 720p) | ~9,5 | replay 60 ms (CPU del emulador); no representa un móvil ARM |
| 720p, A/B repetido: `NX_DEFER_WRITE_BACKS=0` / por defecto | 16,9 / 16,8 | sin diferencia: se queda como está |

`NX_LOG_BIG_VERTEX=<bytes>` (nuevo, `runs/bigv`): los flujos grandes son vértices de efectos que la CPU reescribe cada
frame (mismo búfer `0x1145254a0`, 1,2–3,3 MB, stride 28, tamaño creciente) en draws cuyo número de vértices el runtime
no conoce (rango "unknown": probablemente indirectos), así que la copia es necesaria. Lo siguiente: sumar bytes por
dirección para ver qué más llega a ~65 MB/frame.

`NX_UPLOAD_STATS=1` (`runs/diag1`): vértices 96.297 copias y 28,8 GB en la ventana, 1 % iguales; índices 326 MB (100 %
iguales); uniform/storage 502 MB (25 %). En el título: 13,7 fps.

## Falta ⚠️

1. **Probar el APK en un dispositivo** (`private/nier/apk/nier.apk`). Copiar a `NieR Automata/` en el teléfono:
   `analysis/` (main.bin, sdk.bin, main.npdm), `extract/romfs/` (11,6 GB) y `spirv/` (todo de `private/nier`).
2. Rendimiento: qué vértices cambian cada frame (¿nubes/partículas escritas por la CPU, anillos dinámicos?), por qué
   la GPU tarda 36 ms en una RTX 4060 y por qué los write-backs (0 % cambiado) cuestan 20 ms (lectura de memoria no
   cacheada en PC). Medir en la tablet.
3. Películas: en PC salen verdes/negras (sin VP9); comprobar en Android (`AMediaCodec`).
4. Preparación de datos en el teléfono sin PC (como P5R v0.4): extractor de shaders DAT en C++.
5. 60 fps: NieR no tiene parche de fps (el lanzador la marca experimental).
6. Persona Q/Q2 los actualiza otra sesión (v0.3.3, 2026-10-31). P4G/P4AU: pendiente aplicarles lo de la guía.
