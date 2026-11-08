// NieR:Automata's shaders out of the imported RomFS, on the phone: what games/p5r/tools/cpk.py (--extract --grep
// shader), games/nier/tools/dat.py and games/nier/tools/nier_shaders.py (with tools/maxwell/containers.py's Stage) do
// on the PC, in the same layout, for the translator (spirv_harness --batch):
//   <out>/nier/manifest.tsv, code_<sha1>.bin, ctrl_<sha1>.bin
// one group, every stage (.vso/.pso/.gso/.cso) of the DAT archives of data000.cpk whose path names a shader.
#pragma once

#include <functional>
#include <string>

namespace nier {

// Distinct code blocks written, or -1 with `error` set. progress(done, total) counts the DAT archives.
long extract_shaders(const std::string& data000_cpk, const std::string& out_dir,
                     const std::function<void(int, int)>& progress, std::string& error);

}  // namespace nier
