# Port nativo de NieR:Automata a Android — progreso

Última actualización: 2026-11-02 (sesión 5)

## Estado en una línea

En PC arranca, pasa la pantalla de sistema YoRHa, el título, Nueva partida (ranura, nombre, dificultad) y **juega el
prólogo en 3D** (unidades de vuelo sobre las nubes, 2B/12H, rayos, el tutorial de disparo) con la imagen correcta.
Rendimiento en PC en el vuelo: 9,8 fps a 1080p, **~17 fps a 720p** (con el recorte de vértices las subidas bajan a la
mitad, 123 → 63 MB por frame; en PC apenas +2–3 % de fps). Android:
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
$P -m recomp private/nier/exefs/main private/nier/gen/main --name main --base 0x4000000000 --hookable 9bf1d0,8fc7a8,9d514c
$P -m recomp private/nier/exefs/sdk  private/nier/gen/sdk  --name sdk  --base 0x4010000000
# 3. shaders
$P games/p5r/tools/cpk.py private/nier/extract/romfs/data000.cpk --extract private/nier/extract/cpk --grep shader
$P games/nier/tools/nier_shaders.py private/nier/extract/cpk private/nier/shaders_extracted/nier
# traductor propio de NieR (subgrupos de 128): dotnet build -c Release -p:NxBuildDir=spirv_harness_nier en tools/maxwell/spirv_harness
dotnet private/build/spirv_harness_nier/bin/Release/net10.0/spirv_harness.dll --batch private/nier/shaders_extracted --out private/nier/spirv --jobs 8 --no-val
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
    Enter = A y las flechas = cruceta sirven para manejarlo con `adb shell input keyevent`. En el emulador el sonido
    sale por AAudio y los vídeos usan el decodificador VP9 del sistema (`c2.goldfish.vp9.decoder`); con el recorte de
    vértices también arranca sin errores.

### Sesión 2 (2026-10-31, primera prueba en la tablet)

Tablet Lenovo **TB331FC** (Snapdragon 680, **Adreno 610**, 8 GB, Android 14), APK v0.1.0 del 31/10 08:56.

15. **Caída al empezar Nueva partida y tras ~4 diálogos de la partida cargada** (las dos iguales: SIGSEGV en el hilo
    `g70`, `main_00C82610`, `ldr x8, [x8, #0x10]` con la vtable nula o basura, `lr main+c82674`). Es la cola-anillo de
    comandos del juego (productor `main+cab840`/`cab944`, consumidor `main+cab968`/`cab9c4`; cola en `obj+0x228`: +0
    búfer, +8 capacidad, +0x10 índice de lectura, +0x14 índice publicado, +0x18 máscara). Tras un marcador de "vuelta
    al principio" el consumidor espera con `ldr w9, [x1, #0x14]; cmp; b.eq` a que el productor publique; con
    `nx::ld` (memcpy normal) **clang borraba ese bucle** (regla de progreso de C++: un bucle sin efectos se puede
    suponer que termina) y el consumidor leía el hueco antes de que el productor lo escribiera. Comprobado en el
    desensamblado de `libnier.so`. Las barreras (`dmb`, `ldar`/`stlr`, `ldxr`/`stxr`) ya estaban bien traducidas.
    **Arreglo en el recompilador** (`recomp/emit.py`, `poll_loads`): en un bucle sin escrituras, llamadas ni atómicos
    que lee una dirección que el bucle no cambia, la carga pasa a `nx::ld_poll` (volátil + barrera acquire) si es una
    espera pura, o a `nx::ld_vol` (solo volátil) si el bucle además cuenta (búsquedas acotadas por un campo). NieR:
    `main` 1.359 / 3.488, `sdk` 266 / 1.122 (`report.txt`). Sirve igual para P5R/P4G/P4AU al regenerar.
    **Probado en la tablet** (APK del 31/10 16:37): 5 min en el título sin caídas, y el usuario cargó la partida y
    siguió jugando más allá del punto donde se cerraba, sin caídas. La versión de PC con este cambio no está
    recompilada ni probada todavía. Para la ruta de teclas por adb hace falta `input keyevent --longpress` (a 13 fps
    el juego no ve una pulsación corta) y en el diálogo de confirmación hay que pulsar DERECHA para elegir "Sí".
16. **Rendimiento en el menú: ~12 fps** (replay 27 ms, espera de GPU 43 ms). No es solo la GPU: cada frame el runtime
    espera a la GPU para aplicar 13 write-backs (3,48 MB comparados, **0 % cambiado**, 13,6 ms) porque el frame
    siguiente lee esa memoria ("deferred write-backs ... applied for a later read 60 (3420 ms)": 57 ms por frame).
    Eso serializa CPU y GPU. Es el mismo problema de los write-backs del PC (Falta 2), aquí mucho más caro.
    En juego (después de cargar): ~15 fps, ~10 con los rayos; replay 19–30 ms + espera de GPU 23–30 ms, y ~33 ms por
    frame esperando write-backs (6–16 por frame, 2,4–3 MB, 0–4 % cambiado). Con los rayos sube el replay (más draws)
    y los write-backs.

