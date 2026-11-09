// The import on the phone (libnierimport.so, loaded by ImportActivity in its own process ":import"): the player's base
// game (XCI or NSP) and update 1.0.3 (NSP) with their keys -> the data folder the NCE build reads, then the shaders:
//   1. nxi::run_import: <data>/extract/romfs (the RomFS with the update applied) and <data>/exefs/{main,sdk}, checked
//      to be update 1.0.3 (main's build ID)
//   2. the shaders out of extract/romfs/data000.cpk (nier_shaders.cpp)
//   3. translated to SPIR-V (libspirvharness.so: NieR's shader translator built with NativeAOT) into <data>/spirv
//   Importer.run(baseFd, baseName, updateFd, updateName, keysPath, titleKeysPath, dataDir, workDir, jobs) -> 0, or an
//   error (Importer.error())
//   Importer.progress() -> {phase, done, total}: 1 importing (MiB), 2 extracting, 3 translating, 4 finishing, 5 done
#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/stat.h>
#include <unistd.h>

#include <atomic>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "nier_shaders.h"
#include "nximport.h"

namespace {

// NieR:Automata The End of YoRHa Edition (US) and the build ID of update 1.0.3's main (games/nier/game.cpp).
constexpr uint64_t kTitle = 0x010056B015FE8000ull;
constexpr const char* kBuild = "3C10EC35C5EF4C36";

std::atomic<int> g_phase{0}, g_done{0}, g_total{0};
std::atomic<bool> g_cancel{false};
std::mutex g_error_lock;
std::string g_error;

using HarnessMain = int (*)(int, const char**);
using HarnessProgress = void (*)(int*, int*);
HarnessProgress g_harness_progress = nullptr;

void set_error(const std::string& e) {
    std::lock_guard lk(g_error_lock);
    g_error = e;
}

std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

void remove_tree(const std::string& path) {
    if (DIR* d = opendir(path.c_str())) {
        while (dirent* e = readdir(d)) {
            std::string n = e->d_name;
            if (n == "." || n == "..") continue;
            std::string p = path + "/" + n;
            struct stat st{};
            if (lstat(p.c_str(), &st) == 0 && S_ISDIR(st.st_mode)) remove_tree(p);
            else unlink(p.c_str());
        }
        closedir(d);
    }
    rmdir(path.c_str());
}

int fail(const std::string& e) {
    set_error(e);
    g_phase = 0;
    return 1;
}

// main's build ID (NSO header +0x40, the first 8 bytes in hex), empty when it cannot be read.
std::string build_id(const std::string& main) {
    unsigned char id[8];
    int fd = open(main.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) return {};
    bool ok = pread(fd, id, 8, 0x40) == 8;
    close(fd);
    if (!ok) return {};
    char hex[17];
    for (int i = 0; i < 8; i++) std::snprintf(hex + 2 * i, 3, "%02X", id[i]);
    return hex;
}

int run(int base_fd, const std::string& base_name, int update_fd, const std::string& update_name, const std::string& keys,
        const std::string& title_keys, const std::string& data_dir, const std::string& work_dir, int jobs) {
    g_phase = 1, g_done = 0, g_total = 0, g_cancel = false;
    set_error("");
    // 1. the game with its update, written where the NCE build reads it
    std::vector<nxi::InputFile> in;
    in.push_back({base_name, base_fd});
    in.push_back({update_name, update_fd});
    int kfd = open(keys.c_str(), O_RDONLY | O_CLOEXEC);
    if (kfd < 0) return fail("cannot read prod.keys");
    in.push_back({"prod.keys", kfd});
    int tfd = title_keys.empty() ? -1 : open(title_keys.c_str(), O_RDONLY | O_CLOEXEC);
    if (tfd >= 0) in.push_back({"title.keys", tfd});
    nxi::Options opt;
    opt.out_dir = data_dir;
    opt.title_id = 0;  // accept any regional base/update pair (010056B015FE8000 US/EU/JP, 0100B8E016F76000 Asia)
    opt.keep_exefs = true;
    opt.cancel = &g_cancel;
    opt.on_progress = [](const nxi::Progress& p) {
        g_done = int(p.done >> 20);
        g_total = int(p.total >> 20);
    };
    std::string err;
    int rc = nxi::run_import(in, opt, err);
    close(kfd);
    if (tfd >= 0) close(tfd);
    if (rc != 0) return fail(err);
    std::string id = build_id(data_dir + "/exefs/main");
    if (id.empty()) return fail("the import wrote no exefs/main");
    // 2. the shaders out of the game
    g_phase = 2, g_done = 0, g_total = 0;
    remove_tree(work_dir);
    mkdir(work_dir.c_str(), 0700);
    const std::string extracted = work_dir + "/shaders";
    long blocks = nier::extract_shaders(data_dir + "/extract/romfs/data000.cpk", extracted,
                                        [](int d, int t) { g_done = d, g_total = t; }, err);
    if (blocks < 0) return fail(err);
    // 3. translated (the harness counts its own progress)
    g_phase = 3, g_done = 0, g_total = int(blocks);
    void* lib = dlopen("libspirvharness.so", RTLD_NOW);
    if (!lib) return fail(std::string("the shader translator is missing: ") + dlerror());
    auto harness = reinterpret_cast<HarnessMain>(dlsym(lib, "spirv_harness_main"));
    g_harness_progress = reinterpret_cast<HarnessProgress>(dlsym(lib, "spirv_harness_progress"));
    if (!harness || !g_harness_progress) return fail("the shader translator is incomplete");
    const std::string out_new = data_dir + "/spirv.new";
    remove_tree(out_new);
    std::string jobs_s = std::to_string(jobs > 0 ? jobs : 4);
    const char* args[] = {"--batch", extracted.c_str(), "--out", out_new.c_str(), "--jobs", jobs_s.c_str(), "--no-val"};
    rc = harness(int(sizeof args / sizeof args[0]), args);
    if (rc != 0) return fail("the shader translation failed (code " + std::to_string(rc) + ")");
    // 4. in place of the old set; a note of what it was made from
    g_phase = 4;
    const std::string out = data_dir + "/spirv", old = data_dir + "/spirv.old";
    remove_tree(old);
    rename(out.c_str(), old.c_str());
    if (rename(out_new.c_str(), out.c_str()) != 0) return fail("cannot write " + out);
    remove_tree(old);
    remove_tree(work_dir);
    if (FILE* f = std::fopen((out + "/import.txt").c_str(), "w")) {
        std::fprintf(f, "%s\n%s\n%s\n%ld shaders\n", base_name.c_str(), update_name.c_str(), id.c_str(), blocks);
        std::fclose(f);
    }
    g_phase = 5;
    return 0;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL Java_io_github_nierrecomp_Importer_run(JNIEnv* env, jclass, jint base_fd, jstring base_name,
                                                              jint update_fd, jstring update_name, jstring keys,
                                                              jstring title_keys, jstring data_dir, jstring work_dir,
                                                              jint jobs) {
    return run(base_fd, jstr(env, base_name), update_fd, jstr(env, update_name), jstr(env, keys), jstr(env, title_keys),
               jstr(env, data_dir), jstr(env, work_dir), jobs);
}

JNIEXPORT jintArray JNICALL Java_io_github_nierrecomp_Importer_progress(JNIEnv* env, jclass) {
    int v[3] = {g_phase.load(), g_done.load(), g_total.load()};
    if (v[0] == 3 && g_harness_progress) g_harness_progress(&v[1], &v[2]);
    jintArray a = env->NewIntArray(3);
    env->SetIntArrayRegion(a, 0, 3, v);
    return a;
}

JNIEXPORT jstring JNICALL Java_io_github_nierrecomp_Importer_error(JNIEnv* env, jclass) {
    std::lock_guard lk(g_error_lock);
    return env->NewStringUTF(g_error.c_str());
}

JNIEXPORT void JNICALL Java_io_github_nierrecomp_Importer_cancel(JNIEnv*, jclass) { g_cancel = true; }

}  // extern "C"
