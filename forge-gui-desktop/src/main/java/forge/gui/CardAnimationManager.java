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

    private static String key(String name) {
        if (name == null) {
            return "";
        }
        String result = name.trim().toLowerCase().replace('\\', '/');
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
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
            String folderName = key(folder.getName());
            boolean loaded = loadCardFolder(folder, folderName);
            if (!loaded) {
                // Might be a set folder (e.g. animDir/FIN/Jumbo Cactuar_191)
                File[] subFolders = folder.listFiles(File::isDirectory);
                if (subFolders != null) {
                    for (File sub : subFolders) {
                        loadCardFolder(sub, folderName + "/" + key(sub.getName()));
                    }
                }
            }
        }
    }

    private boolean loadCardFolder(File folder, String... keys) {
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
            for (String animationKey : keys) {
                cardAnimations.put(key(animationKey), frames);
            }
            System.out.println("[CardAnimationManager] Registered animation for card '" + folder.getName() + "' with " + loaded + " frames.");
            return true;
        }
        return false;
    }

    private synchronized boolean tryLoadCard(String rawName) {
        String animationKey = key(rawName);
        if (cardAnimations.containsKey(animationKey)) {
            return true;
        }

        for (File baseDir : getCandidateDirectories()) {
            if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory()) {
                continue;
            }

            // Direct folder match
            File directFolder = new File(baseDir, rawName.replace('/', File.separatorChar));
            if (directFolder.isDirectory() && loadCardFolder(directFolder, animationKey)) {
                ensureTimerRunning();
                return true;
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
        String animationKey = key(cardName);
        if (INSTANCE.cardAnimations.containsKey(animationKey)) {
            return true;
        }
        return INSTANCE.tryLoadCard(cardName);
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

        if (!ed.isEmpty() && col != null && hasAnimation(ed + "/" + col)) {
            return true;
        }

        int art = artIndex > 0 ? artIndex : 1;
        if (!ed.isEmpty() && hasAnimation(ed + "/" + cardName + art + ".full")) {
            return true;
        }
        if (!ed.isEmpty() && artIndex <= 1 && hasAnimation(ed + "/" + cardName + ".full")) {
            return true;
        }
        return ed.isEmpty() && col == null && artIndex <= 0 && hasAnimation(cardName);
    }

    public static BufferedImage getCurrentFrame(String cardName) {
        if (cardName == null) {
            return null;
        }
        if (!INSTANCE.initialized) {
            INSTANCE.initialize();
        }
        String animationKey = key(cardName);
        BufferedImage[] frames = INSTANCE.cardAnimations.get(animationKey);
        if (frames == null && INSTANCE.tryLoadCard(cardName)) {
            frames = INSTANCE.cardAnimations.get(animationKey);
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

        if (!ed.isEmpty() && col != null && hasAnimation(ed + "/" + col)) {
            return getCurrentFrame(ed + "/" + col);
        }

        int art = artIndex > 0 ? artIndex : 1;
        String imagePath = ed + "/" + cardName + art + ".full";
        if (!ed.isEmpty() && hasAnimation(imagePath)) {
            return getCurrentFrame(imagePath);
        }

        if (!ed.isEmpty() && artIndex <= 1) {
            String singleArtPath = ed + "/" + cardName + ".full";
            if (hasAnimation(singleArtPath)) {
                return getCurrentFrame(singleArtPath);
            }
        }
        return ed.isEmpty() && col == null && artIndex <= 0 ? getCurrentFrame(cardName) : null;
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
