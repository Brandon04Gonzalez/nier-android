package io.github.nierrecomp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The home screen in front of the game (a NativeActivity, libnier.so): checks for the player's data in Internal
 * storage/NieR Automata (asking for all-files access), shows the options and writes them to that folder's config.txt,
 * whose NX_* lines the runtime turns into its switches (the other keys it reads as settings):
 *   game text   the language of the game's texts (language=auto, default: the device's; or en-US, es, fr, de, it, ja,
 *               ko, zh-Hans, zh-Hant: runtime language.h hands it to the game as the console language)
 *   resolution  360p, 480p, 720p (default) or 1080p: NX_HANDHELD (1 below 1080p), resolution, NX_RENDER_RES, NX_RES
 *   fullscreen  the picture stretched to the whole screen (NX_STRETCH=1) or kept 16:9 (0, default)
 *   graphics    low (default), medium or high: textures one mip smaller (NX_LOD_BIAS=1), half a mip, or the game's own
 *   frame rate  30 (NX_FPS_CAP=30, default: runtime nvn_queue.cpp holds the present interval at 2) or 60
 *   shadows     on (NX_SHADOWS=1, default) or off (0: runtime nvn_draw.cpp skips the draws into shadow maps)
 *   FPS counter off (default) or on (NX_SHOW_FPS)
 *   on-screen controls on (default) or off (NX_TOUCH_SHOW), their size (NX_TOUCH_SIZE) and opacity (NX_TOUCH_OPACITY),
 *               and where they are (NX_TOUCH_LAYOUT, written by ControlsEditor)
 * It imports or exports the saves (user/save, SaveFiles.java: a ZIP or a folder in, a ZIP in Download out) and installs
 * or turns on and off the mods (mods/<Name>/romfs, Mods.java).
 * The game data is prepared on the PC from the player's own copy of the game (the same layout as private/nier:
 * analysis/ or exefs/, extract/romfs/, spirv/) and copied to Internal storage/NieR Automata: there is no on-phone
 * import.
 * The icon: a home screen shortcut with a picture of the player's own (Android lets an app pin shortcuts, not change
 * its own icon).
 * Other lines of config.txt are kept as they were. The launcher itself speaks the device's language (Lang.java:
 * Spanish, English, French, German, Italian, Portuguese, Japanese, Korean, Chinese; English otherwise).
 */
public class Launcher extends Activity {
    static final String DATA = "NieR Automata";

    // The launcher's colours: dark warm grey, the menus' parchment and their dark ink.
    static final int BACKGROUND = Color.rgb(24, 23, 20), PARCHMENT = Color.rgb(218, 212, 187),
            INK = Color.rgb(52, 49, 42);

    static final class Option {
        final String key, es, en;
        final String[] values, labelsEs, labelsEn;
        int pick;

        Option(String key, String es, String en, String[] values, String[] labelsEs, String[] labelsEn, int pick) {
            this.key = key;
            this.es = es;
            this.en = en;
            this.values = values;
            this.labelsEs = labelsEs;
            this.labelsEn = labelsEn;
            this.pick = pick;
        }
    }

    final List<Option> options = new ArrayList<>();
    final Map<String, String> config = new LinkedHashMap<>();
    TextView status;
    Button play;
    static final int PICK_ZIP = 1, PICK_FOLDER = 2, PICK_ICON = 3, PICK_MOD = 4;

    String t(String spanish, String english) { return Lang.t(spanish, english); }

    String label(Option o) { return t(o.labelsEs[o.pick], o.labelsEn[o.pick]); }

    static File dataDir() { return new File(Environment.getExternalStorageDirectory(), DATA); }

    boolean hasAccess() { return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager(); }

