package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.apps.artstudio.QueueExecutor;
import eu.amadeos.apps.artstudio.bootstrap.ArtStudioModule;
import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.domain.Variations;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences.Bar;
import eu.amadeos.apps.artstudio.repository.Backdrops;
import eu.amadeos.apps.artstudio.repository.PictureFiles;
import eu.amadeos.apps.artstudio.service.art.ArtRenderer;
import eu.amadeos.apps.artstudio.service.render.RenderService;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.theme.DesktopTheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    static final Messages MSG = Messages.load(ArtStudioModule.class, ArtStudioModule.MESSAGES, Locale.ENGLISH);

    @TempDir
    Path dir;

    private final QueueExecutor executor = new QueueExecutor();
    private final FakeView view = new FakeView();
    private final AtomicLong nextSeed = new AtomicLong(100);
    private final AtomicReference<DesktopTheme> theme = new AtomicReference<>(DesktopTheme.WARM_MOTIF);
    private final AtomicReference<Integer> workspace = new AtomicReference<>(1);
    private ArtStudioPreferences preferences;
    private MainPresenter presenter;

    @BeforeEach
    void setUp() {
        preferences = new ArtStudioPreferences(dir.resolve("art-studio.properties"));
        Backdrops backdrops = new Backdrops(dir.resolve("config"), dir.resolve("backdrops"), workspace::get);
        presenter = new MainPresenter(view, MSG, null, preferences,
                new RenderService(new ArtRenderer(), executor, executor), new PictureFiles(), backdrops,
                executor, executor, theme::get, nextSeed::getAndIncrement);
    }

    private void start() {
        presenter.start(List.of());
        executor.runAll();
    }

    @Test
    void startsWithANewFlowPicture() {
        start();
        Recipe r = presenter.recipe();
        assertEquals(Style.FLOW, r.style());
        assertEquals(100, r.seed());
        assertEquals(Palette.AUTUMN, r.palette());
        assertEquals(r, view.recipe);
        assertEquals("Flow #100 - Art Studio", view.title);
        assertNotNull(view.picture);
        // 232 × 132 less the margins is 200 × 100; the 16:10 shape fits as 160 × 100.
        assertEquals(160, view.picture.getWidth());
        assertEquals(100, view.picture.getHeight());
        assertTrue(view.status.startsWith("Flow, seed 100: 160 × 100 pixels in "), view.status);
        assertFalse(view.busy);
        assertTrue(view.commands.hasPicture());
        assertFalse(view.commands.drawing());
        assertFalse(view.commands.canGoBack());
        assertEquals(1, view.history.size());
        assertNotNull(view.history.getFirst(), "the history shows the picture small");
        assertEquals(Style.values().length, view.styleThumbnails.size());
        assertEquals(r, preferences.lastRecipe().orElseThrow());
    }

    @Test
    void startsWithTheLastPicture() {
        Recipe last = Recipe.of(Style.MOSAIC, 4711, Palette.NIGHT).withAspect(Aspect.SQUARE);
        preferences.setLastRecipe(last);
        start();
        assertEquals(last, presenter.recipe());
        assertEquals(100, view.picture.getWidth());
        assertEquals(100, view.picture.getHeight());
    }

    @Test
    void startsWithAFileGivenOnTheCommandLine() throws IOException {
        Path file = dir.resolve("dots.artrecipe");
        Recipe dots = Recipe.of(Style.DOTS, 5, Palette.PASTEL);
        new PictureFiles().writeRecipe(file, dots);
        presenter.start(List.of(file.toString()));
        executor.runAll();
        assertEquals(dots, presenter.recipe());
        assertEquals(2, view.history.size());
        assertTrue(view.errors.isEmpty());
    }

    @Test
    void newPicturesGetOtherSeedsAndColours() {
        start();
        presenter.newPicture();
        executor.runAll();
        Recipe r = presenter.recipe();
        assertNotEquals(100, r.seed());
        assertNotEquals(Palette.AUTUMN, r.palette());
        assertTrue(Palette.BUILT_IN.contains(r.palette()));
        assertTrue(view.commands.canGoBack());

        presenter.setKeepColors(true);
        assertTrue(presenter.keepColors());
        Palette kept = presenter.recipe().palette();
        presenter.newPicture();
        assertEquals(kept, presenter.recipe().palette());

        presenter.randomSeed();
        assertEquals(kept, presenter.recipe().palette());
        assertEquals(Style.FLOW, presenter.recipe().style());
    }

    @Test
    void ownColoursStayOnNewPictures() {
        start();
        view.color = Optional.of(Color.MAGENTA);
        presenter.editSwatch(2);
        Palette own = presenter.recipe().palette();
        assertEquals(Palette.CUSTOM, own.id());
        assertEquals(Color.MAGENTA, own.colors().getFirst());
        presenter.newPicture();
        assertEquals(own, presenter.recipe().palette());

        // Cancelled or the same colour: nothing changes.
        int entries = view.history.size();
        view.color = Optional.empty();
        presenter.editSwatch(2);
        view.color = Optional.of(Color.MAGENTA);
        presenter.editSwatch(2);
        presenter.editSwatch(99);
        assertEquals(entries, view.history.size());
    }

    @Test
    void typedSeeds() {
        start();
        assertTrue(presenter.setSeed(" 1.234.567 "));
        assertEquals(1_234_567, presenter.recipe().seed());
        assertFalse(presenter.setSeed("abc"));
        assertEquals("The seed is a number from 0 to 999999999.", view.status);
        assertFalse(presenter.setSeed("1000000000"));
        assertFalse(presenter.setSeed("-1"));
        assertEquals(1_234_567, presenter.recipe().seed());
        int entries = view.history.size();
        assertTrue(presenter.setSeed("1234567"));
        assertEquals(entries, view.history.size(), "the same seed is no new picture");
    }

    @Test
    void slidersChangeThePictureWithoutNewHistory() {
        start();
        presenter.selectStyle(Style.GRID);
        executor.runAll();
        assertEquals(2, view.history.size());
        presenter.setDensity(80);
        presenter.setScale(20);
        presenter.setChaos(0);
        presenter.setLine(100);
        presenter.setPaper(false);
        executor.runAll();
        Recipe r = presenter.recipe();
        assertEquals(new Recipe(Style.GRID, 100, Palette.AUTUMN, 80, 20, 0, 100, false, Aspect.SCREEN), r);
        assertEquals(2, view.history.size());
        assertEquals(r, preferences.lastRecipe().orElseThrow());

        presenter.setAspect(Aspect.PORTRAIT);
        executor.runAll();
        assertEquals(71, view.picture.getWidth());
        assertEquals(100, view.picture.getHeight());

        presenter.goBack();
        assertEquals(Style.FLOW, presenter.recipe().style());
        presenter.goForward();
        assertEquals(Aspect.PORTRAIT, presenter.recipe().aspect());
        assertFalse(view.commands.canGoForward());
    }

    @Test
    void historyKeepsFortyPicturesAndForgetsTheFuture() {
        start();
        for (int i = 0; i < MainPresenter.HISTORY_SIZE + 5; i++) presenter.randomSeed();
        executor.runAll();
        assertEquals(MainPresenter.HISTORY_SIZE, view.history.size());
        assertEquals(MainPresenter.HISTORY_SIZE - 1, view.historyCurrent);

        presenter.goToHistory(10);
        long seed = presenter.recipe().seed();
        presenter.goToHistory(-1);
        presenter.goToHistory(1000);
        assertEquals(seed, presenter.recipe().seed());
        assertTrue(view.commands.canGoForward());
        presenter.selectStyle(Style.WAVES);
        assertEquals(12, view.history.size(), "the pictures after the current one are dropped");
        assertFalse(view.commands.canGoForward());
    }

    @Test
    void thePictureIsDrawnAgainWhenTheWindowChanges() {
        start();
        view.canvas = new Dimension(432, 232);
        presenter.canvasResized(432, 232);
        assertTrue(view.busy);
        assertTrue(view.commands.drawing());
        executor.runAll();
        assertEquals(320, view.picture.getWidth());
        assertFalse(view.busy);
    }

    @Test
    void anOutdatedPictureIsNotShown() {
        start();
        presenter.selectStyle(Style.CIRCLES);
        presenter.selectStyle(Style.DOTS);
        executor.runAll();
        assertTrue(view.status.startsWith("Dots, "), view.status);
    }

    @Test
    void variations() {
        start();
        presenter.toggleVariations();
        assertTrue(presenter.variationsShown());
        assertEquals(Variations.COUNT, view.variations.size());
        assertTrue(view.busy);
        executor.runAll();
        for (BufferedImage p : view.variations) assertNotNull(p);
        assertTrue(view.commands.variations());
        assertTrue(view.status.startsWith("Nine variations of Flow"), view.status);
        assertFalse(view.busy);

        List<Recipe> around = Variations.around(presenter.recipe());
        presenter.pickVariation(Variations.MIDDLE);
        presenter.pickVariation(-1);
        assertEquals(1, view.history.size());
        presenter.pickVariation(0);
        executor.runAll();
        assertEquals(around.getFirst(), presenter.recipe());
        assertEquals(2, view.history.size());
        assertTrue(presenter.variationsShown(), "new variations around the picked one");
        assertNotNull(view.variations);

        presenter.escape();
        assertFalse(presenter.variationsShown());
        assertNull(view.variations);
        assertNotNull(view.picture);
        presenter.toggleVariations();
        presenter.toggleVariations();
        executor.runAll();
        assertFalse(presenter.variationsShown());
    }

    @Test
    void saveAndOpen() throws IOException {
        start();
        Path recipeFile = dir.resolve("mine.artrecipe");
        view.paths.add(Optional.of(recipeFile));
        presenter.saveRecipe();
        assertTrue(view.busy);
        executor.runAll();
        assertEquals("flow-100.artrecipe", view.suggestedNames.getLast());
        assertEquals("Saved mine.artrecipe.", view.status);
        assertFalse(view.busy);

        Path png = dir.resolve("mine.png");
        view.paths.add(Optional.of(png));
        presenter.savePicture();
        executor.runAll();
        assertEquals(160, ImageIO.read(png.toFile()).getWidth());
        Recipe saved = presenter.recipe();

        presenter.selectStyle(Style.TILES);
        view.paths.add(Optional.of(png));
        presenter.openFile();
        executor.runAll();
        assertEquals(saved, presenter.recipe());
        assertTrue(view.errors.isEmpty());

        // Cancelled dialogs do nothing.
        presenter.openFile();
        presenter.saveRecipe();
        presenter.savePicture();
        assertTrue(executor.isIdle());

        Path broken = dir.resolve("broken.artrecipe");
        Files.writeString(broken, "nothing");
        presenter.open(broken);
        executor.runAll();
        assertTrue(view.errors.getLast().startsWith("Could not open broken.artrecipe: "), view.errors.getLast());
        assertFalse(view.busy);
    }

    @Test
    void savingIntoAMissingFolderFails() {
        start();
        view.paths.add(Optional.of(dir.resolve("no/such/folder/x.artrecipe")));
        presenter.saveRecipe();
        executor.runAll();
        assertEquals(1, view.errors.size());
        assertFalse(view.busy);
    }

    @Test
    void export() throws IOException {
        start();
        assertEquals(new Dimension(ArtStudioPreferences.DEFAULT_EXPORT_WIDTH,
                ArtStudioPreferences.DEFAULT_EXPORT_HEIGHT), dialogSuggestion());

        Path png = dir.resolve("poster.png");
        view.exportSize = Optional.of(new Dimension(400, 250));
        view.paths.add(Optional.of(png));
        presenter.exportPicture();
        assertEquals("flow-100-400x250.png", view.suggestedNames.getLast());
        assertTrue(view.commands.exporting());
        assertEquals("Drawing 400 × 250 pixels...", view.status);
        presenter.exportPicture();
        executor.runAll();
        assertFalse(view.commands.exporting());
        assertEquals("Saved poster.png.", view.status);
        assertEquals(400, ImageIO.read(png.toFile()).getWidth());
        assertEquals(presenter.recipe(), new PictureFiles().readRecipe(png));
        assertEquals(400, preferences.exportWidth());

        // Stopped: nothing is written.
        Path stopped = dir.resolve("stopped.png");
        view.paths.add(Optional.of(stopped));
        presenter.exportPicture();
        presenter.escape();
        executor.runAll();
        assertEquals("Export stopped.", view.status);
        assertFalse(Files.exists(stopped));
        assertFalse(view.busy);
    }

    private Dimension dialogSuggestion() {
        view.exportSize = Optional.empty();
        presenter.exportPicture();
        return view.suggestedExportSize;
    }

    @Test
    void exitAsksDuringAnExport() {
        start();
        view.exportSize = Optional.of(new Dimension(400, 250));
        view.paths.add(Optional.of(dir.resolve("e.png")));
        presenter.exportPicture();
        presenter.exit();
        assertEquals(1, view.confirmations);
        assertFalse(view.disposed);
        view.confirmAnswer = true;
        presenter.exit();
        assertTrue(view.disposed);
        executor.runAll();
        assertFalse(Files.exists(dir.resolve("e.png")));
    }

    @Test
    void backdrop() {
        start();
        presenter.setAsBackdrop();
        assertTrue(view.busy);
        executor.runAll();
        assertEquals("The picture is now the backdrop of workspace 2.", view.status);
        assertFalse(view.busy);
        workspace.set(-1);
        presenter.setAsBackdrop();
        executor.runAll();
        assertEquals("The picture is now the backdrop.", view.status);
        assertTrue(view.errors.isEmpty());
    }

    @Test
    void clipboard() {
        start();
        presenter.copyPicture();
        assertEquals(view.picture, view.copiedPicture);
        presenter.copyRecipe();
        assertEquals(presenter.recipe().toLine(), view.copiedText);

        Recipe other = Recipe.of(Style.ROSETTE, 77, Palette.EARTH);
        view.clipboard = Optional.of(other.toLine());
        presenter.pasteRecipe();
        assertEquals(other, presenter.recipe());
        assertEquals("Recipe pasted.", view.status);

        view.clipboard = Optional.of("a shopping list");
        presenter.pasteRecipe();
        assertEquals(other, presenter.recipe());
        assertEquals("There is no recipe on the clipboard.", view.status);
        view.clipboard = Optional.empty();
        presenter.pasteRecipe();
        assertEquals(other, presenter.recipe());
    }

    @Test
    void desktopColoursFollowTheTheme() {
        start();
        presenter.useDesktopColors();
        Palette motif = presenter.recipe().palette();
        assertEquals(Palette.DESKTOP, motif.id());
        assertEquals(new Color(DesktopTheme.WARM_MOTIF.base().getRGB() & 0xFFFFFF), motif.paper());
        int entries = view.history.size();
        theme.set(DesktopTheme.DARK_SLATE);
        presenter.themeChanged();
        assertNotEquals(motif, presenter.recipe().palette());
        assertEquals(Palette.DESKTOP, presenter.recipe().palette().id());
        assertEquals(entries, view.history.size());

        // Other palettes stay when the theme changes.
        presenter.setPalette(Palette.OCEAN);
        theme.set(DesktopTheme.WARM_MOTIF);
        presenter.themeChanged();
        assertEquals(Palette.OCEAN, presenter.recipe().palette());
    }

    @Test
    void everyDesktopThemeMakesAPalette() {
        for (DesktopTheme t : List.of(DesktopTheme.WARM_MOTIF, DesktopTheme.CDE_BROCADE, DesktopTheme.CDE_DESERT,
                DesktopTheme.CLASSIC_GRAY, DesktopTheme.DARK_SLATE)) {
            Palette p = MainPresenter.desktopPalette(t);
            assertTrue(p.colors().size() >= Palette.MIN_COLORS && p.colors().size() <= Palette.MAX_COLORS);
        }
    }

    @Test
    void windowCommands() {
        start();
        presenter.toggleFullScreen();
        assertTrue(view.fullScreen);
        assertTrue(view.commands.fullScreen());
        executor.runAll();
        // Without margins the picture uses the whole canvas: 232 × 132 fits 16:10 as 211 × 132.
        presenter.canvasResized(232, 132);
        executor.runAll();
        assertEquals(132, view.picture.getHeight());
        presenter.escape();
        assertFalse(view.fullScreen);

        presenter.setBarVisible(Bar.HISTORY, false);
        assertEquals(Boolean.FALSE, view.bars.get(Bar.HISTORY));
        assertFalse(presenter.isBarVisible(Bar.HISTORY));
        assertTrue(presenter.isBarVisible(Bar.STYLES));
        presenter.about();
        assertTrue(view.aboutShown);
        presenter.exit();
        assertTrue(view.disposed);
        assertEquals(0, view.confirmations);
    }

    @Test
    void thumbnailsKeepTheirShape() {
        BufferedImage wide = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        BufferedImage small = MainPresenter.scaled(wide, MainPresenter.THUMB_WIDTH, MainPresenter.THUMB_HEIGHT);
        assertEquals(96, small.getWidth());
        assertEquals(54, small.getHeight());
        BufferedImage tall = MainPresenter.scaled(new BufferedImage(10, 100, BufferedImage.TYPE_INT_RGB), 96, 60);
        assertEquals(6, tall.getWidth());
        assertEquals(60, tall.getHeight());
    }
}
