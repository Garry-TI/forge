package forge.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.Timer;

import forge.localinstance.properties.ForgeConstants;

/**
 * Manages animated card art loops for cards that have animation frame assets.
 */
public final class CardAnimationManager {

    private static final CardAnimationManager INSTANCE = new CardAnimationManager();

    private final Map<String, BufferedImage[]> cardAnimations = new ConcurrentHashMap<>();
    private final Set<JComponent> activeComponents = Collections.newSetFromMap(new WeakHashMap<>());
    private int currentFrameIndex = 0;
    private Timer animationTimer;
    private boolean initialized = false;

    private CardAnimationManager() {
    }

    public static CardAnimationManager getInstance() {
        return INSTANCE;
    }

    private List<File> getCandidateDirectories() {
        List<File> dirs = new ArrayList<>();
        try {
            if (ForgeConstants.RES_DIR != null) {
                dirs.add(new File(ForgeConstants.RES_DIR, "animated_cards"));
            }
            if (ForgeConstants.CACHE_CARD_PICS_DIR != null) {
                dirs.add(new File(ForgeConstants.CACHE_CARD_PICS_DIR, "animated_cards"));
            }
            if (ForgeConstants.CACHE_DIR != null) {
                dirs.add(new File(ForgeConstants.CACHE_DIR, "animated_cards"));
            }
        } catch (Throwable ignored) {
        }
        dirs.add(new File("res/animated_cards"));
        dirs.add(new File("../res/animated_cards"));
        dirs.add(new File("../../res/animated_cards"));
        dirs.add(new File("forge-gui/res/animated_cards"));
        dirs.add(new File("forge-installer/target/res/animated_cards"));
        dirs.add(new File("forge-installer/target/forge-installer-2.0.15-SNAPSHOT/res/animated_cards"));
        dirs.add(new File("Forge.app/Contents/Resources/res/animated_cards"));
        dirs.add(new File("Forge.app/Contents/MacOS/res/animated_cards"));
        dirs.add(new File("Forge.app/res/animated_cards"));
        dirs.add(new File("../Resources/res/animated_cards"));
        dirs.add(new File("D:/projects/forge/res/animated_cards"));
        dirs.add(new File("D:/projects/forge/forge-gui/res/animated_cards"));
        dirs.add(new File("D:/projects/forge/forge-installer/target/forge-installer-2.0.15-SNAPSHOT/res/animated_cards"));
        return dirs;
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        for (File dir : getCandidateDirectories()) {
            scanDirectory(dir);
        }

        if (!cardAnimations.isEmpty()) {
            System.out.println("[CardAnimationManager] Loaded animated cards: " + cardAnimations.keySet());
            ensureTimerRunning();
        }
    }

    private synchronized void ensureTimerRunning() {
        if (animationTimer == null && !cardAnimations.isEmpty()) {
            // 24 FPS timer (~41ms per tick)
            animationTimer = new Timer(41, e -> {
                currentFrameIndex++;
                synchronized (activeComponents) {
                    for (JComponent comp : activeComponents) {
                        if (comp != null && comp.isShowing()) {
                            comp.repaint();
                        }
                    }
                }
            });
            animationTimer.start();
        }
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private void scanDirectory(File animDir) {
        if (animDir == null || !animDir.exists() || !animDir.isDirectory()) {
            return;
        }

        File[] folders = animDir.listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }

        for (File folder : folders) {
            String folderName = folder.getName().toLowerCase().trim();
            String normKey = normalize(folder.getName());
            boolean loaded = loadCardFolder(folder, folderName, normKey);
            if (!loaded) {
                // Might be a set folder (e.g. animDir/FIN/Jumbo Cactuar_191)
                File[] subFolders = folder.listFiles(File::isDirectory);
                if (subFolders != null) {
                    for (File sub : subFolders) {
                        String setCardSlash = (folderName + "/" + sub.getName()).toLowerCase().trim();
                        String setCardUnder = (folderName + "_" + sub.getName()).toLowerCase().trim();
                        String subName = sub.getName().toLowerCase().trim();
                        String subNorm = normalize(sub.getName());
                        loadCardFolder(sub, setCardSlash, setCardUnder);
                        if (cardAnimations.containsKey(setCardSlash)) {
                            BufferedImage[] f = cardAnimations.get(setCardSlash);
                            cardAnimations.putIfAbsent(subName, f);
                            cardAnimations.putIfAbsent(subNorm, f);
                        }
                    }
                }
            }
        }
    }

