// nier_shaders.h. The CRI formats (@UTF tables, CPK, CRILAYLA) and SHA-1 as games/p5r/import/p5r_shaders.cpp reads
// them; PlatinumGames' DAT archives as games/nier/tools/dat.py; the stages as games/nier/tools/nier_shaders.py and
// tools/maxwell/containers.py (Stage, check_stage).
#include "nier_shaders.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <map>
#include <set>
#include <stdexcept>
#include <vector>

#if defined(_WIN32)
#include <direct.h>
#include <io.h>
#else
#include <unistd.h>
#endif

namespace nier {

namespace {

struct Fail : std::runtime_error {
    using std::runtime_error::runtime_error;
};

uint32_t le32(const uint8_t* p) { return uint32_t(p[0]) | uint32_t(p[1]) << 8 | uint32_t(p[2]) << 16 | uint32_t(p[3]) << 24; }
uint64_t le64(const uint8_t* p) { return uint64_t(le32(p)) | uint64_t(le32(p + 4)) << 32; }
uint16_t be16(const uint8_t* p) { return uint16_t(p[0] << 8 | p[1]); }
uint32_t be32(const uint8_t* p) { return uint32_t(p[0]) << 24 | uint32_t(p[1]) << 16 | uint32_t(p[2]) << 8 | p[3]; }
uint64_t be64(const uint8_t* p) { return uint64_t(be32(p)) << 32 | be32(p + 4); }

// ---- SHA-1 (the harness names blocks by the first 16 hex digits of their SHA-1) ----

std::string sha1_16(const uint8_t* data, size_t n) {
    uint32_t h[5] = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
    auto rol = [](uint32_t x, int s) { return (x << s) | (x >> (32 - s)); };
    auto block = [&](const uint8_t* p) {
        uint32_t w[80];
        for (int i = 0; i < 16; i++) w[i] = be32(p + 4 * i);
        for (int i = 16; i < 80; i++) w[i] = rol(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
        uint32_t a = h[0], b = h[1], c = h[2], d = h[3], e = h[4];
        for (int i = 0; i < 80; i++) {
            uint32_t f, k;
            if (i < 20) f = (b & c) | (~b & d), k = 0x5A827999;
            else if (i < 40) f = b ^ c ^ d, k = 0x6ED9EBA1;
            else if (i < 60) f = (b & c) | (b & d) | (c & d), k = 0x8F1BBCDC;
            else f = b ^ c ^ d, k = 0xCA62C1D6;
            uint32_t t = rol(a, 5) + f + e + k + w[i];
            e = d, d = c, c = rol(b, 30), b = a, a = t;
        }
        h[0] += a, h[1] += b, h[2] += c, h[3] += d, h[4] += e;
    };
    size_t i = 0;
    for (; i + 64 <= n; i += 64) block(data + i);
    uint8_t tail[128] = {};
    size_t rest = n - i;
    std::memcpy(tail, data + i, rest);
    tail[rest] = 0x80;
    size_t len = rest + 1 + 8 <= 64 ? 64 : 128;
    uint64_t bits = uint64_t(n) * 8;
    for (int j = 0; j < 8; j++) tail[len - 1 - j] = uint8_t(bits >> (8 * j));
    block(tail);
    if (len == 128) block(tail + 64);
    char out[41];
    for (int j = 0; j < 5; j++) std::snprintf(out + 8 * j, 9, "%08x", h[j]);
    return std::string(out, 16);
}

// ---- CRI: @UTF tables, CPK, CRILAYLA ----

struct Value {
    int type = -1;
    uint64_t u = 0;
    std::string s;
};
using Row = std::map<std::string, Value>;

std::vector<Row> read_utf(std::vector<uint8_t> d) {
    if (d.size() < 0x20) throw Fail("CPK: table too short");
    if (std::memcmp(d.data(), "@UTF", 4) != 0) {  // obfuscated
        uint32_t m = 0x655F;
        for (uint8_t& b : d) b ^= uint8_t(m), m = m * 0x4115;
        if (std::memcmp(d.data(), "@UTF", 4) != 0) throw Fail("CPK: not an @UTF table");
    }
    const uint8_t* p = d.data();
    const size_t base = 8;
    size_t rows_off = base + be16(p + 10), strings_off = base + be32(p + 12);
    uint16_t ncols = be16(p + 24), row_len = be16(p + 26);
    uint32_t nrows = be32(p + 28);
    auto at = [&](size_t o, size_t n) {
        if (o + n > d.size()) throw Fail("CPK: table data out of range");
        return p + o;
    };
    auto cstr = [&](uint32_t o) {
        size_t s = strings_off + o;
        at(s, 1);
        size_t e = s;
        while (e < d.size() && p[e]) e++;
        return std::string(reinterpret_cast<const char*>(p + s), e - s);
    };
    auto value = [&](int t, size_t pos, Value& v) -> size_t {
        v.type = t;
        switch (t) {
            case 0: case 1: v.u = *at(pos, 1); return 1;
            case 2: case 3: v.u = be16(at(pos, 2)); return 2;
            case 4: case 5: case 8: v.u = be32(at(pos, 4)); return 4;
            case 6: case 7: case 9: v.u = be64(at(pos, 8)); return 8;
            case 0xA: v.s = cstr(be32(at(pos, 4))); return 4;
            case 0xB: {  // data: offset, size (not needed here)
                at(pos, 8);
                v.u = be32(p + pos + 4);
                return 8;
            }
        }
        throw Fail("CPK: unknown @UTF column type");
    };
    struct Col {
        std::string name;
        int storage, type;
        Value constant;
    };
    std::vector<Col> cols;
    size_t pos = 0x20;
    for (int i = 0; i < ncols; i++) {
        uint8_t flags = *at(pos, 1);
        Col c{cstr(be32(at(pos + 1, 4))), flags & 0xF0, flags & 0x0F, {}};
        pos += 5;
        if (c.storage == 0x30) pos += value(c.type, pos, c.constant);
        cols.push_back(std::move(c));
    }
    std::vector<Row> rows(nrows);
    for (uint32_t r = 0; r < nrows; r++) {
        size_t q = rows_off + size_t(r) * row_len;
        for (const Col& c : cols) {
            if (c.storage == 0x50) q += value(c.type, q, rows[r][c.name]);
            else if (c.storage == 0x30) rows[r][c.name] = c.constant;
        }
    }
    return rows;
}

std::vector<uint8_t> crilayla(const std::vector<uint8_t>& src) {
    if (src.size() < 0x110 || std::memcmp(src.data(), "CRILAYLA", 8) != 0) return src;
    uint32_t usize = le32(src.data() + 8), hoff = le32(src.data() + 12);
    if (0x10 + uint64_t(hoff) + 0x100 > src.size() || usize > (1u << 30)) throw Fail("CRILAYLA: bad header");
    std::vector<uint8_t> out(size_t(usize) + 0x100);
    std::memcpy(out.data(), src.data() + 0x10 + hoff, 0x100);
    int64_t pos = int64_t(src.size()) - 0x100 - 1;
    uint32_t bits = 0;
    int nbits = 0;
    auto get = [&](int n) {
        uint32_t v = 0;
        while (n) {
            if (nbits == 0) {
                if (pos < 0) throw Fail("CRILAYLA: stream ends early");
                bits = src[size_t(pos--)];
                nbits = 8;
            }
            int take = std::min(n, nbits);
            v = (v << take) | ((bits >> (nbits - take)) & ((1u << take) - 1));
            nbits -= take, n -= take;
        }
        return v;
    };
    int64_t w = int64_t(usize) + 0x100 - 1;
    while (w >= 0x100) {
        if (get(1)) {
            int64_t back = int64_t(get(13)) + 3;
            uint32_t length = 3;
            static const int lbits[4] = {2, 3, 5, 8};
            bool more = true;
            for (int lb : lbits) {
                uint32_t v = get(lb);
                length += v;
                if (v != (1u << lb) - 1) {
                    more = false;
                    break;
                }
            }
            while (more) {
                uint32_t v = get(8);
                length += v;
                more = v == 0xFF;
            }
            for (uint32_t k = 0; k < length; k++, w--) {
                if (w < 0 || w + back >= int64_t(out.size())) throw Fail("CRILAYLA: bad back reference");
                out[size_t(w)] = out[size_t(w + back)];
            }
        } else {
            out[size_t(w--)] = uint8_t(get(8));
        }
    }
    return out;
}

// The CPK file on disk (the imported RomFS's data000.cpk), read where it is.
struct File {
    int fd = -1;
    explicit File(const std::string& path) {
#if defined(_WIN32)
        fd = _open(path.c_str(), _O_RDONLY | _O_BINARY);
#else
        fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
#endif
        if (fd < 0) throw Fail("cannot open " + path + " (" + std::strerror(errno) + ")");
    }
    ~File() {
#if defined(_WIN32)
        _close(fd);
#else
        close(fd);
#endif
    }
    void read(uint64_t off, uint8_t* p, size_t n) const {
#if defined(_WIN32)
        if (_lseeki64(fd, int64_t(off), SEEK_SET) < 0) throw Fail("CPK: cannot seek");
        while (n) {
            int got = _read(fd, p, unsigned(std::min<size_t>(n, 1u << 30)));
            if (got <= 0) throw Fail("CPK: truncated");
            p += got, n -= size_t(got);
        }
#else
        while (n) {
            ssize_t got = pread(fd, p, n, off_t(off));
            if (got <= 0) throw Fail("CPK: truncated");
            p += got, n -= size_t(got), off += uint64_t(got);
        }
#endif
    }
};

struct CpkFile {
    std::string path;
    uint64_t offset, size, extract;
};

std::vector<CpkFile> cpk_files(const File& f) {
    auto block = [&](uint64_t off, const char* magic) {
        uint8_t head[16];
        f.read(off, head, 16);
        if (std::memcmp(head, magic, 4) != 0) throw Fail(std::string("CPK: no ") + magic + " block");
        std::vector<uint8_t> d(size_t(le64(head + 8)));
        f.read(off + 16, d.data(), d.size());
        return d;
    };
    std::vector<Row> header = read_utf(block(0, "CPK "));
    if (header.empty()) throw Fail("CPK: empty header");
    uint64_t toc = header[0]["TocOffset"].u, content = header[0]["ContentOffset"].u;
    if (!toc) throw Fail("CPK: no table of contents");
    uint64_t base = content ? std::min(toc, content) : toc;
    std::vector<CpkFile> out;
    for (Row& r : read_utf(block(toc, "TOC "))) {
        std::string dir = r["DirName"].s, name = r["FileName"].s;
        uint64_t size = r["FileSize"].u, ext = r["ExtractSize"].u;
        out.push_back({dir.empty() ? name : dir + "/" + name, base + r["FileOffset"].u, size, ext ? ext : size});
    }
    return out;
}

// ---- DAT archives (dat.py read_dat) ----

struct Member {
    std::string name;
    const uint8_t* data;
    size_t size;
};

// The members of a DAT archive, none when it is not one (dat.py stops, nier_shaders.py skips the archive).
std::vector<Member> dat_members(const std::vector<uint8_t>& d) {
    std::vector<Member> out;
    if (d.size() < 24 || std::memcmp(d.data(), "DAT\0", 4) != 0) return out;
    uint32_t n = le32(&d[4]), o_off = le32(&d[8]), o_names = le32(&d[16]), o_sizes = le32(&d[20]);
    if (uint64_t(o_off) + 4ull * n > d.size() || uint64_t(o_sizes) + 4ull * n > d.size() || uint64_t(o_names) + 4 > d.size())
        return out;
    uint32_t nlen = le32(&d[o_names]);
    if (uint64_t(o_names) + 4 + uint64_t(nlen) * n > d.size()) return out;
    for (uint32_t i = 0; i < n; i++) {
        uint32_t off = le32(&d[o_off + 4 * i]), size = le32(&d[o_sizes + 4 * i]);
        const char* raw = reinterpret_cast<const char*>(&d[o_names + 4 + size_t(i) * nlen]);
        std::string name(raw, strnlen(raw, nlen));
        size_t start = std::min<size_t>(off, d.size());
        out.push_back({name, d.data() + start, std::min<size_t>(size, d.size() - start)});
    }
    return out;
}

// ---- the stages (nier_shaders.py stage_of, containers.py Stage and check_stage) ----

constexpr uint32_t kCtrlMagic = 0x98761234, kGraphics = 0x12345678, kCompute = 0x12345679;
constexpr size_t kCodeOff = 0x8C;

struct Stage {
    std::string kind;
    size_t code_size, ctrl_off, ctrl_size;
    uint32_t start, length, const_off, const_size;
};

uint32_t u32_at(const uint8_t* d, size_t n, size_t o) { return o + 4 <= n ? le32(d + o) : 0; }

bool zero(const uint8_t* d, size_t n, size_t a, size_t b) {
    b = std::min(b, n);
    for (size_t i = a; i < b; i++)
        if (d[i]) return false;
    return true;
}

const char* kind_of(const std::string& name) {
    size_t dot = name.rfind('.');
    if (dot == std::string::npos) return nullptr;
    std::string ext = name.substr(dot);
    for (char& c : ext) c = char(std::tolower(static_cast<unsigned char>(c)));
    if (ext == ".vso") return "vertex";
    if (ext == ".pso") return "pixel";
    if (ext == ".gso") return "geometry";
    if (ext == ".cso") return "compute";
    return nullptr;
}

// A member's stage, false when it is none (the wrong kind, header or no control section) or does not look right.
bool stage_of(const Member& m, Stage& s) {
    const char* kind = kind_of(m.name);
    const uint8_t* d = m.data;
    size_t n = m.size;
    if (!kind || n < kCodeOff || le32(d) != 0x88) return false;
    s.kind = kind;
    s.code_size = le32(d + 0x88);
    const uint8_t magic[4] = {0x34, 0x12, 0x76, 0x98};
    size_t from = kCodeOff + s.code_size;
    if (from > n) return false;
    const uint8_t* hit = std::search(d + from, d + n, magic, magic + 4);
    if (hit == d + n) return false;
    s.ctrl_off = size_t(hit - d);
    s.ctrl_size = n - s.ctrl_off;
    bool full = s.ctrl_size >= 0x710;
    const uint8_t* c = d + s.ctrl_off;
    s.start = full ? le32(c + 0x708) : 0;
    s.length = full ? le32(c + 0x6F8) : 0;
    s.const_off = full ? le32(c + 0x700) : 0;
    s.const_size = full ? le32(c + 0x6FC) : 0;
    // check_stage: anything off and the stage is left out
    if (kCodeOff + s.code_size > n) return false;
    uint32_t want = s.kind == "compute" ? kCompute : kGraphics;
    if (u32_at(d, n, kCodeOff) != want) return false;
    if (le32(c) != kCtrlMagic) return false;
    if (full) {
        size_t code = kCodeOff, end = size_t(s.start) + s.length;
        if (le32(c + 0x704) != s.code_size) return false;
        if (s.start > 4 && !zero(d, n, code + 4, code + s.start)) return false;
        if (s.const_off != ((end + 0xFF) & ~size_t(0xFF)) ||
            s.code_size != ((size_t(s.const_off) + s.const_size + 0xFF) & ~size_t(0xFF)))
            return false;
        if (!zero(d, n, code + end, code + s.const_off) ||
            !zero(d, n, code + s.const_off + s.const_size, code + s.code_size))
            return false;
    }
    if (s.kind != "compute") {
        uint32_t t = (u32_at(d, n, kCodeOff + 0x30) >> 10) & 0xF;
        const char* sph = t == 1 ? "vertex" : t == 2 ? "hull" : t == 3 ? "domain" : t == 4 ? "geometry" : t == 5 ? "pixel" : "";
        if (s.kind != sph) return false;
    }
    return true;
}

void make_dir(const std::string& p) {
#if defined(_WIN32)
    _mkdir(p.c_str());
#else
    mkdir(p.c_str(), 0755);
#endif
}

void write_file(const std::string& path, const uint8_t* p, size_t n) {
    FILE* f = std::fopen(path.c_str(), "wb");
    if (!f || std::fwrite(p, 1, n, f) != n) {
        if (f) std::fclose(f);
        throw Fail("cannot write " + path + " (" + std::strerror(errno) + ")");
    }
    if (std::fclose(f) != 0) throw Fail("cannot write " + path);
}

bool names_shader_dat(const std::string& path) {
    std::string low = path;
    for (char& c : low) c = char(std::tolower(static_cast<unsigned char>(c)));
    return low.find("shader") != std::string::npos && low.size() > 4 && low.compare(low.size() - 4, 4, ".dat") == 0;
}

}  // namespace

long extract_shaders(const std::string& data000_cpk, const std::string& out_dir,
                     const std::function<void(int, int)>& progress, std::string& error) {
    try {
        File cpk(data000_cpk);
        std::vector<CpkFile> dats;
        for (CpkFile& f : cpk_files(cpk))
            if (names_shader_dat(f.path)) dats.push_back(f);
        if (dats.empty()) throw Fail("no shader archives (*shader*.dat) in data000.cpk (is it NieR:Automata?)");
        // nier_shaders.py walks them in path order
        std::sort(dats.begin(), dats.end(), [](const CpkFile& a, const CpkFile& b) { return a.path < b.path; });
        make_dir(out_dir);
        const std::string gdir = out_dir + "/nier";
        make_dir(gdir);
        std::string manifest =
            "id\tstage\tcode_off\tcode_size\tctrl_off\tctrl_size\tprogram_start\tprogram_bytes\tconst_off\tconst_size\t"
            "code_sha1_16\tcode_file\tctrl_file\n";
        std::set<std::string> written;
        long distinct = 0;
        char line[1024];
        for (size_t i = 0; i < dats.size(); i++) {
            if (progress) progress(int(i), int(dats.size()));
            const CpkFile& f = dats[i];
            std::vector<uint8_t> raw(size_t(f.size));
            cpk.read(f.offset, raw.data(), raw.size());
            std::vector<uint8_t> data = f.extract != f.size ? crilayla(raw) : std::move(raw);
            for (const Member& m : dat_members(data)) {
                Stage s;
                if (!stage_of(m, s)) continue;
                std::string hash = sha1_16(m.data + kCodeOff, s.code_size);
                std::string code_file = "code_" + hash + ".bin";
                std::string ctrl_file = "ctrl_" + sha1_16(m.data + s.ctrl_off, s.ctrl_size) + ".bin";
                if (written.insert(code_file).second) {
                    write_file(gdir + "/" + code_file, m.data + kCodeOff, s.code_size);
                    distinct++;
                }
                if (written.insert(ctrl_file).second) write_file(gdir + "/" + ctrl_file, m.data + s.ctrl_off, s.ctrl_size);
                std::snprintf(line, sizeof line, "%s/%s\t%s\t0x%zx\t0x%zx\t0x%zx\t0x%zx\t0x%x\t0x%x\t0x%x\t0x%x\t%s\t%s\t%s\n",
                              f.path.c_str(), m.name.c_str(), s.kind.c_str(), kCodeOff, s.code_size, s.ctrl_off, s.ctrl_size,
                              s.start, s.length, s.const_off, s.const_size, hash.c_str(), code_file.c_str(), ctrl_file.c_str());
                manifest += line;
            }
        }
        write_file(gdir + "/manifest.tsv", reinterpret_cast<const uint8_t*>(manifest.data()), manifest.size());
        if (progress) progress(int(dats.size()), int(dats.size()));
        return distinct;
    } catch (const Fail& e) {
        error = e.what();
    } catch (const std::bad_alloc&) {
        error = "out of memory";
    }
    return -1;
}

}  // namespace nier