17. **Mar morado en la tablet: ARREGLADO** (`6074f49`). Solo salía en la cinemática de entrada al mar (con franjas
    negras): la escena HDR ya llegaba magenta al tonemap (media 171/23/169; en PC gris). La LUT de color era idéntica
    byte a byte a la del PC. Causa: la Adreno 610 muestrea los **ASTC 6x6 nativos** (NVN 0x7d y 0x8b, casi todas las
    texturas de NieR) con el color de error de ASTC (magenta) en algunos sitios, aunque los mismos bloques se decodifican
    bien en la CPU (0 inválidos). En PC no pasaba porque NVIDIA no tiene ASTC y siempre se decodifica en la CPU. Ahora
    en Adreno 6xx o anterior esos dos formatos se decodifican al subirlos (`NX_ASTC_DECODE=0`: nativos). Coste en la
    tablet: ~200 texturas, 223 MB, 0,8 s de CPU repartidos, ~1 fps. Comprobado en la tablet sin tocar `config.txt`.
    Descartado antes: `R11G11B10F`, subgrupos (el cambio a 128 se queda, es correcto), imágenes sin formato, los avisos
    `could not untile` y todas las opciones solo de Android (UBWC, clears por transferencia, UBO dinámicos, pase de
    presentación, render pass objects) probadas en PC. Herramientas nuevas: archivo `dump_fs` (vuelca una vez el
    siguiente draw con ese fs y lo que lee), `tab_sea.sh` (carga la partida con el archivo `press` y llega al mar).
    Ojo: las pulsaciones de `adb input` no son fiables a 12 fps; el archivo `press` sí. Al cargar, "Sí" está a la
    IZQUIERDA. El gris que sale luego en el mar es el aviso de vida baja del juego.
    **Pendiente**: las naves y los enemigos salen negros todo el rato (también en PC), y más adelante zonas en negro
    y blanco que el usuario dice que no son del juego.
