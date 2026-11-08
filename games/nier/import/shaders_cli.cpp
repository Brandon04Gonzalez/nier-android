// PC test of nier_shaders.h: nier_shaders <data000.cpk> <out dir>
#include <chrono>
#include <cstdio>

#include "nier_shaders.h"

int main(int argc, char** argv) {
    if (argc != 3) return std::fprintf(stderr, "usage: nier_shaders <data000.cpk> <out dir>\n"), 2;
    std::string err;
    auto t0 = std::chrono::steady_clock::now();
    long n = nier::extract_shaders(argv[1], argv[2], [](int d, int t) { std::fprintf(stderr, "  %d/%d\n", d, t); }, err);
    double sec = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
    if (n < 0) return std::fprintf(stderr, "extract: %s\n", err.c_str()), 1;
    std::printf("%ld distinct code blocks in %.1f s\n", n, sec);
    return 0;
}
