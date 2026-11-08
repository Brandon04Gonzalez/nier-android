package io.github.nierrecomp;

/** The import on the phone (games/nier/import/nier_import_jni.cpp): only in ImportActivity's process. */
final class Importer {
    static {
        System.loadLibrary("nierimport");
    }

    /** Blocking: 0 when the game and its shaders are ready in dataDir, else see error(). */
    static native int run(int baseFd, String baseName, int updateFd, String updateName, String keysPath,
                          String titleKeysPath, String dataDir, String workDir, int jobs);

    /** {phase, done, total}: 1 importing the game (MiB), 2 extracting the shaders, 3 translating them, 4 finishing, 5 done. */
    static native int[] progress();

    static native String error();

    static native void cancel();

    private Importer() {}
}
