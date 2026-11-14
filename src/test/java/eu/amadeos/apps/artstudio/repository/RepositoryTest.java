package eu.amadeos.apps.artstudio.repository;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences.Bar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {
    @TempDir
    Path dir;

    private final PictureFiles files = new PictureFiles();
    private final Recipe recipe = new Recipe(Style.TILES, 66, Palette.OCEAN, 10, 20, 30, 40, false, Aspect.SQUARE);

    private static BufferedImage picture() {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        image.setRGB(3, 4, 0x123456);
        return image;
    }

    @Test
    void pngKeepsTheRecipe() throws IOException {
        Path png = dir.resolve("tiles.png");
        files.writePng(png, picture(), recipe);
        BufferedImage read = ImageIO.read(png.toFile());
        assertEquals(20, read.getWidth());
        assertEquals(0x123456, read.getRGB(3, 4) & 0xFFFFFF);
        assertEquals(recipe, files.readRecipeFromPng(png).orElseThrow());
        assertEquals(recipe, files.readRecipe(png));
        assertFalse(Files.exists(dir.resolve(".tiles.png.part")));
    }

    @Test
    void aPngWithoutRecipe() throws IOException {
        Path png = dir.resolve("plain.png");
        ImageIO.write(picture(), "png", png.toFile());
        assertTrue(files.readRecipeFromPng(png).isEmpty());
        assertThrows(IOException.class, () -> files.readRecipe(png));
    }

    @Test
    void recipeFiles() throws IOException {
        Path file = dir.resolve("tiles.artrecipe");
        files.writeRecipe(file, recipe);
        assertTrue(Files.readString(file).startsWith(Recipe.HEADER));
        assertEquals(recipe, files.readRecipe(file));
        files.writeRecipe(file, recipe.withSeed(1));
        assertEquals(1, files.readRecipe(file).seed());

        Path garbage = dir.resolve("garbage.artrecipe");
        Files.writeString(garbage, "nothing to see");
        assertThrows(IOException.class, () -> files.readRecipe(garbage));
        Path big = dir.resolve("big.artrecipe");
        Files.write(big, new byte[100_000]);
        assertThrows(IOException.class, () -> files.readRecipe(big));
        assertThrows(IOException.class, () -> files.readRecipe(dir.resolve("missing.artrecipe")));
    }

    @Test
    void preferences() {
        Path file = dir.resolve("art-studio.properties");
        ArtStudioPreferences p = new ArtStudioPreferences(file);
        for (Bar bar : Bar.values()) assertTrue(p.isVisible(bar));
        assertFalse(p.keepColors());
        assertTrue(p.lastRecipe().isEmpty());
        assertEquals(ArtStudioPreferences.DEFAULT_EXPORT_WIDTH, p.exportWidth());

        p.setVisible(Bar.HISTORY, false);
        p.setKeepColors(true);
        p.setExportSize(4961, 7016);
        p.setLastRecipe(recipe);
        ArtStudioPreferences again = new ArtStudioPreferences(file);
        assertFalse(again.isVisible(Bar.HISTORY));
        assertTrue(again.isVisible(Bar.STYLES));
        assertTrue(again.keepColors());
        assertEquals(4961, again.exportWidth());
        assertEquals(7016, again.exportHeight());
        assertEquals(recipe, again.lastRecipe().orElseThrow());

        again.setExportSize(5, 99_999);
        ArtStudioPreferences odd = new ArtStudioPreferences(file);
        assertEquals(ArtStudioPreferences.DEFAULT_EXPORT_WIDTH, odd.exportWidth());
        assertEquals(ArtStudioPreferences.DEFAULT_EXPORT_HEIGHT, odd.exportHeight());
    }

    @Test
    void aBrokenLastRecipeIsForgotten() throws IOException {
        Path file = dir.resolve("p.properties");
        Files.writeString(file, "last.recipe=style\\=nothing\n");
        assertTrue(new ArtStudioPreferences(file).lastRecipe().isEmpty());
    }

    @Test
    void workspaceFromXprop() {
        assertEquals(2, Backdrops.parseWorkspace("_NET_CURRENT_DESKTOP(CARDINAL) = 2\n"));
        assertEquals(0, Backdrops.parseWorkspace("_NET_CURRENT_DESKTOP(CARDINAL) = 0"));
        assertEquals(-1, Backdrops.parseWorkspace("_NET_CURRENT_DESKTOP:  not found."));
        assertEquals(-1, Backdrops.parseWorkspace(null));
        assertEquals(1, Backdrops.currentWorkspace(command -> new eu.amadeos.commons.process.FinishedProcess(
                "_NET_CURRENT_DESKTOP(CARDINAL) = 1\n", "", 0)));
        assertEquals(-1, Backdrops.currentWorkspace(command -> {
            throw new IOException("no xprop");
        }));
    }

    @Test
    void backdropOfTheCurrentWorkspace() throws IOException {
        Path config = dir.resolve("config");
        Path pictures = dir.resolve("backdrops");
        AtomicInteger workspace = new AtomicInteger(2);
        Backdrops backdrops = new Backdrops(config, pictures, workspace::get);
        assertEquals(2, backdrops.apply(picture(), recipe));
        Properties p = PropertiesFile.load(config.resolve(Backdrops.CONFIG));
        String first = p.getProperty("workspace.3.wallpaper");
        assertTrue(first.startsWith(pictures.resolve("backdrop-tiles-66-").toString()), first);
        assertEquals("FILL", p.getProperty("workspace.3.mode"));
        assertEquals(Palette.hex(Palette.OCEAN.paper()), p.getProperty("workspace.3.color"));
        assertEquals("/usr/share/backgrounds/amadeos/wallpaper.png", p.getProperty("wallpaper"),
                "the other workspaces keep the amadeOS backdrop");
        assertEquals(recipe, new PictureFiles().readRecipe(Path.of(first)));

        // A second backdrop on the same workspace replaces the first picture, which is deleted.
        sleepAMillisecond();
        backdrops.apply(picture(), recipe.withSeed(67));
        p = PropertiesFile.load(config.resolve(Backdrops.CONFIG));
        assertFalse(Files.exists(Path.of(first)));
        assertTrue(Files.exists(Path.of(p.getProperty("workspace.3.wallpaper"))));

        // Without a known workspace the common backdrop changes; workspace 3 keeps its own.
        workspace.set(-1);
        sleepAMillisecond();
        assertEquals(-1, backdrops.apply(picture(), recipe.withSeed(68)));
        p = PropertiesFile.load(config.resolve(Backdrops.CONFIG));
        assertTrue(p.getProperty("wallpaper").contains("backdrop-tiles-68-"));
        assertTrue(p.getProperty("workspace.3.wallpaper").contains("backdrop-tiles-67-"));
        try (var list = Files.list(pictures)) {
            assertEquals(2, list.count());
        }
    }

    @Test
    void theUsersOtherSettingsStay() throws IOException {
        Path config = dir.resolve("config");
        Files.createDirectories(config);
        Files.writeString(config.resolve(Backdrops.CONFIG), "wallpaper=/home/me/sea.jpg\nmode=FIT\ncolor=#102030\n"
                + "workspace.1.wallpaper=/usr/share/backdrops/Concave.pm\nworkspace.1.mode=TILE\n");
        new Backdrops(config, dir.resolve("b"), () -> 1).apply(picture(), recipe);
        Properties p = PropertiesFile.load(config.resolve(Backdrops.CONFIG));
        assertEquals("/home/me/sea.jpg", p.getProperty("wallpaper"));
        assertEquals("FIT", p.getProperty("mode"));
        assertEquals("/usr/share/backdrops/Concave.pm", p.getProperty("workspace.1.wallpaper"));
        assertTrue(p.getProperty("workspace.2.wallpaper").endsWith(".png"));
        assertNull(p.getProperty("workspace.3.wallpaper"));
        assertEquals(List.of(), List.of());
    }

    private static void sleepAMillisecond() {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() == start) Thread.onSpinWait();
    }
}
