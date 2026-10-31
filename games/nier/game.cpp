// NieR:Automata The End of YoRHa Edition (010056B015FE8000, USA, update 1.0.3): what the shared runtime needs to know
// about the game, and what only NieR needs. PlatinumGames' engine, Switch version by Virtuos, SDK 12.3.9. Sound is
// Wwise 2021.1 over nn::audio AudioOut (romfs sound/), films are CRI USM through nn::movie (subsdk0, romfs movie/).
// The NVN shaders are DAT archives in romfs data000.cpk (games/nier/tools/nier_shaders.py). Notes: games/nier/PROGRESO.md.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include <nx/game.h>
#include <nx/runtime.h>

#include "platform.h"

namespace nx {

const game::Info game::kInfo = {"nier", "NieR Automata", 0x010056B015FE8000ull, "io.github.nierrecomp",
                                "private/nier", 0x1000000 /* main.npdm META +0x14 */, "3C10EC35C5EF4C36" /* 1.0.3 */};

namespace {

// Copy every fputs line into the log (assertion messages), then do the real one.
void fputs_logged(CPU& c) {
    static const uint64_t real = find_symbol("fputs");
    const char* s = reinterpret_cast<const char*>(c.x[0]);
    if (s) {
        std::string line(s, strnlen(s, 1024));
        while (!line.empty() && (line.back() == '\n' || line.back() == '\r')) line.pop_back();
        log("[guest fputs] %s", line.c_str());
    }
    if (real) call(c, real);
    else c.x[0] = 0;
}

}  // namespace

NX_HLE("fputs", fputs_logged);

void game::after_init() {}
void game::renderer_start() {}
void game::first_frame() {}

}  // namespace nx
