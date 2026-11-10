package io.github.nierrecomp;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The game's saves, imported and exported from the home screen. They live in Internal storage/NieR Automata/user/save
 * as on the Switch: the GameData file and a SlotData_<n> file per slot, in one folder. An import takes a ZIP or a folder
 * (as JKSV or Checkpoint export a Switch save, or PC saves like SlotData_0.dat), finds the folder that holds those files
 * wherever it sits inside, copies the current saves to user/save_backup_<date> first, then writes everything in that
 * folder over them (files it does not bring stay). Saves from PC (.dat files or saves with SteamID headers) are
 * automatically converted to Switch format. An export writes Download/NieR-saves-<date>.zip (the whole save folder),
 * which imports back as is.
 */
final class SaveFiles {
    // A file the game keeps in its save folder: what marks the folder to import inside a ZIP or a picked folder.
    // Matches Switch saves (no extension) and PC saves (.dat extension).
    static final Pattern MARK = Pattern.compile("(?:^|/)(GameData|SystemData|SlotData_\\d+)(?:\\.dat)?$", Pattern.CASE_INSENSITIVE);
    static final long MAX_BYTES = 256L << 20;  // a save is a few MB at most: anything bigger is not one

    /** What an import did: files written, the game's save files among them, the backup folder (null: nothing kept). */
    static final class Result {
        int files;
        int converted;
        final TreeSet<String> saves = new TreeSet<>();
        String backup;
    }

    private SaveFiles() {}

    static File saveDir() { return new File(Launcher.dataDir(), "user/save"); }