18. **De dónde sale la espera de los write-backs** (`NX_LOG_WB_READS=<n>` y la línea "reads that applied deferred
    write-backs", nuevos; PC con `NX_PIPELINED=1`, el modo de Android, `runs/wbsite1`): el 100 % es `written bind`.
    Cada frame un compute enlaza **los mismos 1.920 bytes** (`0x5923C3BD00`) que escribió un compute del frame
    anterior. Las regiones escritas viven un solo envío, así que la región nueva se copia de la memoria del juego,
    y para eso hay que aplicar el write-back anterior: la CPU espera a que la GPU acabe **todo** el frame anterior
    (en PC ~18 ms por frame, 26,6 fps; en la tablet 33–57 ms). Arreglo propuesto: copiar en la GPU desde la región
    del envío anterior (guardada en un búfer persistente al final de cada envío, porque su trozo del anillo se
    libera al retirarlo) y rellenar `before` cuando se aplique el write-back anterior; si la CPU escribió esos
    bytes entre medias, volver a esperar como ahora.
19. **"Va lento" = cámara lenta**: NieR ata la velocidad del juego a los fps (la versión de Switch, a 30). Por
    debajo de 30 fps no salta frames: el juego entero va más despacio. A 15 fps en la tablet va a media velocidad.
    Solo se arregla subiendo los fps (o saltando frames sin dibujarlos). La pista de PC de la iluminación global
    (mod FAR: 128 volúmenes de luz) no parece aplicar: en Switch no hay ningún compute con 128 grupos.
20. **Cubemaps de entorno vacíos: ARREGLADO** (`0a73f6c`). Las 6 texturas que salían con `could not untile` en PC y
    en la tablet eran todas cubemaps (reflejo 256x256 ASTC 6x6 con 9 mips, 128x128 y 64x64 ASTC, 64x64 y 32x32 RGBA8):
    `nvnTextureBuilderGetStorageSize` (HLE, `nvn_texture_layout.cpp`) contaba una sola cara porque el `depth` del
    builder de un cubemap (target 8) es 1; el driver de la Switch reserva 6. Ahora cuenta 6. Resultado: 0 texturas
    rechazadas (PC y tablet), el mar refleja el cielo (verde azulado en vez de gris oscuro, `runs/cube1/cmp.png`).
21. **Naves y enemigos negros: SIN RESOLVER** (también en PC; siguen negros con los cubemaps arreglados): negro puro,
    solo se ve el brillo de los motores. En el frame del mar las draws opacas de la escena principal son 34 de
    `fs 66b7c1c862052729` (lee una textura de 4x4 ASTC, dos de 32x32 RGBA8 y una de 8x8; mezcla aditiva: puede no ser
    la nave), 6 de `fs e0308bb136058637` y la del agua (`3be06d89029de5d4`, 15 texturas). Siguiente: identificar el
    draw de las naves con instantáneas. Ojo: `NX_SNAP_FS=<hash>@<draw>` solo escribe en modo síncrono (sin
    `NX_PIPELINED`), y el umbral es el contador global de draws (~2,2 M en el mar a los 270 s de `bench.sh`). Sospechas:
    las sondas de luz de la GI (el búfer de 1.920 bytes de la espera de write-backs encaja con 40 sondas SH L1) o una
    textura del material que no se sube. Tampoco miradas aún las zonas negro/blanco de más adelante.
    Descartado (PC, `runs/shipdump`, `audit1`, `rtup1`): texturas reescritas por la CPU sin avisar (`NX_TEX_AUDIT=2`
    solo ve cambiar una ASTC de 2048²) y la regla de limpiar los destinos comprimibles nunca escritos
    (`NX_RT_UPLOAD=1`: igual de negras). `dump_fs` de `66b7c1c862052729`: lee una 4x4 blanca, una 32x32 gris y una
    8x8 de 4 mips **negra** (`58416af2d0`, memoria `58c001bc00`, RGBA8, sin flag de destino, cargada de los datos al
    principio): puede ser negra de verdad. Siguiente paso: instantáneas en modo síncrono alrededor del draw 2,2 M para
    saber qué draw es la nave, y mirar su shader (¿las sondas SH del búfer de 1.920 bytes?).

22. **Rendimiento: las esperas de write-backs no tienen atajo** (PC, modo Android). Techo sin write-backs
    (`NX_EXP_NO_WB=1`, inseguro): 29,6 fps (el tope de 30) frente a 26,6, y 69 fps sin limitador. Probado no esperar en
    el `written bind` (dejar empezar el compute con los datos de un frame antes): la espera pasa entera a los
    **argumentos de un dispatch indirecto** (12 bytes que calcula la GPU en el frame anterior; ~30 ms por frame) y
    va peor (24,5 fps); retirado. NieR realimenta resultados de la GPU al frame siguiente por los dos caminos. El arreglo
    de verdad: mantener esas regiones en la GPU entre envíos (búfer persistente copiado al acabar cada envío, `before`
    rellenado al aplicar el write-back anterior) y lanzar el dispatch indirecto desde un búfer de GPU
    (`vkCmdDispatchIndirect`) en vez de leer los argumentos en la CPU.
23. **60 fps y cámara lenta: HECHO** (`games/nier/game.cpp`). El bucle principal (`main+591100`) llama en cada frame
    al gestor de tiempo `main+8fc7a8` y al setter del modo vsync del motor `main+9bf1d0` con 1. El modo vive en el
    presentador (`*(main+0x12c1c48)+0x504`) y la presentación (`main+9baa54`) lo aplica al cambiar: 0 = intervalo 1
    (60 fps), 1 = intervalo 2 (30 fps, el de Switch). El gestor de tiempo mide el frame (`+0xbc`, ms) y fija la
    velocidad del juego (`+0xb0`, en frames de 60 fps) = tiempo medio / 16,67 ms **limitada a 1,0..2,0**: a 60 fps da
    1,0 (el motor ya funciona bien a 60) y por debajo de 30 fps se queda en 2,0 (por eso a 15 fps iba a media
    velocidad). Las dos llamadas pasan por la tabla de funciones (nuevo `--hookable` del recompilador) y se enganchan:
    con `NX_FPS_CAP=60` (la opción del lanzador) o `NX_NIER_FPS=60` el modo pasa a 0; cuando la velocidad toca el tope
    de 2,0, se usa el tiempo del último frame hasta `NX_NIER_MAX_STEP` (4,0 por defecto: compensa hasta 15 fps; 2 = el
    del juego). Medido en PC: 60,0 fps con velocidad 1,0 (`runs/st60`); a 15 fps simulados (`NX_PRESENT_INTERVAL=4`)
    la velocidad sube a ~3,75 y el prólogo avanza a la par que a 30 fps (`runs/slow_cmp.png`: con el tope 2,0 va
    claramente por detrás). Ojo: el texto animado del arranque va por frames (a 60 fps se escribe el doble de rápido);
    es interfaz, no la lógica. Pendiente: probarlo en la tablet y vigilar fallos de física con pasos de hasta 4,0.

24. **Esperas de write-backs eliminadas: HECHO** (`odd/tasks/wb-carry.md`). Al final de cada envío diferido las
    regiones escritas por compute se copian en la GPU a un búfer propio (`written_carry`, 32 MB, `NX_WB_CARRY_MB`), y el
    envío siguiente crea su región desde esa copia en la GPU en vez de esperar (`written_adopt`: enlace de compute,
    lectura de compute y argumentos de dispatch indirecto), si el write-back sigue pendiente y la CPU no escribió esos
    bytes (copia "esperada" por cadena; el `before` de la región adoptada lo rellena el write-back anterior,
    `WriteBack::next`). `NX_WB_CARRY=0`: como antes. PC en modo Android: **26,6 → 53,0 fps**, ninguna espera, 0
    rechazos, imágenes normales (`runs/carry_cmp.png`). Pendiente: la tablet.

25. **Ataques de los enemigos (las "bolas"): HECHO; naves negras: SIN RESOLVER.** NieR usaba cinco funciones NVN
    que eran stubs: `DrawElementsIndirect`/`DrawArraysIndirect`, `ClearBuffer`, `CopyBufferToBuffer` y `ReportValue`
    (`nvn_state.cpp`, nuevas `Cmd`). Ahora se graban y se reproducen: `ClearBuffer` con `vkCmdFillBuffer` sobre las
    regiones de compute (o en memoria del juego), `CopyBufferToBuffer` con `vkCmdCopyBuffer` cuando toca regiones
    (4.692 en GPU / 15.308 en memoria por partida), `ReportValue` escribe su u32. Con eso salen los anillos de los
    proyectiles enemigos. Los draws indirectos (~47 por frame) leen su registro de la memoria del juego; si lo escribió
    un dispatch del mismo envío (o del anterior, adoptado por wb-carry) se dibujan con `vkCmdDraw*Indirect` desde la
    copia en la GPU (vértices enteros, índices hasta el mayor `first + count` visto en ese registro + margen;
    `NX_GPU_INDIRECT=0`: como antes). Son ~2.500 por partida, partículas (`{36 32 0 0 0}`), no las naves. PC 50,8 fps.
    **Naves**: siguen oscuras. Descartado en esta sesión: (a) registros indirectos escritos por la GPU (arriba);
    (b) shaders de vértice/fragmento que escriban storage (ninguno, según la reflexión); (c) consultas de oclusión
    "samples passed" (tipo 1) con diferencia de 1 entre informes (`NX_SAMPLES_SHIFT=16` probado: igual; retirado).
    Siguiente: confirmar con `trace_draws` en modo síncrono si el pase de color de la nave existe (antes parecía estar
    solo en los pases `zmap`) y revisar las sondas SH (búfer de 1.920 bytes) y la textura 8x8 negra del punto 21.
    Ojo: aparece una espera nueva `reads that applied deferred write-backs: written ~60 (~770 ms)` cada 60 frames
    (~13 ms/frame en PC) desde que `CopyBufferToBuffer`/`ClearBuffer` tocan regiones (camino compuesto de
    `written_read`, que lee toda la memoria): candidata a optimizar.

26. **Cuello de botella de GPU en la tablet (Adreno 610, 720p): medido.** `NX_GPU_PROFILE=1` en `config.txt`, tres
    corridas (`nier-port/tab_runs/gprof1..3`, build `929f01f`). La ruta automática no pasó del diálogo "¿Cargar este
    archivo?" (a 13 fps la IZQUIERDA no entra ni con 1 s; `tab_sea2.sh`), así que el perfil es del **menú de carga**,
    que solo muestra interfaz y aun así gasta **40,8–41,7 ms de GPU por frame** (13,4 fps; la CPU replay ~11 ms, el
    resto es esperar a la GPU). Coincide con la primera queja ("el menú va a 13 fps y no es pesado"). Por pase y frame:
    | ms | pase | qué parece |
    |---|---|---|
    | 8,6 | 1280x720 `fs f1f497acfa4a9eb3`, 11 draws, ~1-3k vértices | fondo/niebla a pantalla completa con shader caro |
    | 5,3–6,1 | 1280x720 `fs 2ed85e54c3061599`, 65–87 draws, 44 pipelines | la interfaz |
    | 4,7 | compute `a686f64415f6841a` | ¿histograma/luminancia o sondas? |
    | 3,7 / 2,5 / 1,8 / 0,9 | 1280x720 `f5a50e…`, `8bdcc1…`, `86698c…`, `089e6f…` (1–2 draws cada uno) | cadena de posproceso a pantalla completa |
    | 1,0 / 0,8 / 0,5 | 800x450, 400x225, 640x360 | bloom/desenfoque por niveles |
    | 1,9 / 1,5 | copias de texturas (3/frame) / fuera de pases (48 limpiezas/subidas por frame) | |
    Casi todo es trabajo por píxel a 1280x720: la palanca grande es la resolución interna (el lanzador ya ofrece 480p
    = 44 % de los píxeles; comprobar que `NX_RENDER_RES` baja de verdad los destinos del juego y no solo la ventana).
    Siguientes candidatos: identificar `f1f497…` y el compute `a686f6…` (¿se pueden saltar en menús o a calidad baja?),
    y reducir los pases de posproceso a media resolución con `GRAPHICS=low`. En juego (log del usuario
    `tab_runs/user_carry`): ~16-17 fps con ~15 ms de espera en `present` (también limitado por GPU).

### Sesión 4 (2026-11-01)

27. **Menú lento: resolución dinámica de NieR activada (`NX_GPU_TIME_FEED`, por defecto en NieR).** Lo encontrado:
    - `NX_RENDER_RES` (la opción 480p del lanzador) **no baja nada en NieR**: con 854x480 los pases siguen a 1280x720
      (el juego no usa `GetDefaultDisplayResolution` para su 3D). Esa opción solo cambia la ventana.
    - El pase `fs f1f497…` del menú (8,6 ms en la tablet) es una escena 3D tapada por el menú: quads + dos draws de
      2100 partículas instanciadas (`vs 46c7d649…`, datos por instancia de 64 bytes que escribe un compute). Quitarlo
      (`NX_SKIP_FS`, `NX_SKIP_VS` nuevo) no cambia la imagen del menú, pero ese shader también sirve en el juego.
    - **NieR tiene resolución dinámica** y la decide con sus contadores de timestamp (`ReportCounter` tipo 0). El
      runtime los escribía con la hora de la CPU al reproducir, así que el motor medía el replay (~7–11 ms en la
      tablet) y no la GPU (34–41 ms): nunca bajaba. Con los tiempos ×4 (`NX_GPU_TIME_SCALE=4`, diagnóstico) el 3D
      pasa a 1056x594, el mínimo del motor en portátil (con ×20 igual; 0,825 por eje = 68 % de los píxeles).
    - Arreglo: `NX_GPU_TIME_FEED=1` (por defecto en `games/nier/game.cpp`; `=0` en `config.txt` lo apaga). Cada frame
      reproducido lleva timestamps de GPU sin esperas (`nvn_gpu_busy.cpp`, lo de `NX_GPU_PROFILE=3`); GPU/replay
      (media móvil, 0,05–8) es el ritmo al que avanzan los timestamps de los informes dentro del envío, sin volver
      atrás nunca. El log dice `gpu time feed: GPU x ms, replay y ms: report timestamps xk`. Otros juegos: apagado.
    - **Tablet, menú de carga, A/B de 60 s** (`nier-port/tab_menu.sh`): **24,5 → 28,2 fps** (tope 30 en su
      `config.txt`), espera en `present` 12,2 → 7,1 ms, imagen igual. En PC no cambia nada (GPU ≈ replay, factor ~1).
    - Pulsaciones: con el menú a 28 fps la IZQUIERDA de 0,3 s del diálogo "¿Cargar…?" entra a la primera.
      `nier-port/tab_play.sh <nombre> [segundos]` carga la partida y juega pulsando A (vuela y dispara), con
      `EXTRA="K=V"` para líneas de `config.txt` solo en esa corrida.
    - En juego no ayuda: 14–15 fps con y sin feed (`tab_runs/playA`, `playB`). La GPU gasta ~53–70 ms por frame y el
      mínimo del motor solo quita un 32 % de píxeles al 3D. Además, `reads that applied deferred write-backs:
      written 60 (1722 ms)` por 60 frames (~29 ms/frame de espera en el replay) y 23 write-backs/frame (11,6 ms).

28. **Espera "written" eliminada** (`nvn_written.cpp`, `written_read`). Una lectura de un rango en parte escrito por la
    GPU en este envío (los búferes de partículas de NieR) esperaba a que llegaran los write-backs diferidos del frame
    anterior sobre **todo** el rango, aunque las partes que cubre una región se sustituyen después por su copia de GPU.
    Ahora solo esperan los huecos que ninguna región cubre. Tablet en vuelo (`tab_runs/playG` contra `playA`): replay
    27,7 → 16,4 ms, espera de fence 17 → 0,1 ms, la línea "reads that applied…" ya no sale (PC tampoco). Los fps no
    suben (14–15): el vuelo está limitado solo por la GPU. **Perfil de GPU en vuelo** (`tab_runs/playP`, 54 ms/frame):
    | ms | pase |
    |---|---|
    | 14,1 | 1280x720 `fs 1abb730d92165df3` (73 draws, 16,5k vértices): escena principal |
    | 11,7 | 1280x720 `fs f69e0c3893bfa4fa` (65 draws, 325k vértices): solo profundidad (el fs de las sombras) |
    | 8,0 | 1280x720 `fs 2ac0161dd586fd88` (5 draws, 1,3k vértices): pantalla completa (¿mar/niebla?) |
    | 7,0 | mapa de sombras 2048x2048 (92 draws, 193k vértices); "Sombras: desactivadas" del lanzador lo quita |
    | 3,4 | compute `a686f64415f6841a` |

29. **Naves y enemigos negros: ARREGLADO** (`games/nier/game.cpp`, gancho de `main+9d514c`; recompilar `main` con
    `--hookable 9bf1d0,8fc7a8,9d514c`). Cómo se encontró:
    - Instantáneas draw a draw del pase de color en vuelo (`NX_SNAP_FOLLOW=3be06d89029de5d4@f5400`, `runs/sf2`): justo
      después del primer draw (el mar) las naves ya son siluetas negras. El mar falla el test de profundidad donde el
      z-prepass (`vs 33017dd2…`, objetivo R32F `…6d60`) puso las naves y queda el negro del clear. En el pase de color
      **no hay ningún draw de las naves**.
    - Sus draws de color son indirectos (`fs ca95d86f…`, el material con las sondas de luz; y `fs e0308bb1…`): cada
      objeto es `SetRenderEnableConditional` (su consulta de oclusión del z-prepass) + 1–4 `DrawElementsIndirect`
      cuyos registros valen `{0 1 0 0 0}`: **count 0**. Los registros salen de una plantilla estática de 60000 bytes
      (`0x110c50200`) que se copia cada frame con `CopyBufferToBuffer` a `0x110c5ec60`. Comprobado que nada escribe
      jamás esos counts: ni la CPU (watchpoint por hardware en el puntero de host, `NX_WATCH_LIVE` + archivo `watch`;
      ojo, hay que dar la dirección de **host**, no la de GPU), ni ningún comando reproducido (`NX_WATCH_CMDS`,
      nuevo: dispatches, clears, copias, informes, `UpdateUniformBuffer`), ni ningún compute (ninguno se descarta:
      ahora se avisa de dispatches sin shader o indirectos con argumentos a 0). En la Switch algo que no emulamos los
      rellena (no se ha encontrado qué).
    - `main+9d514c` dibuja un rango de una malla: instanciado si tiene ≥ 2 instancias; por la ruta de culling en GPU
      si sus parámetros (`x4`) llevan ese búfer (`+0x10` distinto de 0 y `+0x18[+0x28]` no nulo → `main+9c64a4`,
      registro en `+0x48` de la estructura de culling); si no, un `DrawElements` normal con el count que conoce la CPU
      (`main+9c5d1c`). El gancho pone `+0x10` a 0 durante la llamada: siempre la ruta directa.
      `NX_NIER_GPU_CULLING=1` vuelve a la del juego.
    - Resultado: naves blancas con su sombreado y enemigos grises en PC (`runs/ship1`) y tablet (`tab_runs/playS`).
      Coste: ahora sí se dibujan (material pesado, sin culling: la oclusión condicional está apagada, `NX_OCCL`). PC
      ~18 fps en vuelo (antes ~25 con las naves sin dibujar); tablet 12–16 fps, GPU ~78 ms/frame en la escena del
      láser. Diagnósticos nuevos: `NX_TRACE_INDIRECT=<draw>|f<frame>`, `NX_TRACE_INDIRECT_CALLERS=<n>`.
    - `NX_OCCL=1` (culling por oclusión en GPU del runtime, ya que NieR envuelve cada objeto en consulta + render
      condicional): en PC funciona (21–46 % de objetos ocultos, naves bien), en la tablet no ayuda (10,6 contra 13,1
      fps de media, `tab_runs/playO` / `playS`). Se queda apagado, como en P5R.

30. **Suelo de la resolución dinámica: 0,6 (antes 0,8333)** (`games/nier/game.cpp`, `resolution_floor`). El control
    está en `main+9bf3e0` (una vez por frame, con los ms de GPU que da `NX_GPU_TIME_FEED`): > 45 ms dos veces → −0,07;
    > 32 ms → −0,01; previsión < 30 ms → +0,01; dentro de [mín, 1], con mín = 0,8333 (0,4 con un bit de
    configuración) o el override del juego si es menor (float en `*(main+0x12c7430)`, 1 salvo que el juego lo fije con
    `main+9bf670`). La altura se redondea a múltiplos de 9 (594 → escala 0,825). El viewport es tamaño del destino ×
    escala (`main+9bf6a0`; `NX_TRACE_VIEWPORT=<ancho>`, nuevo, dice desde dónde se fija). El gancho por frame escribe
    `NX_NIER_MIN_SCALE` (0,3–1, por defecto 0,6) en el override: con una GPU que llega a tiempo no cambia nada (PC
    sigue a 1,0). Tablet, misma ruta de juego: **13,1 → 17,3 fps de media** (`tab_runs/playF`), 3D a 768x432 reescalado
    por el juego (más suave; interfaz nítida).

### Sesión 5 (2026-11-02)

31. **Comprobaciones y lanzador.** El usuario confirma que los objetos conservan el color también más adelante (las
    "zonas negro/blanco" eran lo mismo que las naves negras, punto 29). **Películas en Android: funcionan**: el
    `AMediaCodec` usa el VP9 por hardware del Snapdragon (`c2.qti.vp9.decoder`, 1600x904, NV12) a ~30 fps; los logos
    de Square Enix y PlatinumGames se ven bien (`tab_runs/film1`). En PC siguen sin VP9. El lanzador ofrece
    "Resolución dinámica mínima" (`NX_NIER_MIN_SCALE`: 50 %, **60 %** por defecto, 70 % o 83 %, el mínimo del juego).
    `tab_play.sh`/`tab_menu.sh` ya no tocan la tablet si NieR está en primer plano (el usuario puede estar jugando).

32. **Perfil de GPU en vuelo con las naves dibujadas y el suelo 0,6** (`tab_runs/playP2`, 46 ms/frame en modo perfil):
    13,7 ms el pase de color con naves/estructuras (`fs ca95d86f…`, 75 draws, 238k vértices), 8,6 ms el z-prepass
    (238k vértices), 7,3 ms las sombras 2048² (193k vértices), 6,4 ms el resto de la escena; la pantalla completa
    `2ac0161d…` bajó de 8 a 0,4 ms con la resolución dinámica. Lo que queda es sobre todo trabajo por vértice (~670k
    por frame en tres pases): siguiente palanca, mirar el coste de los vertex shaders traducidos (`vs 33017dd2…`,
    `e6d9c4b4…`) en el Adreno 610, o un mapa de sombras más pequeño.

33. **Cuánto cuesta dibujar las naves (punto 29), medido** (tablet, misma ruta, 133 s de juego cada corrida,
    `tab_runs/cullTA..TD`; media ponderada por tiempo desde que acaba el menú):
    | modo | fps | peor ventana |
    |---|---|---|
    | ruta del juego (`NX_NIER_GPU_CULLING=1`: naves sin dibujar, el mínimo posible) | 20,8 | 16 |
    | **arreglo actual** (ruta directa) | **14,9** | 9 |
    | arreglo + `NX_OCCL=1` (descarte por oclusión) | 14,8 | 9 |
    | arreglo con el fs de las naves cambiado por uno trivial (`NX_SWAP_FS=ca95d86f…:f69e0c…`) | 17,2 | 13 |
    PC (limitado por CPU): 22,9 → 21,3 fps. La ruta directa **no dibuja de más**: cada objeto son 4 partes de
    material contiguas (2019/2097/2313/717 índices de 32 bits), no niveles de detalle, y el pase de color dibuja menos
    índices que el z-prepass que elige la CPU (374k frente a 473k en los mismos frames; `NX_TRACE_DRAWS` ahora
    escribe la dirección de índices). Es el coste de sombrear las naves, que la Switch también paga: ~19 ms/frame en
    la tablet, la mitad el fs `ca95d86f…` (6.326 líneas de SPIR-V, sondas de luz por SSBO) y la otra mitad vértices y
    los draws `e0308bb1…`. Si se quiere recuperar fps: optimizar ese fs (precisión media/fp16 en Adreno, lecturas del
    SSBO, una variante barata con `GRAPHICS=low`) y su vs `e6d9c4b4…`.

34. **Opciones para recuperar fps, probadas en la tablet** (misma ruta; base 14,9 fps, `tab_runs/cullTA`):
    | opción | fps | resultado |
    |---|---|---|
    | `spirv-opt -O` al fs de las naves (SSA, código muerto; 1980 → 144 lecturas de variables) | 14,9 | sin efecto: el compilador del Adreno ya lo hacía |
    | `--relax-float-ops` (RelaxedPrecision, fp16 en Adreno) a ese fs | — | **cuelga la GPU** (`GPU hang`): descartado |
    | suelo de resolución 0,5 (`NX_NIER_MIN_SCALE`, opción del lanzador) | **16,4** | +10 %, imagen más suave |
    | "Sombras: desactivadas" (`NX_SHADOWS=0`) antes de este arreglo | 15,1 | no hacía nada en NieR |
    El SSBO del fs solo se lee 5 veces (pasarlo a UBO no daría casi nada) y una variante barata hecha a mano no
    compensa. **Sombras desactivadas arregladas** (`nvn_draw.cpp`, `shadow_draw_skipped`): el mapa 2048² de NieR
    escribe también un destino de color de su tamaño (formato 0xfa), y la regla solo saltaba pases sin color. Ahora
    también salta los de un único color 2D del mismo tamaño que la profundidad cuadrada (los cubemaps de entorno no).
    PC: el pase deja de aparecer en el perfil (antes 100 draws y 224k vértices por frame) y la imagen sale bien
    (`runs/shdpc`). En la tablet ese pase costaba ~7,3 ms (punto 32); **falta medirlo allí**: la pantalla se había
    apagado y bloqueado (`mWakefulness=Dozing`; NieR no arranca sin ventana y las capturas salen negras).

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
| 720p + recorte de vértices arreglado (ahora por defecto), 1.ª medida | 19,0 (vs 16,8) | subidas 69 → 10 MB/frame; imagen igual a la de referencia (`runs/cmp_trim.png`). **Ojo**: esa ejecución llegó a otra escena |
| **720p, A/B controlado sin partida guardada** (`c_t1` / `c_t0`) | **16,9 / 16,5** | último tramo: 63 vs 123 MB subidos por frame. En PC la ganancia es pequeña (+2–3 %); en el móvil copiar a memoria no cacheada cuesta mucho más |
| `NX_EXP_NO_WB=1` | 15,9 | no quita estos write-backs: descartado |

**Perfil de GPU por pases** (`NX_GPU_PROFILE=1`, `runs/gprof`, vuelo a 720p, ~30 ms de GPU por frame): 11,6 ms fuera
de los pases (limpiezas, subidas, copias; ~51 por frame), **8,8 ms el mapa de sombras 2048×2048** (`fs f69e0c3893bfa4fa`,
120 draws y 243k vértices por frame), 2,2 ms el pase principal, 1,5 ms un compute (`d9db47fa2f852c79`). Que una RTX 4060
tarde 8,8 ms en un mapa de sombras apunta a los vértices leídos desde memoria del host por PCIe (el anillo de subidas):
en PC la GPU sale peor de lo que será en un móvil (memoria compartida). Siguiente: medir lo mismo en la tablet, y en PC
probar a copiar los vértices estables a memoria de la GPU.

> Para medir, borra antes `private/nier/user/save`: con una partida guardada la ruta de teclas carga la partida y
> empieza en otro punto (las cifras no son comparables). La partida de las pruebas largas está en `user/save_bak_long1`.

**Estabilidad** (`runs/long1`, 10 min a 720p con el recorte, A cada 3 s y R a ratos): carga la partida guardada, vuela,
dispara sobre el mar, llega a un fin de partida ("Los YoRHa fueron exterminados…", final W, por pulsar al azar), vuelve
al título y la partida guardada muestra "Final: W" y 3:20 de juego. Sin errores; 23 fps al final. Las partidas se
guardan en `private/nier/user/save` (bórralas para volver a probar Nueva partida desde cero).

**Causa de las subidas** (`NX_UPLOAD_STATS=1` ahora lista las direcciones que más bytes de vértices copian): el búfer de
vértices de efectos se enlaza desde la misma base con un tamaño que crece draw a draw, y cada draw lo copiaba entero
otra vez (~20 veces por frame: 62 % de los bytes). El recorte (`NX_VERTEX_TRIM`) no funcionaba con draws indexados
porque `draw_inputs_shared` pide los flujos con `indexed=false`: ahora el tipo sale del comando. `NX_VERTEX_TRIM=0` lo
apaga. Los write-backs pasan a 17/frame con 7 % de palabras cambiadas (antes 6 y 0 %): vigilar.

`NX_LOG_BIG_VERTEX=<bytes>` (nuevo, `runs/bigv`): los flujos grandes son vértices de efectos que la CPU reescribe cada
frame (mismo búfer `0x1145254a0`, 1,2–3,3 MB, stride 28, tamaño creciente) en draws cuyo número de vértices el runtime
no conoce (rango "unknown": probablemente indirectos), así que la copia es necesaria. Lo siguiente: sumar bytes por
dirección para ver qué más llega a ~65 MB/frame.

`NX_UPLOAD_STATS=1` (`runs/diag1`): vértices 96.297 copias y 28,8 GB en la ventana, 1 % iguales; índices 326 MB (100 %
iguales); uniform/storage 502 MB (25 %). En el título: 13,7 fps.

## Falta ⚠️

1. ~~Probar el APK en un dispositivo~~: probado en la tablet Lenovo TB331FC (Adreno 610) desde la sesión 2.
2. Rendimiento en juego (tablet, vuelo): ~17 fps con el suelo 0,6; la GPU es el límite y ahora es sobre todo
   trabajo por vértice (punto 32). Ideas: coste de los vertex shaders traducidos en el Adreno, sombras a 1024². El
   menú de carga ya va a 28 fps (tope 30).
3. ~~Zonas en negro y blanco~~: eran las naves/estructuras negras (punto 29; confirmado por el usuario, punto 31).
4. Películas: en Android funcionan (punto 31); en PC salen verdes/negras (sin decodificador VP9).
5. Preparación de datos en el teléfono sin PC (como P5R v0.4): extractor de shaders DAT en C++.
6. ~~Opción del lanzador para `NX_NIER_MIN_SCALE`~~ (punto 31). Hay cambios sin confirmar de otra sesión en
   `Launcher.java` (compartir registros como archivo), `AndroidManifest.xml` y `LogProvider.java`: no incluidos en los
   commits, pero sí en los APK compilados desde este árbol.
7. Otros ports y la guía de P5R: **P4G** ya la tenía (v0.2.0, `7c3fcdc`, otra sesión). **P4AU**: arreglos de runtime
   traídos en la sesión 1 (`c355cbb`, ver su PROGRESO; faltan mods/`hle_fs.cpp` y las opciones del lanzador).
   **Persona Q/Q2** (3DS, runtime zakuro) los actualiza otra sesión: no tocados.
8. ~~`p5r-main` y el `switch` perdido~~: arreglado allí por otra sesión (`platform_android_input.cpp` igual que en
   `nier`; `p5r-main` va por `5e05e70`). Su v0.5.2 trae "compartir el log como archivo" (`LogProvider`), que es lo
   que está sin confirmar en el lanzador de NieR.

### 2026-11-02: Arreglo del aspecto de pantalla panorámica (16:9 vs 20:9)

- `runtime/src/platform_android.cpp`: `window_size` actualizado para extraer la altura objetivo y escalar el ancho proporcionalmente al aspect ratio del dispositivo (`sw * target_h / sh`), eliminando la lectura rígida de resoluciones 16:9 (`WxH`); añadido `unsetenv("NX_STRETCH")` antes de leer `config.txt`.
- `games/nier/java/io/github/nierrecomp/Launcher.java`: `save()` actualizado para guardar presets limpios (`"360p"`, `"480p"`, `"720p"`, `"1080p"`) en lugar de resoluciones fijas 16:9 (`1280x720`, etc.).

### 2026-11-03: Integración de soluciones y PRs de Persona 5 Royal

1. **Controladores de GPU personalizados (PR #133 / adrenotools / Turnip)**:
   - Integrado 	hird_party/libadrenotools en CMakeLists.txt (NX_CUSTOM_DRIVER=1, enlace estático con dependencias de hooks).
   - ndroid/build_apk.ps1: empaquetado de librerías hook (libhook_impl.so, libmain_hook.so, libfile_redirect_hook.so, libgsl_alloc_hook.so) en el APK para arm64-v8a.
   - 
untime/src/platform_android.cpp: open_custom_vulkan() carga el driver Turnip configurado en <files>/gpu_driver/active.txt a través de libadrenotools.
   - games/nier/java/io/github/nierrecomp/Launcher.java: selector e importador de drivers en formato .zip / .so en la interfaz de usuario.
2. **Arreglo de interbloqueo en sistema de archivos (Commit 5e05e70)**:
   - 
untime/src/hle_fs.cpp: sustitución de std::mutex g_fs; por std::recursive_mutex g_fs; para evitar bloqueos por reentrancia de hilos al acceder a archivos.
3. **Exportación segura de registros (Commit 98004d7)**:
   - Integrado LogProvider.java (content://io.github.nierrecomp.logprovider) y registrado en AndroidManifest.xml para compartir Download/nier-log.txt sin FileUriExposedException ni bloqueos de scoped storage en Android 7+.

### 2026-11-11: Arreglo de esquiva/dash continuo fantasma (v0.2.5 - versionCode 7)

1. **Servicio `hid` e implementación de `IsSixAxisSensorAtRest` (cmd 82)**:
   - `runtime/src/svc_misc.cpp`: Registrado el servicio `hid` (y alias `hid:sys`, `hid:dbg`, `hid:tmp`) e implementado el comando IPC 82 (`IsSixAxisSensorAtRest`), devolviendo `isAtRest = 1` (true). Evita que el juego interprete que el mando/consola está en constante agitación y active continuamente la esquiva por movimiento (Issues #13, #15, #18 y reporte en Discord).
2. **Desacople de SixAxis del stick derecho**:
   - `runtime/src/hle_hid.cpp`: La inyección de velocidad angular en el sensor SixAxis simulada a partir del stick analógico derecho se restringe únicamente a P5R (`game::kInfo.id == "p5r"`, minijuego de Dardos). En NieR, el stick derecho controla la cámara, por lo que rotar la cámara ya no dispara falsas esquivas por movimiento.
3. **Versión v0.2.5**:
   - `games/nier/AndroidManifest.xml`: Versión subida a `0.2.5` (`versionCode="7"`). Compilado APK NCE Release.
