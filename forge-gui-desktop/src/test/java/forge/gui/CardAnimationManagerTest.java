package forge.gui;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.testng.annotations.Test;

public class CardAnimationManagerTest {

    @Test(groups = { "UnitTest", "fast" })
    public void animationIsScopedToSetAndCollectorNumber() throws Exception {
        CardAnimationManager manager = CardAnimationManager.getInstance();
        Field animationsField = CardAnimationManager.class.getDeclaredField("cardAnimations");
        animationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, BufferedImage[]> animations = (Map<String, BufferedImage[]>) animationsField.get(manager);
        Map<String, BufferedImage[]> originalAnimations = new HashMap<>(animations);

        Field initializedField = CardAnimationManager.class.getDeclaredField("initialized");
        initializedField.setAccessible(true);
        boolean originallyInitialized = initializedField.getBoolean(manager);

        Path root = Files.createTempDirectory("forge-animation-test-");
        try {
            Path printing = Files.createDirectories(root.resolve("FIN").resolve("191"));
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png",
                    printing.resolve("frame_000.png").toFile());

            animations.clear();
            initializedField.setBoolean(manager, true);
            Method scanDirectory = CardAnimationManager.class.getDeclaredMethod("scanDirectory", File.class);
            scanDirectory.setAccessible(true);
            scanDirectory.invoke(manager, root.toFile());

            assertTrue(CardAnimationManager.hasAnimation("Jumbo Cactuar", "FIN", 1, "191"));
            assertNotNull(CardAnimationManager.getCurrentFrame("Jumbo Cactuar", "FIN", 1, "191"));
            assertFalse(CardAnimationManager.hasAnimation("Jumbo Cactuar", "FIN", 2, "343"));
            assertFalse(CardAnimationManager.hasAnimation("Jumbo Cactuar"));
        } finally {
            animations.clear();
            animations.putAll(originalAnimations);
            initializedField.setBoolean(manager, originallyInitialized);
            Files.walk(root)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }
}