    private boolean loadCardFolder(File folder, String cardName, String normKey) {
        File[] frameFiles = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg");
        });

        if (frameFiles == null || frameFiles.length == 0) {
            return false;
        }

        Arrays.sort(frameFiles, Comparator.comparing(File::getName));
        BufferedImage[] frames = new BufferedImage[frameFiles.length];
        int loaded = 0;

        for (int i = 0; i < frameFiles.length; i++) {
            try {
                frames[i] = ImageIO.read(frameFiles[i]);
                if (frames[i] != null) {
                    loaded++;
                }
            } catch (Exception ex) {
                System.err.println("[CardAnimationManager] Error loading frame " + frameFiles[i].getName() + ": " + ex.getMessage());
            }
        }

        if (loaded > 0) {
            cardAnimations.put(cardName, frames);
            cardAnimations.put(normKey, frames);
            System.out.println("[CardAnimationManager] Registered animation for card '" + folder.getName() + "' with " + loaded + " frames.");
            return true;
        }
        return false;
    }

    private synchronized boolean tryLoadCard(String rawName, String key, String normKey) {
        if (cardAnimations.containsKey(key) || cardAnimations.containsKey(normKey)) {
            return true;
        }

        for (File baseDir : getCandidateDirectories()) {
            if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory()) {
                continue;
            }

            // Direct folder match
            File directFolder = new File(baseDir, rawName);
            if (directFolder.isDirectory() && loadCardFolder(directFolder, key, normKey)) {
                ensureTimerRunning();
                return true;
            }

            File directFolderLower = new File(baseDir, key);
            if (directFolderLower.isDirectory() && loadCardFolder(directFolderLower, key, normKey)) {
                ensureTimerRunning();
                return true;
            }

            // Scan subdirectories for matching name or normalized key
            File[] subs = baseDir.listFiles(File::isDirectory);
            if (subs != null) {
                for (File sub : subs) {
                    String subName = sub.getName().toLowerCase().trim();
                    String subNorm = normalize(sub.getName());
                    if (subName.equals(key) || subNorm.equals(normKey)) {
                        if (loadCardFolder(sub, key, normKey)) {
                            ensureTimerRunning();
                            return true;
                        }
                    }

                    // Check 1 level down inside subfolder (e.g. baseDir/FIN/...)
                    File subDirect = new File(sub, rawName);
                    if (subDirect.isDirectory() && loadCardFolder(subDirect, key, normKey)) {
                        ensureTimerRunning();
                        return true;
                    }
                    File[] subSubs = sub.listFiles(File::isDirectory);
                    if (subSubs != null) {
                        for (File subSub : subSubs) {
                            String ssName = subSub.getName().toLowerCase().trim();
                            String ssNorm = normalize(subSub.getName());
                            String slashKey = (subName + "/" + ssName).toLowerCase().trim();
                            String underKey = (subName + "_" + ssName).toLowerCase().trim();
                            if (ssName.equals(key) || ssNorm.equals(normKey)
                                    || slashKey.equals(key) || underKey.equals(key)) {
                                if (loadCardFolder(subSub, key, normKey)) {
                                    ensureTimerRunning();
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasAnimation(String cardName) {
        if (cardName == null) {
            return false;
        }
        if (!INSTANCE.initialized) {
            INSTANCE.initialize();
        }
        String key = cardName.toLowerCase().trim();
        String normKey = normalize(key);
        if (INSTANCE.cardAnimations.containsKey(key) || INSTANCE.cardAnimations.containsKey(normKey)) {
            return true;
        }
        for (String k : INSTANCE.cardAnimations.keySet()) {
            if (k.startsWith(key + "_") || k.startsWith(key + "1")
                    || k.endsWith("/" + key) || k.endsWith("/" + key + "1")
                    || k.endsWith("_" + key) || k.endsWith("_" + key + "1")) {
                return true;
            }
        }
        return INSTANCE.tryLoadCard(cardName, key, normKey);
    }

    public static boolean hasAnimation(String cardName, int artIndex, String collectorNumber) {
        return hasAnimation(cardName, null, artIndex, collectorNumber);
    }

    public static boolean hasAnimation(String cardName, String edition, int artIndex, String collectorNumber) {
        if (cardName == null) {
            return false;
        }
        String ed = edition != null ? edition.trim().toLowerCase() : "";
        String col = (collectorNumber != null && !collectorNumber.isEmpty() && !"N.A.".equalsIgnoreCase(collectorNumber))
                ? collectorNumber.trim() : null;

        if (!ed.isEmpty() && col != null) {
            if (hasAnimation(ed + "/" + cardName + "_" + col)
                    || hasAnimation(ed + "_" + cardName + "_" + col)
                    || hasAnimation(ed + "/" + col)
                    || hasAnimation(ed + "_" + col)) {
                return true;
            }
        }
        if (col != null) {
            String colKey = cardName + "_" + col;
            if (hasAnimation(colKey)) {
                return true;
            }
        }

        int art = artIndex > 0 ? artIndex : 1;
        if (!ed.isEmpty()) {
            if (hasAnimation(ed + "/" + cardName + art)
                    || hasAnimation(ed + "_" + cardName + art)) {
                return true;
            }
        }
        String artKey = cardName + art;
        if (hasAnimation(artKey)) {
            return true;
        }

        if (!ed.isEmpty() && artIndex <= 1) {
            if (hasAnimation(ed + "/" + cardName)
                    || hasAnimation(ed + "_" + cardName)) {
                return true;
            }
        }
        // If this is an explicit non-primary art variant (artIndex > 1), do NOT fall back to generic cardName!
        if (artIndex > 1) {
            return false;
        }
        return hasAnimation(cardName);
    }

    public static BufferedImage getCurrentFrame(String cardName) {
        if (cardName == null) {
            return null;
        }
        if (!INSTANCE.initialized) {
            INSTANCE.initialize();
        }
        String key = cardName.toLowerCase().trim();
        BufferedImage[] frames = INSTANCE.cardAnimations.get(key);
        if (frames == null) {
            frames = INSTANCE.cardAnimations.get(normalize(key));
        }
        if (frames == null) {
            frames = INSTANCE.cardAnimations.get(key + "1");
        }
        if (frames == null) {
            frames = INSTANCE.cardAnimations.get(normalize(key + "1"));
        }
        if (frames == null) {
            if (INSTANCE.tryLoadCard(cardName, key, normalize(key))) {
                frames = INSTANCE.cardAnimations.get(key);
                if (frames == null) {
                    frames = INSTANCE.cardAnimations.get(normalize(key));
                }
            }
        }
        if (frames == null) {
            for (Map.Entry<String, BufferedImage[]> entry : INSTANCE.cardAnimations.entrySet()) {
                String k = entry.getKey();
                if (k.startsWith(key + "_") || k.startsWith(key + "1")
                        || k.endsWith("/" + key) || k.endsWith("/" + key + "1")
                        || k.endsWith("_" + key) || k.endsWith("_" + key + "1")) {
                    frames = entry.getValue();
                    break;
                }
            }
        }
        if (frames == null || frames.length == 0) {
            return null;
        }
        int idx = Math.floorMod(INSTANCE.currentFrameIndex, frames.length);
        return frames[idx];
    }

    public static BufferedImage getCurrentFrame(String cardName, int artIndex, String collectorNumber) {
        return getCurrentFrame(cardName, null, artIndex, collectorNumber);
    }

    public static BufferedImage getCurrentFrame(String cardName, String edition, int artIndex, String collectorNumber) {
        if (cardName == null) {
            return null;
        }
        String ed = edition != null ? edition.trim().toLowerCase() : "";
        String col = (collectorNumber != null && !collectorNumber.isEmpty() && !"N.A.".equalsIgnoreCase(collectorNumber))
                ? collectorNumber.trim() : null;

        if (!ed.isEmpty() && col != null) {
            String edColCardSlash = ed + "/" + cardName + "_" + col;
            if (hasAnimation(edColCardSlash)) {
                return getCurrentFrame(edColCardSlash);
            }
            String edColCardUnder = ed + "_" + cardName + "_" + col;
            if (hasAnimation(edColCardUnder)) {
                return getCurrentFrame(edColCardUnder);
            }
            String edColSlash = ed + "/" + col;
            if (hasAnimation(edColSlash)) {
                return getCurrentFrame(edColSlash);
            }
            String edColUnder = ed + "_" + col;
            if (hasAnimation(edColUnder)) {
                return getCurrentFrame(edColUnder);
            }
        }
        if (col != null) {
            String colKey = cardName + "_" + col;
            if (hasAnimation(colKey)) {
                return getCurrentFrame(colKey);
            }
        }

        int art = artIndex > 0 ? artIndex : 1;
        if (!ed.isEmpty()) {
            String edArtSlash = ed + "/" + cardName + art;
            if (hasAnimation(edArtSlash)) {
                return getCurrentFrame(edArtSlash);
            }
            String edArtUnder = ed + "_" + cardName + art;
            if (hasAnimation(edArtUnder)) {
                return getCurrentFrame(edArtUnder);
            }
        }
        String artKey = cardName + art;
        if (hasAnimation(artKey)) {
            return getCurrentFrame(artKey);
        }

        if (!ed.isEmpty() && artIndex <= 1) {
            String edCardSlash = ed + "/" + cardName;
            if (hasAnimation(edCardSlash)) {
                return getCurrentFrame(edCardSlash);
            }
            String edCardUnder = ed + "_" + cardName;
            if (hasAnimation(edCardUnder)) {
                return getCurrentFrame(edCardUnder);
            }
        }
        if (artIndex > 1) {
            return null;
        }
        return getCurrentFrame(cardName);
    }

    public static void register(JComponent component) {
        if (component == null) {
            return;
        }
        if (!INSTANCE.initialized) {
            INSTANCE.initialize();
        }
        synchronized (INSTANCE.activeComponents) {
            INSTANCE.activeComponents.add(component);
        }
        INSTANCE.ensureTimerRunning();
    }

    public static void register(JComponent component, String cardName) {
        register(component);
    }

    public static void unregister(JComponent component) {
        if (component == null) {
            return;
        }
        synchronized (INSTANCE.activeComponents) {
            INSTANCE.activeComponents.remove(component);
        }
    }
}
