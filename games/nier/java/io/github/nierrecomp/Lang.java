package io.github.nierrecomp;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The launcher's texts in the device's language. Callers keep their Spanish and English pair (t(es, en)); the other
 * languages are looked up here by the English text, which falls back to English when a text has no translation.
 * Texts with a variable part take it through String.format (%s, %d), so the key stays the same.
 * Only the launcher: the game picks its own text language (the "language" key of config.txt, or the device's).
 */
final class Lang {
    // The columns of the table, after the English key.
    private static final String[] COLUMNS = {"fr", "de", "it", "pt", "ja", "ko", "zh-Hans", "zh-Hant"};
    private static final Map<String, String[]> TABLE = new HashMap<>();
    private static final String DEVICE = device();

    private Lang() {}

    /** The device's language as one of es, en and the table's columns (anything else: en). */
    static String device() {
        Locale l = Locale.getDefault();
        String lang = l.getLanguage();
        if (lang.equals("zh")) {
            String script = l.getScript(), country = l.getCountry();
            boolean traditional = script.equals("Hant")
                    || (script.isEmpty() && (country.equals("TW") || country.equals("HK") || country.equals("MO")));
            return traditional ? "zh-Hant" : "zh-Hans";
        }
        if (lang.equals("es") || lang.equals("en")) return lang;
        for (String c : COLUMNS)
            if (c.equals(lang)) return c;
        return "en";
    }

    static String t(String spanish, String english) {
        if (DEVICE.equals("es")) return spanish;
        if (DEVICE.equals("en")) return english;
        String[] row = TABLE.get(english);
        if (row == null) return english;
        for (int k = 0; k < COLUMNS.length; k++)
            if (COLUMNS[k].equals(DEVICE)) return row[k];
        return english;
    }

    static String f(String spanish, String english, Object... args) {
        return String.format(Locale.getDefault(), t(spanish, english), args);
    }

    private static void put(String english, String... others) {
        if (others.length != COLUMNS.length) throw new IllegalArgumentException(english);
        TABLE.put(english, others);
    }

    static {
        // Options
        put("Text and subtitle language", "Langue des textes et sous-titres", "Sprache für Texte und Untertitel",
                "Lingua di testi e sottotitoli", "Idioma dos textos e legendas", "テキストと字幕の言語", "텍스트 및 자막 언어",
                "文本和字幕语言", "文字與字幕語言");
        put("Automatic (device language)", "Automatique (langue de l'appareil)", "Automatisch (Gerätesprache)",
                "Automatica (lingua del dispositivo)", "Automático (idioma do dispositivo)", "自動（端末の言語）",
                "자동 (기기 언어)", "自动（设备语言）", "自動（裝置語言）");
        put("Resolution", "Résolution", "Auflösung", "Risoluzione", "Resolução", "解像度", "해상도", "分辨率", "解析度");
        put("Graphics", "Graphismes", "Grafik", "Grafica", "Gráficos", "グラフィック", "그래픽", "画质", "畫質");
        put("Low", "Bas", "Niedrig", "Bassa", "Baixo", "低", "낮음", "低", "低");
        put("Medium", "Moyens", "Mittel", "Media", "Médio", "中", "중간", "中", "中");
        put("High", "Élevés", "Hoch", "Alta", "Alto", "高", "높음", "高", "高");
        put("Frame rate", "Images par seconde", "Bildrate", "Fotogrammi al secondo", "Quadros por segundo",
                "フレームレート", "프레임 레이트", "帧率", "影格率");
        put("60 fps (experimental)", "60 fps (expérimental)", "60 fps (experimentell)", "60 fps (sperimentale)",
                "60 fps (experimental)", "60 fps（実験的）", "60 fps (실험적)", "60 fps（实验性）", "60 fps（實驗性）");
        put("Shadows", "Ombres", "Schatten", "Ombre", "Sombras", "影", "그림자", "阴影", "陰影");
        put("Off", "Désactivé", "Aus", "Disattivato", "Desativado", "オフ", "끔", "关", "關");
        put("Fullscreen (stretch)", "Plein écran (étirer)", "Vollbild (strecken)", "Schermo intero (allarga)", "Tela cheia (esticar)", "全画面（引き伸ばし）", "전체 화면 (늘이기)", "全屏（拉伸）", "全螢幕（拉伸）");
        put("On", "Activé", "An", "Attivato", "Ativado", "オン", "켬", "开", "開");
        put("FPS counter", "Compteur de FPS", "FPS-Anzeige", "Contatore FPS", "Contador de FPS", "FPS表示", "FPS 표시",
                "帧率显示", "FPS 顯示");
        put("On-screen controls", "Commandes tactiles", "Touch-Steuerung", "Comandi touch", "Controles na tela",
                "タッチ操作", "터치 컨트롤", "触屏按键", "觸控按鍵");
        put("Button size", "Taille des boutons", "Tastengröße", "Dimensione dei pulsanti", "Tamanho dos botões",
                "ボタンの大きさ", "버튼 크기", "按键大小", "按鍵大小");
        put("Small", "Petite", "Klein", "Piccola", "Pequeno", "小", "작게", "小", "小");
        put("Normal", "Normale", "Normal", "Normale", "Normal", "標準", "보통", "标准", "標準");
        put("Large", "Grande", "Groß", "Grande", "Grande", "大", "크게", "大", "大");
        put("Button opacity", "Opacité des boutons", "Deckkraft der Tasten", "Opacità dei pulsanti",
                "Opacidade dos botões", "ボタンの不透明度", "버튼 불투명도", "按键不透明度", "按鍵不透明度");
        put("Controls position", "Position des commandes", "Position der Steuerung", "Posizione dei comandi",
                "Posição dos controles", "操作ボタンの配置", "컨트롤 위치", "按键位置", "按鍵位置");
        put("Move", "Déplacer", "Verschieben", "Sposta", "Mover", "移動", "이동", "移动", "移動");
        put("Saves", "Sauvegardes", "Spielstände", "Salvataggi", "Jogos salvos", "セーブデータ", "저장 데이터", "存档",
                "存檔");
        put("Import", "Importer", "Importieren", "Importa", "Importar", "インポート", "가져오기", "导入", "匯入");
        put("Export", "Exporter", "Exportieren", "Esporta", "Exportar", "エクスポート", "내보내기", "导出", "匯出");
        put("Mods", "Mods", "Mods", "Mod", "Mods", "MOD", "모드", "模组", "模組");
        put("Install", "Installer", "Installieren", "Installa", "Instalar", "インストール", "설치", "安装", "安裝");
        put("Manage", "Gérer", "Verwalten", "Gestisci", "Gerenciar", "管理", "관리", "管理", "管理");
        put("Bloom (experimental)", "Bloom (expérimental)", "Bloom (experimentell)", "Bloom (sperimentale)",
                "Bloom (experimental)", "ブルーム（実験的）", "블룸 (실험적)", "泛光（实验性）", "泛光（實驗性）");
        put("On (normal)", "Activé (normal)", "An (normal)", "Attivato (normale)", "Ativado (normal)", "オン（通常）",
                "켬 (기본)", "开（正常）", "開（正常）");
        put("Touch stick sensitivity", "Sensibilité du stick tactile", "Empfindlichkeit des Touch-Sticks",
                "Sensibilità della levetta touch", "Sensibilidade do analógico na tela", "タッチスティックの感度",
                "터치 스틱 감도", "触屏摇杆灵敏度", "觸控搖桿靈敏度");
        put("Touch camera sensitivity", "Sensibilité de la caméra tactile", "Empfindlichkeit der Touch-Kamera",
                "Sensibilità della telecamera touch", "Sensibilidade da câmera na tela", "タッチカメラの感度",
                "터치 카메라 감도", "触屏视角灵敏度", "觸控視角靈敏度");
        put("Physical gamepad buttons", "Boutons de la manette", "Tasten des Controllers", "Pulsanti del controller",
                "Botões do controle físico", "コントローラーのボタン配置", "게임패드 버튼 배치", "实体手柄按键",
                "實體手把按鍵");
        put("Nintendo (A right, B down)", "Nintendo (A à droite, B en bas)", "Nintendo (A rechts, B unten)",
                "Nintendo (A a destra, B in basso)", "Nintendo (A à direita, B embaixo)", "Nintendo（A が右、B が下）",
                "Nintendo (A 오른쪽, B 아래)", "Nintendo（A 在右，B 在下）", "Nintendo（A 在右，B 在下）");
        put("Xbox / PC (A down, B right)", "Xbox / PC (A en bas, B à droite)", "Xbox / PC (A unten, B rechts)",
                "Xbox / PC (A in basso, B a destra)", "Xbox / PC (A embaixo, B à direita)", "Xbox / PC（A が下、B が右）",
                "Xbox / PC (A 아래, B 오른쪽)", "Xbox / PC（A 在下，B 在右）", "Xbox / PC（A 在下，B 在右）");
        put("Debug logs", "Journaux de débogage", "Debug-Protokolle", "Log di debug", "Registros de depuração",
                "デバッグログ", "디버그 로그", "调试日志", "除錯記錄");
        put("Export logs", "Exporter les journaux", "Protokolle exportieren", "Esporta i log", "Exportar registros",
                "ログをエクスポート", "로그 내보내기", "导出日志", "匯出記錄");
        // Status and the play button
        put("All-files access is needed to read the game data.",
                "L'accès à tous les fichiers est nécessaire pour lire les données du jeu.",
                "Für die Spieldaten wird Zugriff auf alle Dateien benötigt.",
                "Serve l'accesso a tutti i file per leggere i dati del gioco.",
                "É preciso o acesso a todos os arquivos para ler os dados do jogo.",
                "ゲームデータを読み込むには、すべてのファイルへのアクセス許可が必要です。",
                "게임 데이터를 읽으려면 모든 파일 액세스 권한이 필요합니다.",
                "需要“所有文件访问权限”才能读取游戏数据。",
                "需要「所有檔案存取權」才能讀取遊戲資料。");
        put("Grant access", "Autoriser l'accès", "Zugriff erlauben", "Concedi l'accesso", "Permitir acesso",
                "アクセスを許可", "액세스 허용", "授予权限", "授予權限");
        put("Game data missing in Internal storage/%s: %s. Prepare it on the PC from your own copy of the game and copy it there.",
                "Données du jeu manquantes dans Stockage interne/%s : %s. Préparez-les sur le PC à partir de votre propre copie du jeu et copiez-les là.",
                "Spieldaten fehlen in Interner Speicher/%s: %s. Bereite sie am PC aus deiner eigenen Kopie des Spiels vor und kopiere sie dorthin.",
                "Mancano dati del gioco in Memoria interna/%s: %s. Preparali sul PC dalla tua copia del gioco e copiali lì.",
                "Faltam dados do jogo em Armazenamento interno/%s: %s. Prepare-os no PC a partir da sua própria cópia do jogo e copie-os para lá.",
                "内部ストレージ/%s にゲームデータがありません：%s。ご自身のゲームから PC で用意し、そこへコピーしてください。",
                "내부 저장소/%s 에 게임 데이터가 없습니다: %s. 본인 소유 게임으로 PC에서 준비한 뒤 그곳에 복사하세요.",
                "内部存储/%s 中缺少游戏数据：%s。请在电脑上用你自己的游戏副本准备好，再复制到那里。",
                "內部儲存空間/%s 中缺少遊戲資料：%s。請在電腦上用你自己的遊戲副本準備好，再複製到那裡。");
        put("Internal storage/%s/exefs/main is not NieR Automata 1.0.3 (build %s). Copy the exefs of the game's version 1.0.3.",
                "Stockage interne/%s/exefs/main n'est pas NieR Automata 1.0.3 (build %s). Copiez l'exefs de la version 1.0.3 du jeu.",
                "Interner Speicher/%s/exefs/main ist nicht NieR Automata 1.0.3 (Build %s). Kopiere das exefs der Spielversion 1.0.3.",
                "Memoria interna/%s/exefs/main non è NieR Automata 1.0.3 (build %s). Copia l'exefs della versione 1.0.3 del gioco.",
                "Armazenamento interno/%s/exefs/main não é NieR Automata 1.0.3 (build %s). Copie o exefs da versão 1.0.3 do jogo.",
                "内部ストレージ/%s/exefs/main は NieR Automata 1.0.3 ではありません（ビルド %s）。ゲームのバージョン 1.0.3 の exefs をコピーしてください。",
                "내부 저장소/%s/exefs/main 은(는) NieR Automata 1.0.3 이 아닙니다(빌드 %s). 게임 버전 1.0.3 의 exefs 를 복사하세요.",
                "内部存储/%s/exefs/main 不是 NieR Automata 1.0.3（版本 %s）。请复制游戏 1.0.3 版本的 exefs。",
                "內部儲存空間/%s/exefs/main 不是 NieR Automata 1.0.3（版本 %s）。請複製遊戲 1.0.3 版本的 exefs。");
        put("Look again", "Chercher à nouveau", "Erneut suchen", "Cerca di nuovo", "Procurar de novo", "再検索",
                "다시 찾기", "重新查找", "重新尋找");
        put("Data ready: %s", "Données prêtes : %s", "Daten bereit: %s", "Dati pronti: %s", "Dados prontos: %s",
                "データ準備完了：%s", "데이터 준비됨: %s", "数据就绪：%s", "資料就緒：%s");
        put("Play", "Jouer", "Spielen", "Gioca", "Jogar", "プレイ", "플레이", "开始游戏", "開始遊戲");
        // Saves
        put("Import saves", "Importer des sauvegardes", "Spielstände importieren", "Importa salvataggi",
                "Importar jogos salvos", "セーブデータのインポート", "저장 데이터 가져오기", "导入存档", "匯入存檔");
        put("Pick a ZIP file or a folder with NieR Automata saves (the GameData and SlotData_0, SlotData_1... files, as "
                        + "JKSV or Checkpoint export them, or the ZIP the Export button makes). Your current saves are "
                        + "copied aside first.",
                "Choisissez un fichier ZIP ou un dossier avec des sauvegardes de NieR Automata (les fichiers GameData et "
                        + "SlotData_0, SlotData_1..., tels que JKSV ou Checkpoint les exportent, ou le ZIP créé par le "
                        + "bouton Exporter). Vos sauvegardes actuelles sont copiées à part avant.",
                "Wähle eine ZIP-Datei oder einen Ordner mit Spielständen von NieR Automata (die Dateien GameData und "
                        + "SlotData_0, SlotData_1..., wie JKSV oder Checkpoint sie exportieren, oder die ZIP-Datei der "
                        + "Exportieren-Taste). Deine aktuellen Spielstände werden vorher gesichert.",
                "Scegli un file ZIP o una cartella con salvataggi di NieR Automata (i file GameData e SlotData_0, "
                        + "SlotData_1..., come li esportano JKSV o Checkpoint, o lo ZIP creato dal pulsante Esporta). "
                        + "Prima viene fatta una copia dei salvataggi attuali.",
                "Escolha um arquivo ZIP ou uma pasta com jogos salvos de NieR Automata (os arquivos GameData e "
                        + "SlotData_0, SlotData_1..., como o JKSV ou o Checkpoint exportam, ou o ZIP que o botão "
                        + "Exportar cria). Antes, é feita uma cópia dos seus jogos salvos atuais.",
                "NieR Automata のセーブデータが入った ZIP ファイルまたはフォルダを選んでください（JKSV や Checkpoint "
                        + "が書き出す GameData と SlotData_0、SlotData_1…のファイル、またはエクスポートボタンで作った "
                        + "ZIP）。先に現在のセーブデータのコピーを取ります。",
                "NieR Automata 저장 데이터가 든 ZIP 파일이나 폴더를 선택하세요 (JKSV나 Checkpoint가 내보내는 GameData "
                        + "및 SlotData_0, SlotData_1... 파일, 또는 내보내기 버튼으로 만든 ZIP). 먼저 현재 저장 데이터를 "
                        + "따로 복사해 둡니다.",
                "请选择包含 NieR Automata 存档的 ZIP 文件或文件夹（JKSV 或 Checkpoint 导出的 GameData 和 "
                        + "SlotData_0、SlotData_1… 文件，或“导出”按钮生成的 ZIP）。会先备份你当前的存档。",
                "請選擇包含 NieR Automata 存檔的 ZIP 檔案或資料夾（JKSV 或 Checkpoint 匯出的 GameData 和 "
                        + "SlotData_0、SlotData_1… 檔案，或「匯出」按鈕產生的 ZIP）。會先備份你目前的存檔。");
        put("ZIP file", "Fichier ZIP", "ZIP-Datei", "File ZIP", "Arquivo ZIP", "ZIP ファイル", "ZIP 파일", "ZIP 文件",
                "ZIP 檔案");
        put("Folder", "Dossier", "Ordner", "Cartella", "Pasta", "フォルダ", "폴더", "文件夹", "資料夾");
        put("Cancel", "Annuler", "Abbrechen", "Annulla", "Cancelar", "キャンセル", "취소", "取消", "取消");
        put("No saves found", "Aucune sauvegarde trouvée", "Keine Spielstände gefunden", "Nessun salvataggio trovato",
                "Nenhum jogo salvo encontrado", "セーブデータが見つかりません", "저장 데이터를 찾을 수 없음", "未找到存档",
                "找不到存檔");
        put("No NieR Automata saves there: look for the GameData or SlotData_0, SlotData_1... files.",
                "Aucune sauvegarde de NieR Automata ici : cherchez les fichiers GameData ou SlotData_0, SlotData_1...",
                "Dort sind keine Spielstände von NieR Automata: suche die Dateien GameData oder SlotData_0, SlotData_1...",
                "Lì non ci sono salvataggi di NieR Automata: cerca i file GameData o SlotData_0, SlotData_1...",
                "Não há jogos salvos de NieR Automata aí: procure os arquivos GameData ou SlotData_0, SlotData_1...",
                "そこには NieR Automata のセーブデータがありません。GameData または SlotData_0、SlotData_1…のファイルを探してください。",
                "그곳에는 NieR Automata 저장 데이터가 없습니다. GameData 또는 SlotData_0, SlotData_1... 파일을 찾으세요.",
                "那里没有 NieR Automata 的存档：请找 GameData 或 SlotData_0、SlotData_1… 文件。",
                "那裡沒有 NieR Automata 的存檔：請找 GameData 或 SlotData_0、SlotData_1… 檔案。");
        put("\n\nYour previous saves are in user/%s.", "\n\nVos sauvegardes précédentes sont dans user/%s.",
                "\n\nDeine bisherigen Spielstände liegen in user/%s.", "\n\nI salvataggi precedenti sono in user/%s.",
                "\n\nSeus jogos salvos anteriores estão em user/%s.", "\n\n以前のセーブデータは user/%s にあります。",
                "\n\n이전 저장 데이터는 user/%s 에 있습니다.", "\n\n你之前的存档在 user/%s。", "\n\n你之前的存檔在 user/%s。");
        put("Saves imported", "Sauvegardes importées", "Spielstände importiert", "Salvataggi importati",
                "Jogos salvos importados", "セーブデータをインポートしました", "저장 데이터를 가져왔습니다", "存档已导入",
                "存檔已匯入");
        put("%d files (%s). The game loads them at its next start.",
                "%d fichiers (%s). Le jeu les charge à son prochain démarrage.",
                "%d Dateien (%s). Das Spiel lädt sie beim nächsten Start.",
                "%d file (%s). Il gioco li carica al prossimo avvio.",
                "%d arquivos (%s). O jogo os carrega na próxima vez que iniciar.",
                "%d 個のファイル（%s）。次回の起動時にゲームが読み込みます。",
                "파일 %d개 (%s). 다음 실행 때 게임이 불러옵니다.",
                "%d 个文件（%s）。游戏下次启动时会读取。",
                "%d 個檔案（%s）。遊戲下次啟動時會讀取。");
        put("Import failed", "Échec de l'importation", "Import fehlgeschlagen", "Importazione non riuscita",
                "Falha na importação", "インポートできませんでした", "가져오기 실패", "导入失败", "匯入失敗");
        put("No saves", "Aucune sauvegarde", "Keine Spielstände", "Nessun salvataggio", "Nenhum jogo salvo",
                "セーブデータなし", "저장 데이터 없음", "没有存档", "沒有存檔");
        put("There are no saves yet.", "Il n'y a pas encore de sauvegarde.", "Es gibt noch keine Spielstände.",
                "Non ci sono ancora salvataggi.", "Ainda não há jogos salvos.", "まだセーブデータがありません。",
                "아직 저장 데이터가 없습니다.", "还没有存档。", "還沒有存檔。");
        put("Saves exported", "Sauvegardes exportées", "Spielstände exportiert", "Salvataggi esportati",
                "Jogos salvos exportados", "セーブデータをエクスポートしました", "저장 데이터를 내보냈습니다", "存档已导出",
                "存檔已匯出");
        put("Saved to Download/%s. The Import button takes it back as is.",
                "Enregistré dans Download/%s. Le bouton Importer le reprend tel quel.",
                "Gespeichert in Download/%s. Die Importieren-Taste übernimmt die Datei unverändert.",
                "Salvato in Download/%s. Il pulsante Importa lo riprende così com'è.",
                "Salvo em Download/%s. O botão Importar o aceita como está.",
                "Download/%s に保存しました。インポートボタンでそのまま読み込めます。",
                "Download/%s 에 저장했습니다. 가져오기 버튼으로 그대로 불러올 수 있습니다.",
                "已保存到 Download/%s。可用“导入”按钮原样导入。",
                "已儲存到 Download/%s。可用「匯入」按鈕原樣匯入。");
        put("Export failed", "Échec de l'exportation", "Export fehlgeschlagen", "Esportazione non riuscita",
                "Falha na exportação", "エクスポートできませんでした", "내보내기 실패", "导出失败", "匯出失敗");
        // Mods
        put("Not a mod", "Ce n'est pas un mod", "Kein Mod", "Non è una mod", "Não é um mod", "MOD ではありません",
                "모드가 아닙니다", "不是模组", "不是模組");
        put("Choose a mod's ZIP file (with a romfs folder inside) or a .cpk file that replaces one of the game's.",
                "Choisissez le fichier ZIP d'un mod (avec un dossier romfs dedans) ou un fichier .cpk qui remplace un de ceux du jeu.",
                "Wähle die ZIP-Datei eines Mods (mit einem romfs-Ordner darin) oder eine .cpk-Datei, die eine des Spiels ersetzt.",
                "Scegli il file ZIP di una mod (con una cartella romfs dentro) o un file .cpk che sostituisce uno di quelli del gioco.",
                "Escolha o arquivo ZIP de um mod (com uma pasta romfs dentro) ou um arquivo .cpk que substitua um dos do jogo.",
                "MOD の ZIP ファイル（中に romfs フォルダがあるもの）か、ゲームのファイルを置き換える .cpk ファイルを選んでください。",
                "모드의 ZIP 파일(안에 romfs 폴더가 있는 것) 또는 게임 파일 하나를 대체하는 .cpk 파일을 선택하세요.",
                "请选择模组的 ZIP 文件（内含 romfs 文件夹），或替换游戏文件的 .cpk 文件。",
                "請選擇模組的 ZIP 檔案（內含 romfs 資料夾），或替換遊戲檔案的 .cpk 檔案。");
        put("Installing the mod…", "Installation du mod…", "Mod wird installiert…", "Installazione della mod…",
                "Instalando o mod…", "MOD をインストール中…", "모드 설치 중…", "正在安装模组…", "正在安裝模組…");
        put("Install failed", "Échec de l'installation", "Installation fehlgeschlagen", "Installazione non riuscita",
                "Falha na instalação", "インストールできませんでした", "설치 실패", "安装失败", "安裝失敗");
        put("That ZIP has no romfs folder. A mod holds the files it replaces under romfs/, with the same paths as the game's.",
                "Ce ZIP n'a pas de dossier romfs. Un mod contient les fichiers qu'il remplace sous romfs/, avec les mêmes chemins que ceux du jeu.",
                "Diese ZIP-Datei hat keinen romfs-Ordner. Ein Mod enthält die Dateien, die er ersetzt, unter romfs/, mit denselben Pfaden wie im Spiel.",
                "Quello ZIP non ha una cartella romfs. Una mod contiene i file che sostituisce in romfs/, con gli stessi percorsi di quelli del gioco.",
                "Esse ZIP não tem uma pasta romfs. Um mod traz os arquivos que substitui em romfs/, com os mesmos caminhos que os do jogo.",
                "その ZIP には romfs フォルダがありません。MOD は置き換えるファイルを、ゲームと同じパスで romfs/ の下に持っています。",
                "그 ZIP에는 romfs 폴더가 없습니다. 모드는 대체할 파일을 게임과 같은 경로로 romfs/ 아래에 담고 있습니다.",
                "该 ZIP 没有 romfs 文件夹。模组应把要替换的文件放在 romfs/ 下，路径与游戏文件相同。",
                "該 ZIP 沒有 romfs 資料夾。模組應把要替換的檔案放在 romfs/ 下，路徑與遊戲檔案相同。");
        put("Mod installed", "Mod installé", "Mod installiert", "Mod installata", "Mod instalado", "MOD をインストールしました",
                "모드를 설치했습니다", "模组已安装", "模組已安裝");
        put("%d files in mods/%s. The game loads it at its next start.",
                "%d fichiers dans mods/%s. Le jeu le charge à son prochain démarrage.",
                "%d Dateien in mods/%s. Das Spiel lädt ihn beim nächsten Start.",
                "%d file in mods/%s. Il gioco la carica al prossimo avvio.",
                "%d arquivos em mods/%s. O jogo o carrega na próxima vez que iniciar.",
                "%d 個のファイルを mods/%s に入れました。次回の起動時にゲームが読み込みます。",
                "파일 %d개를 mods/%s 에 넣었습니다. 다음 실행 때 게임이 불러옵니다.",
                "%d 个文件已放入 mods/%s。游戏下次启动时会加载。",
                "%d 個檔案已放入 mods/%s。遊戲下次啟動時會載入。");
        put("No mods", "Aucun mod", "Keine Mods", "Nessuna mod", "Nenhum mod", "MOD がありません", "모드 없음", "没有模组",
                "沒有模組");
        put("No mods yet. Install a ZIP or a .cpk, or copy each mod to Internal storage/%s/mods/<name>/romfs/ (the same paths as the game's files).",
                "Pas encore de mod. Installez un ZIP ou un .cpk, ou copiez chaque mod dans Stockage interne/%s/mods/<nom>/romfs/ (avec les mêmes chemins que les fichiers du jeu).",
                "Noch keine Mods. Installiere eine ZIP- oder .cpk-Datei oder kopiere jeden Mod nach Interner Speicher/%s/mods/<Name>/romfs/ (mit denselben Pfaden wie die Dateien des Spiels).",
                "Ancora nessuna mod. Installa uno ZIP o un .cpk, oppure copia ogni mod in Memoria interna/%s/mods/<nome>/romfs/ (con gli stessi percorsi dei file del gioco).",
                "Ainda não há mods. Instale um ZIP ou um .cpk, ou copie cada mod para Armazenamento interno/%s/mods/<nome>/romfs/ (com os mesmos caminhos que os arquivos do jogo).",
                "まだ MOD がありません。ZIP か .cpk をインストールするか、各 MOD を 内部ストレージ/%s/mods/<名前>/romfs/ にコピーしてください（ゲームのファイルと同じパスで）。",
                "아직 모드가 없습니다. ZIP이나 .cpk를 설치하거나, 각 모드를 내부 저장소/%s/mods/<이름>/romfs/ 에 복사하세요 (게임 파일과 같은 경로로).",
                "还没有模组。请安装 ZIP 或 .cpk，或把每个模组复制到 内部存储/%s/mods/<名称>/romfs/（路径与游戏文件相同）。",
                "還沒有模組。請安裝 ZIP 或 .cpk，或把每個模組複製到 內部儲存空間/%s/mods/<名稱>/romfs/（路徑與遊戲檔案相同）。");
        put("Active mods", "Mods actifs", "Aktive Mods", "Mod attive", "Mods ativos", "有効な MOD", "활성 모드", "启用的模组",
                "啟用的模組");
        put("Apply", "Appliquer", "Übernehmen", "Applica", "Aplicar", "適用", "적용", "应用", "套用");
        put("A mod folder could not be renamed: %s", "Impossible de renommer le dossier d'un mod : %s",
                "Ein Mod-Ordner konnte nicht umbenannt werden: %s", "Impossibile rinominare la cartella di una mod: %s",
                "Não foi possível renomear a pasta de um mod: %s", "MOD フォルダの名前を変更できませんでした：%s",
                "모드 폴더 이름을 바꿀 수 없습니다: %s", "无法重命名模组文件夹：%s", "無法重新命名模組資料夾：%s");
        // Logs
        put("Logs exported to Download/%s", "Journaux exportés dans Download/%s", "Protokolle nach Download/%s exportiert",
                "Log esportati in Download/%s", "Registros exportados para Download/%s",
                "ログを Download/%s に書き出しました", "로그를 Download/%s 에 내보냈습니다", "日志已导出到 Download/%s",
                "記錄已匯出到 Download/%s");
        put("Share logs", "Partager les journaux", "Protokolle teilen", "Condividi i log", "Compartilhar registros",
                "ログを共有", "로그 공유", "分享日志", "分享記錄");
        put("Could not export the logs: %s", "Impossible d'exporter les journaux : %s",
                "Protokolle konnten nicht exportiert werden: %s", "Impossibile esportare i log: %s",
                "Não foi possível exportar os registros: %s", "ログを書き出せませんでした：%s",
                "로그를 내보낼 수 없습니다: %s", "无法导出日志：%s", "無法匯出記錄：%s");
        // The controls editor
        put("Drag the controls where you want them.\nThe d-pad and A B X Y move together. Red: they touch.",
                "Faites glisser les commandes où vous voulez.\nLa croix et A B X Y bougent ensemble. En rouge : elles se touchent.",
                "Ziehe die Tasten an die gewünschte Stelle.\nSteuerkreuz und A B X Y bewegen sich gemeinsam. Rot: sie berühren sich.",
                "Trascina i comandi dove vuoi.\nLa croce direzionale e A B X Y si muovono insieme. In rosso: si toccano.",
                "Arraste os controles para onde quiser.\nO direcional e A B X Y se movem juntos. Em vermelho: estão se tocando.",
                "操作ボタンを好きな位置へドラッグしてください。\n十字ボタンと A B X Y は一緒に動きます。赤：重なっています。",
                "컨트롤을 원하는 곳으로 끌어 놓으세요.\n방향 버튼과 A B X Y는 함께 움직입니다. 빨간색: 서로 닿음.",
                "把按键拖到你想要的位置。\n十字键和 A B X Y 会一起移动。红色：互相重叠。",
                "把按鍵拖到你想要的位置。\n十字鍵和 A B X Y 會一起移動。紅色：互相重疊。");
        put("Reset", "Réinitialiser", "Zurücksetzen", "Ripristina", "Restaurar", "リセット", "초기화", "重置", "重設");
        put("Save", "Enregistrer", "Speichern", "Salva", "Salvar", "保存", "저장", "保存", "儲存");
        put("Controls saved", "Commandes enregistrées", "Steuerung gespeichert", "Comandi salvati", "Controles salvos",
                "保存しました", "저장했습니다", "已保存", "已儲存");
        put("Could not write config.txt", "Impossible d'écrire config.txt", "config.txt konnte nicht geschrieben werden",
                "Impossibile scrivere config.txt", "Não foi possível gravar config.txt", "config.txt を書き込めませんでした",
                "config.txt 를 쓸 수 없습니다", "无法写入 config.txt", "無法寫入 config.txt");
        // The icon
        put("Icon", "Icône", "Symbol", "Icona", "Ícone", "アイコン", "아이콘", "图标", "圖示");
        put("Choose a picture", "Choisir une image", "Bild auswählen", "Scegli un'immagine", "Escolher uma imagem", "画像を選択", "이미지 선택", "选择图片", "選擇圖片");
        put("Your home screen does not support shortcuts.", "Votre écran d'accueil ne prend pas en charge les raccourcis.", "Dein Startbildschirm unterstützt keine Verknüpfungen.", "La tua schermata Home non supporta le scorciatoie.", "Sua tela inicial não aceita atalhos.", "ホーム画面がショートカットに対応していません。", "홈 화면이 바로가기를 지원하지 않습니다.", "你的主屏幕不支持快捷方式。", "你的主畫面不支援捷徑。");
        put("Accept the prompt to put the shortcut with your picture on the home screen. The icon in the app drawer stays: Android does not let an app change its own.", "Acceptez la demande pour placer le raccourci avec votre image sur l'écran d'accueil. L'icône du tiroir d'applications ne change pas : Android ne laisse pas une app changer la sienne.", "Bestätige die Anfrage, um die Verknüpfung mit deinem Bild auf den Startbildschirm zu legen. Das Symbol in der App-Übersicht bleibt: Android lässt Apps ihr eigenes nicht ändern.", "Accetta la richiesta per mettere la scorciatoia con la tua immagine nella schermata Home. L'icona nel cassetto delle app resta: Android non lascia che un'app cambi la propria.", "Aceite o aviso para colocar o atalho com sua imagem na tela inicial. O ícone na gaveta de apps continua: o Android não deixa um app mudar o próprio.", "表示される確認を許可すると、画像付きのショートカットがホーム画面に追加されます。アプリ一覧のアイコンは変わりません（Android ではアプリが自分のアイコンを変更できません）。", "확인 창을 수락하면 이미지가 들어간 바로가기가 홈 화면에 추가됩니다. 앱 서랍의 아이콘은 그대로입니다(Android 는 앱이 자신의 아이콘을 바꾸지 못하게 합니다).", "接受提示即可将带有你图片的快捷方式放到主屏幕。应用抽屉中的图标不变：Android 不允许应用更改自己的图标。", "接受提示即可將帶有你圖片的捷徑放到主畫面。應用程式抽屜中的圖示不變：Android 不允許應用程式更改自己的圖示。");
        put("That picture could not be used.", "Impossible d'utiliser cette image.", "Dieses Bild kann nicht verwendet werden.", "Impossibile usare quell'immagine.", "Não foi possível usar essa imagem.", "その画像は使用できませんでした。", "그 이미지를 사용할 수 없습니다.", "无法使用该图片。", "無法使用該圖片。");
    }
}