    static String stamp() { return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()); }

    /** The folder part of the paths that end in one of the game's save files (the shortest), or null when none does. */
    static String prefix(List<String> paths) {
        String best = null;
        for (String p : paths) {
            Matcher m = MARK.matcher(p);
            if (m.find()) {
                String pre = p.substring(0, m.start(1));
                if (best == null || pre.length() < best.length()) best = pre;
            }
        }
        return best;
    }

    /** The path under the save folder for an imported path, or null to skip it (outside the save, or unsafe). */
    static String target(String path, String prefix) {
        if (!path.startsWith(prefix)) return null;
        String rel = path.substring(prefix.length());
        if (rel.isEmpty() || rel.startsWith("/") || rel.contains("..") || rel.contains("\\")) return null;
        if (rel.startsWith("__MACOSX/") || rel.endsWith(".DS_Store")) return null;
        return rel;
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 16];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    static void copyTree(File from, File to) throws IOException {
        if (from.isDirectory()) {
            if (!to.isDirectory() && !to.mkdirs()) throw new IOException("cannot make " + to);
            File[] kids = from.listFiles();
            if (kids != null) for (File k : kids) copyTree(k, new File(to, k.getName()));
        } else {
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
                copy(in, out);
            }
        }
    }

    static boolean hasFiles(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return false;
        for (File k : kids) if (k.isFile() || hasFiles(k)) return true;
        return false;
    }

    /** The current saves, copied aside; the folder's name, or null when there are none. */
    static String backup() throws IOException {
        File src = saveDir();
        if (!hasFiles(src)) return null;
        String name = "save_backup_" + stamp();
        copyTree(src, new File(Launcher.dataDir(), "user/" + name));
        return name;
    }

    /**
     * Normalizes save file names from PC to Switch by stripping the .dat extension if it matches a save pattern.
     */
    static String normalizeSaveName(String rel) {
        if (rel.toLowerCase(Locale.ROOT).endsWith(".dat")) {
            String withoutDat = rel.substring(0, rel.length() - 4);
            int slash = withoutDat.lastIndexOf('/');
            String base = slash >= 0 ? withoutDat.substring(slash + 1) : withoutDat;
            if (base.matches("(?i)GameData|SystemData|SlotData_\\d+")) {
                return withoutDat;
            }
        }
        return rel;
    }

    /**
     * Converts PC save headers to Switch format by clearing the SteamID64 field.
     * Switch saves use all zeros for this field, whereas PC has an 8-byte SteamID64.
     * Returns true if any header bytes were zeroed out.
     */
    static boolean convertSaveData(String filename, byte[] data) {
        if (data == null) return false;
        int slash = filename.lastIndexOf('/');
        String base = slash >= 0 ? filename.substring(slash + 1) : filename;
        boolean changed = false;

        // SlotData_0, SlotData_1, etc.
        // Size: 235980 bytes. Header: 0..4 (zeros), 4..12 (SteamID64 on PC, 0 on Switch), 12..16 (magic 0x09025954)
        if (base.matches("(?i)SlotData_\\d+")) {
            if (data.length >= 12) {
                for (int i = 4; i < 12; i++) {
                    if (data[i] != 0) {
                        data[i] = 0;
                        changed = true;
                    }
                }
            }
        }
        // GameData
        // Size: 1128 bytes. Header: 0..8 (SteamID64 on PC, 0 on Switch), 8..12 (magic 0x09025954)
        else if (base.equalsIgnoreCase("GameData")) {
            if (data.length >= 8) {
                for (int i = 0; i < 8; i++) {
                    if (data[i] != 0) {
                        data[i] = 0;
                        changed = true;
                    }
                }
            }
        }
        // SystemData
        // Size: 72 bytes. Header: 0..8 (SteamID64 on PC, 0 on Switch)
        else if (base.equalsIgnoreCase("SystemData")) {
            if (data.length >= 8) {
                for (int i = 0; i < 8; i++) {
                    if (data[i] != 0) {
                        data[i] = 0;
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    static void write(Result r, String rel, InputStream in) throws IOException {
        String destRel = normalizeSaveName(rel);
        boolean isPcExt = !destRel.equalsIgnoreCase(rel);
        File out = new File(saveDir(), destRel);
        File dir = out.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot make " + dir);

        byte[] data = readAll(in);
        boolean dataConverted = convertSaveData(destRel, data);
        if (isPcExt || dataConverted) {
            r.converted++;
        }

        try (OutputStream o = new FileOutputStream(out)) {
            o.write(data);
        }
        r.files++;
        if (destRel.indexOf('/') < 0 && MARK.matcher(destRel).find()) r.saves.add(destRel);
    }

    /**
     * Checks the user save folder for PC save files (*.dat or files with SteamID headers)
     * and converts them in-place to native Switch format.
     * Returns the count of converted files.
     */
    static int convertExistingSaves() {
        File dir = saveDir();
        if (!dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int converted = 0;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            String norm = normalizeSaveName(name);
            boolean isDat = !norm.equals(name);

            if (!MARK.matcher(name).find() && !MARK.matcher(norm).find()) continue;

            try {
                byte[] data;
                try (InputStream in = new FileInputStream(f)) {
                    data = readAll(in);
                }
                boolean dataChanged = convertSaveData(norm, data);
                if (isDat || dataChanged) {
                    File target = new File(dir, norm);
                    try (OutputStream out = new FileOutputStream(target)) {
                        out.write(data);
                    }
                    if (isDat && !target.equals(f)) {
                        f.delete();
                    }
                    converted++;
                }
            } catch (IOException ignored) {}
        }
        return converted;
    }

    /** Null when the ZIP holds no saves. */
    static Result importZip(ContentResolver cr, Uri uri) throws IOException {
        List<String> names = new ArrayList<>();
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(cr.openInputStream(uri))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; )
                if (!e.isDirectory()) {
                    names.add(e.getName().replace('\\', '/'));
                    total += Math.max(0, e.getSize());
                }
        }
        String pre = prefix(names);
        if (pre == null || total > MAX_BYTES) return null;
        Result r = new Result();
        r.backup = backup();
        try (ZipInputStream z = new ZipInputStream(cr.openInputStream(uri))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                String rel = target(e.getName().replace('\\', '/'), pre);
                if (rel != null) write(r, rel, z);
            }
        }
        return r;
    }

    static void listTree(ContentResolver cr, Uri tree, String docId, String path, Map<String, Uri> out)
            throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE};
        try (Cursor c = cr.query(children, cols, null, null, null)) {
            if (c == null) throw new IOException("cannot list the folder");
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (Document.MIME_TYPE_DIR.equals(mime)) listTree(cr, tree, id, path + name + "/", out);
                else out.put(path + name, DocumentsContract.buildDocumentUriUsingTree(tree, id));
                if (out.size() > 4096) throw new IOException("too many files: is this a save folder?");
            }
        }
    }

    /** Null when the folder holds no saves. The folder itself may be the save folder or hold it anywhere inside. */
    static Result importTree(ContentResolver cr, Uri tree) throws IOException {
        String root = DocumentsContract.getTreeDocumentId(tree);
        String rootName = "";
        try (Cursor c = cr.query(DocumentsContract.buildDocumentUriUsingTree(tree, root),
                new String[] {Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) rootName = c.getString(0);
        }
        Map<String, Uri> files = new LinkedHashMap<>();
        listTree(cr, tree, root, rootName + "/", files);
        String pre = prefix(new ArrayList<>(files.keySet()));
        if (pre == null) return null;
        Result r = new Result();
        r.backup = backup();
        for (Map.Entry<String, Uri> f : files.entrySet()) {
            String rel = target(f.getKey(), pre);
            if (rel == null) continue;
            try (InputStream in = cr.openInputStream(f.getValue())) {
                if (in != null) write(r, rel, in);
            }
        }
        return r;
    }

    static void zipTree(ZipOutputStream z, File dir, String path) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) {
                zipTree(z, k, path + k.getName() + "/");
            } else {
                z.putNextEntry(new ZipEntry(path + k.getName()));
                try (InputStream in = new FileInputStream(k)) {
                    copy(in, z);
                }
                z.closeEntry();
            }
        }
    }

    /** The ZIP written, or null when there are no saves to export. */
    static File export() throws IOException {
        if (!hasFiles(saveDir())) return null;
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot make " + dir);
        File out = new File(dir, "NieR-saves-" + stamp() + ".zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            zipTree(z, saveDir(), "save/");
        }
        return out;
    }
}