    String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last.substring(last.lastIndexOf('/') + 1);
    }

    void pick(int request, String type, boolean multiple) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        if (multiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, request);
    }

    // A home screen shortcut with the player's picture (cropped to a square; an adaptive icon, so the home screen gives
    // it its own shape).
    void onIconPicked(Uri uri) {
        try {
            Bitmap src;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                src = BitmapFactory.decodeStream(in);
            }
            if (src == null) throw new IllegalArgumentException("not a picture");
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Bitmap icon = Bitmap.createScaledBitmap(square, 432, 432, true);
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                tell(t("Icono", "Icon"), t("Tu pantalla de inicio no admite accesos directos.",
                        "Your home screen does not support shortcuts."));
                return;
            }
            Intent launch = new Intent(this, Launcher.class).setAction(Intent.ACTION_MAIN);
            ShortcutInfo info = new ShortcutInfo.Builder(this, "picture_" + System.currentTimeMillis())
                    .setShortLabel("NieR").setLongLabel("NieR Automata").setIcon(Icon.createWithAdaptiveBitmap(icon))
                    .setIntent(launch).build();
            sm.requestPinShortcut(info, null);
            tell(t("Icono", "Icon"), t("Acepta el aviso para poner el acceso directo con tu imagen en la pantalla de inicio. "
                            + "El icono del cajón de apps no cambia: Android no deja que una app cambie el suyo.",
                    "Accept the prompt to put the shortcut with your picture on the home screen. The icon in the app "
                            + "drawer stays: Android does not let an app change its own."));
        } catch (Exception e) {
            tell(t("Icono", "Icon"), t("No se pudo usar esa imagen.", "That picture could not be used."));
        }
    }

    // ---- the game data, prepared on the PC ----

    // What the game needs in the data folder: the game's files (extract/romfs, with its CPKs), the translated shaders
    // (spirv/, results.tsv holds their records) and the game's code: analysis/main.bin for the recompiled build (the
    // runtime's loader.cpp), exefs/main for the native one.
    List<String> missingData() {
        List<String> missing = new ArrayList<>();
        File d = dataDir();
        if (!new File(d, "extract/romfs/data000.cpk").canRead()) missing.add("extract/romfs/data000.cpk");
        if (!new File(d, "spirv/results.tsv").canRead()) missing.add("spirv/results.tsv");
        if (!new File(d, "analysis/main.bin").canRead() && !new File(d, "exefs/main").canRead())
            missing.add("analysis/main.bin (exefs/main)");
        return missing;
    }

    // The build ID the port is made for (NieR:Automata The End of YoRHa Edition, update 1.0.3): the start of
    // exefs/main's module ID (NSO header +0x40), as games/nier/game.cpp names it. The native build refuses another
    // (runtime main.cpp); say so here instead of closing.
    static final String BUILD_ID = "3C10EC35C5EF4C36";

    // Null when exefs/main is missing or the right version, else its build ID.
    String wrongBuild() {
        File main = new File(dataDir(), "exefs/main");
        if (!main.canRead()) return null;
        byte[] id = new byte[8];
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(main, "r")) {
            f.seek(0x40);
            f.readFully(id);
        } catch (java.io.IOException e) {
            return "?";
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : id) hex.append(String.format("%02X", b & 0xFF));
        return hex.toString().equals(BUILD_ID) ? null : hex.toString();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // The game's texts (runtime language.h): its own language names, as a console lists them.
        options.add(new Option("language", "Idioma de textos y subtítulos", "Text and subtitle language",
                new String[] {"auto", "en-US", "es", "fr", "de", "it", "ja", "ko", "zh-Hans", "zh-Hant"},
                new String[] {"Automático (idioma del dispositivo)", "English", "Español", "Français", "Deutsch",
                        "Italiano", "日本語", "한국어", "简体中文", "繁體中文"},
                new String[] {"Automatic (device language)", "English", "Español", "Français", "Deutsch", "Italiano",
                        "日本語", "한국어", "简体中文", "繁體中文"}, 0));
        options.add(new Option("RESOLUTION", "Resolución", "Resolution",
                new String[] {"360p", "480p", "720p", "1080p"},
                new String[] {"360p", "480p", "720p", "1080p"},
                new String[] {"360p", "480p", "720p", "1080p"}, 2));
        options.add(new Option("NX_STRETCH", "Pantalla completa (estirar)", "Fullscreen (stretch)",
                new String[] {"0", "1"},
                new String[] {"Desactivada", "Activada"},
                new String[] {"Off", "On"}, 0));
        options.add(new Option("GRAPHICS", "Gráficos", "Graphics", new String[] {"low", "medium", "high"},
                new String[] {"Bajos", "Medios", "Altos"}, new String[] {"Low", "Medium", "High"}, 0));
        options.add(new Option("NX_FPS_CAP", "Fotogramas por segundo", "Frame rate", new String[] {"30", "60"},
                new String[] {"30 fps", "60 fps (experimental)"}, new String[] {"30 fps", "60 fps (experimental)"}, 0));
        options.add(new Option("NX_SHADOWS", "Sombras", "Shadows", new String[] {"0", "1"},
                new String[] {"Desactivadas", "Activadas"}, new String[] {"Off", "On"}, 1));
        options.add(new Option("NX_SHOW_FPS", "Contador de FPS", "FPS counter", new String[] {"0", "1"},
                new String[] {"Desactivado", "Activado"}, new String[] {"Off", "On"}, 0));
        options.add(new Option("NX_TOUCH_SHOW", "Controles táctiles", "On-screen controls", new String[] {"1", "0"},
                new String[] {"Activados", "Desactivados"}, new String[] {"On", "Off"}, 0));
        options.add(new Option("NX_TOUCH_SIZE", "Tamaño de los botones", "Button size",
                new String[] {"80", "100", "125"}, new String[] {"Pequeño", "Normal", "Grande"},
                new String[] {"Small", "Normal", "Large"}, 1));
        options.add(new Option("NX_TOUCH_OPACITY", "Opacidad de los botones", "Button opacity",
                new String[] {"50", "85", "100"}, new String[] {"Baja", "Normal", "Alta"},
                new String[] {"Low", "Normal", "High"}, 1));
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("NieR:Automata");
        title.setTextColor(PARCHMENT);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.rgb(220, 220, 220));
        status.setTextSize(14);
        status.setPadding(0, dp(6), 0, dp(14));
        root.addView(status);

        addRow(root, t("Icono", "Icon"), t("Elegir imagen", "Choose a picture"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pick(PICK_ICON, "image/*", false);
            }
        });

        for (final Option o : options) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));
            TextView name = new TextView(this);
            name.setText(t(o.es, o.en));
            name.setTextColor(Color.WHITE);
            name.setTextSize(17);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            final Button value = new Button(this);
            value.setAllCaps(false);
            value.setMinWidth(dp(180));
            row.addView(value);
            value.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    o.pick = (o.pick + 1) % o.values.length;
                    value.setText(label(o));
                    save();
                }
            });
            value.setTag(o);
            root.addView(row);
        }

        addRow(root, t("Posición de los controles", "Controls position"), t("Mover", "Move"),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!hasAccess()) {
                            askAccess();
                            return;
                        }
                        save();
                        startActivity(new Intent(Launcher.this, ControlsEditor.class));
                    }
                });

        // Saves: in from a ZIP or a folder (a Switch backup or another player's), out to a ZIP in Download.
        addPairRow(root, t("Partidas guardadas", "Saves"), t("Importar", "Import"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                askImport();
            }
        }, t("Exportar", "Export"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                exportSaves();
            }
        });

        // Mods: a ZIP (its romfs/ folder) or a .cpk in, and each mod in mods/ turned on or off.
        addPairRow(root, t("Mods", "Mods"), t("Instalar", "Install"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                pick(PICK_MOD, "*/*", false);
            }
        }, t("Gestionar", "Manage"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                manageMods();
            }
        });

        play = new Button(this);
        play.setTextSize(20);
        play.setAllCaps(false);
        play.setBackgroundColor(PARCHMENT);
        play.setTextColor(INK);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(18);
        root.addView(play, lp);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasAccess()) {
                    askAccess();
                    return;
                }
                if (!missingData().isEmpty() || wrongBuild() != null) {
                    refresh();
                    return;
                }
                save();
                startActivity(new Intent(Launcher.this, android.app.NativeActivity.class));
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    void refresh() {
        load();
        for (Option o : options) {
            String v = config.get(o.key);
            if ("RESOLUTION".equals(o.key) && v == null) {
                String hh = config.get("NX_HANDHELD");
                if ("0".equals(hh)) v = "1080p";
                else {
                    String r = config.get("resolution");
                    if (r != null && r.contains("360")) v = "360p";
                    else if (r != null && r.contains("480")) v = "480p";
                    else v = "720p";
                }
            }
            for (int k = 0; v != null && k < o.values.length; k++)
                if (o.values[k].equals(v)) o.pick = k;
        }
        updateButtons(getWindow().getDecorView());
        List<String> missing = hasAccess() ? missingData() : new ArrayList<String>();
        if (!hasAccess()) {
            status.setText(t("Hace falta el permiso de acceso a todos los archivos para leer los datos del juego.",
                    "All-files access is needed to read the game data."));
            play.setText(t("Dar permiso", "Grant access"));
        } else if (!missing.isEmpty()) {
            status.setText(Lang.f("Faltan datos del juego en Almacenamiento interno/%s: %s. Prepáralos en el PC a partir "
                            + "de tu propia copia del juego y cópialos ahí.",
                    "Game data missing in Internal storage/%s: %s. Prepare it on the PC from your own copy of the game "
                            + "and copy it there.", DATA, android.text.TextUtils.join(", ", missing)));
            play.setText(t("Volver a buscar", "Look again"));
        } else if (wrongBuild() != null) {
            status.setText(Lang.f("Almacenamiento interno/%s/exefs/main no es NieR Automata 1.0.3 (build %s). Copia el "
                            + "exefs de la versión 1.0.3 del juego.",
                    "Internal storage/%s/exefs/main is not NieR Automata 1.0.3 (build %s). Copy the exefs of the "
                            + "game's version 1.0.3.", DATA, wrongBuild()));
            play.setText(t("Volver a buscar", "Look again"));
        } else {
            status.setText(Lang.f("Datos listos: %s", "Data ready: %s", dataDir().getPath()));
            play.setText(t("Jugar", "Play"));
        }
    }

    void updateButtons(View v) {
        if (v instanceof Button && v.getTag() instanceof Option) {
            ((Button) v).setText(label((Option) v.getTag()));
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int k = 0; k < g.getChildCount(); k++) updateButtons(g.getChildAt(k));
        }
    }

    void tell(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    // ---- saves ----

    void askImport() {
        new AlertDialog.Builder(this)
                .setTitle(t("Importar partidas", "Import saves"))
                .setMessage(t("Elige un archivo ZIP o una carpeta con partidas de NieR Automata (los archivos GameData y "
                                + "SlotData_0, SlotData_1..., como los exporta JKSV o Checkpoint, o el ZIP que exporta "
                                + "este botón). Antes se guarda una copia de tus partidas actuales.",
                        "Pick a ZIP file or a folder with NieR Automata saves (the GameData and SlotData_0, SlotData_1... "
                                + "files, as JKSV or Checkpoint export them, or the ZIP the Export button makes). Your "
                                + "current saves are copied aside first."))
                .setPositiveButton(t("Archivo ZIP", "ZIP file"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip",
                                "application/x-zip-compressed", "application/octet-stream"});
                        startActivityForResult(i, PICK_ZIP);
                    }
                })
                .setNeutralButton(t("Carpeta", "Folder"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
                    }
                })
                .setNegativeButton(t("Cancelar", "Cancel"), null)
                .show();
    }

    void importSaves(int request, Uri uri) {
        try {
            SaveFiles.Result r = request == PICK_ZIP ? SaveFiles.importZip(getContentResolver(), uri)
                    : SaveFiles.importTree(getContentResolver(), uri);
            if (r == null) {
                tell(t("No hay partidas", "No saves found"),
                        t("Ahí no encontré partidas de NieR Automata: busca los archivos GameData o SlotData_0, "
                                        + "SlotData_1...",
                                "No NieR Automata saves there: look for the GameData or SlotData_0, SlotData_1... files."));
                return;
            }
            String kept = r.backup == null ? "" : Lang.f("\n\nTus partidas anteriores quedaron en user/%s.",
                    "\n\nYour previous saves are in user/%s.", r.backup);
            tell(t("Partidas importadas", "Saves imported"),
                    Lang.f("%d archivos (%s). El juego los carga en su próximo inicio.",
                            "%d files (%s). The game loads them at its next start.", r.files,
                            android.text.TextUtils.join(", ", r.saves)) + kept);
        } catch (Exception e) {
            tell(t("No se pudo importar", "Import failed"), String.valueOf(e.getMessage()));
        }
    }

    void exportSaves() {
        try {
            java.io.File out = SaveFiles.export();
            // Listed at once by the file pickers and the Files app (written straight to disk, it would wait for a scan).
            if (out != null) android.media.MediaScannerConnection.scanFile(this, new String[] {out.getPath()}, null, null);
            if (out == null) tell(t("No hay partidas", "No saves"), t("Todavía no hay partidas guardadas.", "There are no saves yet."));
            else tell(t("Partidas exportadas", "Saves exported"),
                    Lang.f("Guardadas en Descargas/%s. Se importan tal cual con el botón Importar.",
                            "Saved to Download/%s. The Import button takes it back as is.", out.getName()));
        } catch (Exception e) {
            tell(t("No se pudo exportar", "Export failed"), String.valueOf(e.getMessage()));
        }
    }

    // ---- mods ----

    // A ZIP or a .cpk, copied in on a thread of its own (a mod can be large) behind a dialog that waits for it.
    void installMod(final Uri uri) {
        final String name = displayName(uri);
        final String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip") && !lower.endsWith(".cpk")) {
            tell(t("No es un mod", "Not a mod"), t("Elige el archivo ZIP de un mod (con una carpeta romfs dentro) o un "
                            + "archivo .cpk que reemplace uno del juego.",
                    "Choose a mod's ZIP file (with a romfs folder inside) or a .cpk file that replaces one of the game's."));
            return;
        }
        final AlertDialog wait = new AlertDialog.Builder(this).setTitle(t("Mods", "Mods"))
                .setMessage(t("Instalando el mod…", "Installing the mod…")).setCancelable(false).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Mods.Result r = null;
                String error = null;
                try {
                    r = lower.endsWith(".zip") ? Mods.installZip(getContentResolver(), uri, name)
                            : Mods.installCpk(getContentResolver(), uri, name);
                } catch (Exception e) {
                    error = String.valueOf(e.getMessage());
                }
                final Mods.Result done = r;
                final String failed = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isDestroyed()) return;  // the home screen closed while the mod was copied
                        wait.dismiss();
                        if (failed != null) tell(t("No se pudo instalar", "Install failed"), failed);
                        else if (done == null)
                            tell(t("No es un mod", "Not a mod"), t("Ese ZIP no tiene una carpeta romfs. Un mod lleva los "
                                            + "archivos que reemplaza en romfs/, con las mismas rutas que los del juego.",
                                    "That ZIP has no romfs folder. A mod holds the files it replaces under romfs/, with "
                                            + "the same paths as the game's."));
                        else tell(t("Mod instalado", "Mod installed"),
                                    Lang.f("%d archivos en mods/%s. El juego lo carga en su próximo inicio.",
                                            "%d files in mods/%s. The game loads it at its next start.", done.files,
                                            done.folder));
                    }
                });
            }
        }).start();
    }

    // The mods in mods/, each with a check box: on (Name) or off (_Name, which the runtime skips).
    void manageMods() {
        final List<Mods.Mod> mods = Mods.list();
        if (mods.isEmpty()) {
            tell(t("No hay mods", "No mods"), Lang.f("Todavía no hay mods. Instala un ZIP o un .cpk, o copia cada mod a "
                            + "Almacenamiento interno/%s/mods/<nombre>/romfs/ (con las mismas rutas que los archivos del "
                            + "juego).",
                    "No mods yet. Install a ZIP or a .cpk, or copy each mod to Internal storage/%s/mods/<name>/romfs/ "
                            + "(the same paths as the game's files).", DATA));
            return;
        }
        String[] names = new String[mods.size()];
        final boolean[] on = new boolean[mods.size()];
        for (int k = 0; k < mods.size(); k++) {
            names[k] = mods.get(k).name;
            on[k] = mods.get(k).on;
        }
        new AlertDialog.Builder(this)
                .setTitle(t("Mods activos", "Active mods"))
                .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which, boolean checked) {
                        on[which] = checked;
                    }
                })
                .setPositiveButton(t("Aplicar", "Apply"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        List<String> failed = new ArrayList<>();
                        for (int k = 0; k < mods.size(); k++)
                            if (!Mods.setOn(mods.get(k), on[k])) failed.add(mods.get(k).name);
                        if (!failed.isEmpty())
                            tell(t("Mods", "Mods"), Lang.f("No se pudo renombrar la carpeta de un mod: %s",
                                    "A mod folder could not be renamed: %s", android.text.TextUtils.join(", ", failed)));
                    }
                })
                .setNegativeButton(t("Cancelar", "Cancel"), null)
                .show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_ICON) onIconPicked(data.getData());
        else if (request == PICK_MOD) installMod(data.getData());
        else if (request == PICK_ZIP || request == PICK_FOLDER) importSaves(request, data.getData());
    }

    void askAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    // ---- config.txt: KEY=value lines, in order ----

    static Map<String, String> readConfig(File f) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!f.canRead()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (eq > 0) out.put(line.substring(0, eq), line.substring(eq + 1));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static boolean writeConfig(File f, Map<String, String> config) {
        try (FileWriter w = new FileWriter(f)) {
            for (Map.Entry<String, String> e : config.entrySet()) w.write(e.getKey() + "=" + e.getValue() + "\n");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void load() {
        config.clear();
        config.putAll(readConfig(new File(dataDir(), "config.txt")));
    }

    void save() {
        if (!hasAccess()) return;
        dataDir().mkdirs();
        if (!dataDir().isDirectory()) return;
        load();
        for (Option o : options) config.put(o.key, o.values[o.pick]);
        final String res = config.get("RESOLUTION");
        if ("360p".equals(res)) {
            config.put("NX_HANDHELD", "1");
            config.put("resolution", "640x360");
            config.put("NX_RENDER_RES", "640x360");
            config.put("NX_RES", "360p");
        } else if ("480p".equals(res)) {
            config.put("NX_HANDHELD", "1");
            config.put("resolution", "854x480");
            config.put("NX_RENDER_RES", "854x480");
            config.put("NX_RES", "480p");
        } else if ("1080p".equals(res)) {
            config.put("NX_HANDHELD", "0");
            config.put("resolution", "1920x1080");
            config.put("NX_RENDER_RES", "1920x1080");
            config.put("NX_RES", "1080p");
        } else {
            config.put("NX_HANDHELD", "1");
            config.put("resolution", "1280x720");
            config.put("NX_RENDER_RES", "1280x720");
            config.put("NX_RES", "720p");
        }
        for (String k : new String[] {"RENDER_SCALE", "NX_ANISO_MAX", "NX_LOD_BIAS"}) config.remove(k);
        final String preset = config.get("GRAPHICS");
        if ("low".equals(preset)) config.put("NX_LOD_BIAS", "1");
        else if ("medium".equals(preset)) config.put("NX_LOD_BIAS", "0.5");
        writeConfig(new File(dataDir(), "config.txt"), config);
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    LinearLayout row(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    Button button(String text, int minWidth, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setMinWidth(dp(minWidth));
        b.setText(text);
        b.setOnClickListener(click);
        return b;
    }

    void addRow(LinearLayout root, String label, String button, View.OnClickListener click) {
        LinearLayout row = row(label);
        row.addView(button(button, 180, click));
        root.addView(row);
    }

    void addPairRow(LinearLayout root, String label, String first, View.OnClickListener firstClick, String second,
            View.OnClickListener secondClick) {
        LinearLayout row = row(label);
        row.addView(button(first, 88, firstClick));
        row.addView(button(second, 88, secondClick));
        root.addView(row);
    }
}
