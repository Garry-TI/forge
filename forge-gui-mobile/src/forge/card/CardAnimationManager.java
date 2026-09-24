package forge.card;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;

import forge.localinstance.properties.ForgeConstants;

/**
 * Manages animated card art loops for cards in the mobile/adventure LibGDX UI.
 */
public final class CardAnimationManager {

    private static final CardAnimationManager INSTANCE = new CardAnimationManager();

    private final Map<String, File[]> animationFiles = new ConcurrentHashMap<>();
    private final Map<String, Texture[]> animationTextures = new ConcurrentHashMap<>();
    private final Set<Texture> allTextures = Collections.newSetFromMap(new ConcurrentHashMap<>());
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

        if (!animationFiles.isEmpty()) {
            System.out.println("[CardAnimationManager-Mobile] Discovered animated cards: " + animationFiles.keySet());
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
        for (String animationKey : keys) {
            animationFiles.put(key(animationKey), frameFiles);
        }
        System.out.println("[CardAnimationManager-Mobile] Registered animation files for card '" + folder.getName() + "' with " + frameFiles.length + " frames.");
        return true;
    }

    private synchronized boolean tryLoadCard(String rawName) {
        String animationKey = key(rawName);
        if (animationFiles.containsKey(animationKey)) {
            return true;
        }

        for (File baseDir : getCandidateDirectories()) {
            if (baseDir == null || !baseDir.exists() || !baseDir.isDirectory()) {
                continue;
            }

            File directFolder = new File(baseDir, rawName.replace('/', File.separatorChar));
            if (directFolder.isDirectory() && loadCardFolder(directFolder, animationKey)) {
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
        if (INSTANCE.animationFiles.containsKey(animationKey)) {
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

    public static boolean isAnimationTexture(Texture texture) {
        return texture != null && INSTANCE.allTextures.contains(texture);
    }

    public static Texture getCurrentFrame(String cardName) {
        if (cardName == null) {
            return null;
        }
        if (!INSTANCE.initialized) {
            INSTANCE.initialize();
        }

        String animationKey = key(cardName);
        Texture[] textures = INSTANCE.animationTextures.get(animationKey);

        if (textures == null) {
            File[] files = INSTANCE.animationFiles.get(animationKey);
            if (files == null && INSTANCE.tryLoadCard(cardName)) {
                files = INSTANCE.animationFiles.get(animationKey);
            }
            if (files == null || files.length == 0) {
                return null;
            }

            textures = loadTextures(files);
            if (textures == null || textures.length == 0) {
                return null;
            }

            INSTANCE.animationTextures.put(animationKey, textures);
        }

        if (textures.length == 0) {
            return null;
        }

        // 24 FPS calculation
        long now = System.currentTimeMillis();
        int idx = (int) Math.floorMod(now * 24L / 1000L, textures.length);
        Texture frame = textures[idx];

        // Request next frame rendering so the animation loops continuously
        if (Gdx.graphics != null) {
            Gdx.graphics.requestRendering();
        }

        return frame;
    }

    public static Texture getCurrentFrame(String cardName, int artIndex, String collectorNumber) {
        return getCurrentFrame(cardName, null, artIndex, collectorNumber);
    }

    public static Texture getCurrentFrame(String cardName, String edition, int artIndex, String collectorNumber) {
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

    private static synchronized Texture[] loadTextures(File[] files) {
        Texture[] textures = new Texture[files.length];
        int loaded = 0;
        for (int i = 0; i < files.length; i++) {
            try {
                FileHandle fh = Gdx.files.absolute(files[i].getAbsolutePath());
                Texture t = new Texture(fh);
                t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                textures[i] = t;
                INSTANCE.allTextures.add(t);
                loaded++;
            } catch (Exception ex) {
                System.err.println("[CardAnimationManager-Mobile] Error loading texture frame " + files[i].getName() + ": " + ex.getMessage());
            }
        }

        if (loaded == 0) {
            return null;
        }
        System.out.println("[CardAnimationManager-Mobile] Successfully loaded " + loaded + " texture frames into GPU.");
        return textures;
    }
}
