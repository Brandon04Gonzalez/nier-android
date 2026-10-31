package io.github.nierrecomp;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The mods, as the runtime takes them (runtime/src/hle_fs.cpp, LayeredFS style): Internal storage/NieR Automata/mods/
 * <ModName>/romfs/<same path as in the game's RomFS>. A file there replaces the game's file of the same path; mods
 * apply in alphabetical order, the last one wins; a folder whose name starts with '_' (or '.') is ignored, which is how
 * a mod is turned off here. Paths are case sensitive on Android, so a mod must keep the game's case.
 * Installing takes a ZIP (the part under its romfs/ folder, wherever that sits inside) or a single .cpk, which goes to
 * the root of the RomFS, where the game keeps its own (data000.cpk...): a .cpk named like one of them replaces it.
 * Mods are only read at the game's start.
 */
final class Mods {
    /** A mod folder: its name without the '_' that turns it off, and whether it is on. */
    static final class Mod {
        final File dir;
        final String name;
        final boolean on;

        Mod(File dir) {
            this.dir = dir;
            String n = dir.getName();
            int k = 0;
            while (k < n.length() && n.charAt(k) == '_') k++;
            this.name = n.substring(k);
            this.on = k == 0;
        }
    }

    /** What an install did: the mod's folder name (under mods/) and the files written. */
    static final class Result {
        String folder;
        int files;
    }

    private Mods() {}

    static File modsDir() { return new File(Launcher.dataDir(), "mods"); }

    /** The mod folders (the ones with a romfs/ folder), by name. */
    static List<Mod> list() {
        List<Mod> out = new ArrayList<>();
        File[] kids = modsDir().listFiles();
        if (kids == null) return out;
        Arrays.sort(kids);
        for (File k : kids)
            if (k.isDirectory() && !k.getName().startsWith(".") && new File(k, "romfs").isDirectory()) {
                Mod m = new Mod(k);
                if (!m.name.isEmpty()) out.add(m);
            }
        return out;
    }

    static int countOn() {
        int n = 0;
        for (Mod m : list()) if (m.on) n++;
        return n;
    }

    /** Turns a mod on or off by renaming its folder (Name or _Name). False when the rename failed. */
    static boolean setOn(Mod m, boolean on) {
        if (m.on == on) return true;
        File to = new File(modsDir(), on ? m.name : "_" + m.name);
        return !to.exists() && m.dir.renameTo(to);
    }

    /** A folder name made from a file name: no extension, no path or odd characters, never hidden or off. */
    static String folderName(String fileName) {
        String base = fileName == null ? "" : fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceAll("[^A-Za-z0-9 ()\\[\\]+,.=@#&'_-]", "_").trim();
        while (base.startsWith("_") || base.startsWith(".")) base = base.substring(1);
        return base.isEmpty() ? "mod" : base;
    }

    /** The folder to install into: the mod of that name, where it is (on or off), else a new one (on). */
    static File installDir(String name) {
        File off = new File(modsDir(), "_" + name);
        File on = new File(modsDir(), name);
        return !on.isDirectory() && off.isDirectory() ? off : on;
    }

    /** True for a relative path that stays inside its folder. */
    static boolean safe(String rel) {
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("\\")) return false;
        for (String part : rel.split("/"))
            if (part.equals("..")) return false;
        return !rel.startsWith("__MACOSX/") && !rel.endsWith(".DS_Store");
    }

    /** The part of the paths up to and including their romfs/ folder (the shortest one), or null when none has one. */
    static String romfsPrefix(List<String> paths) {
        String best = null;
        for (String p : paths) {
            String lower = p.toLowerCase(Locale.ROOT);
            int at;
            if (lower.startsWith("romfs/")) {
                at = 0;
            } else {
                int k = lower.indexOf("/romfs/");
                if (k < 0) continue;
                at = k + 1;
            }
            String pre = p.substring(0, at + "romfs/".length());
            if (best == null || pre.length() < best.length()) best = pre;
        }
        return best;
    }

    static void write(File out, InputStream in) throws IOException {
        File dir = out.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot make " + dir);
        try (OutputStream o = new FileOutputStream(out)) {
            SaveFiles.copy(in, o);
        }
    }

    /** Null when the ZIP has no romfs/ folder. */
    static Result installZip(ContentResolver cr, Uri uri, String fileName) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(cr.openInputStream(uri))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; )
                if (!e.isDirectory()) names.add(e.getName().replace('\\', '/'));
        }
        String pre = romfsPrefix(names);
        if (pre == null) return null;
        Result r = new Result();
        File dir = installDir(folderName(fileName));
        r.folder = dir.getName();
        File romfs = new File(dir, "romfs");
        try (ZipInputStream z = new ZipInputStream(cr.openInputStream(uri))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                String path = e.getName().replace('\\', '/');
                if (!path.startsWith(pre)) continue;
                String rel = path.substring(pre.length());
                if (!safe(rel)) continue;
                write(new File(romfs, rel), z);
                r.files++;
            }
        }
        return r;
    }

    /** A .cpk, to the root of the mod's RomFS under its own name. */
    static Result installCpk(ContentResolver cr, Uri uri, String fileName) throws IOException {
        Result r = new Result();
        File dir = installDir(folderName(fileName));
        r.folder = dir.getName();
        String name = new File(fileName).getName();
        if (!safe(name)) throw new IOException("bad file name: " + fileName);
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("cannot open " + fileName);
            write(new File(new File(dir, "romfs"), name), in);
        }
        r.files = 1;
        return r;
    }
}
