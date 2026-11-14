package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.apps.artstudio.OnEdt;
import eu.amadeos.apps.artstudio.QueueExecutor;
import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.domain.Variations;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences.Bar;
import eu.amadeos.apps.artstudio.repository.Backdrops;
import eu.amadeos.apps.artstudio.repository.PictureFiles;
import eu.amadeos.apps.artstudio.service.art.ArtRenderer;
import eu.amadeos.apps.artstudio.service.render.RenderService;
import eu.amadeos.commons.theme.DesktopTheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window with the real renderer: settings, actions, variations. Needs a display (Xvfb in CI). */
class MainWindowTest {
    @TempDir
    Path dir;

    @Test
    void drawThroughTheWindow() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        OnEdt.run(() -> {
            QueueExecutor executor = new QueueExecutor();
            ArtStudioPreferences preferences = new ArtStudioPreferences(dir.resolve("art-studio.properties"));
            MainWindow window = new MainWindow(MainPresenterTest.MSG, null);
            AtomicLong seeds = new AtomicLong(500);
            MainPresenter presenter = new MainPresenter(window, MainPresenterTest.MSG, null, preferences,
                    new RenderService(new ArtRenderer(), executor, executor), new PictureFiles(),
                    new Backdrops(dir.resolve("config"), dir.resolve("b"), () -> 0), executor, executor,
                    () -> DesktopTheme.WARM_MOTIF, seeds::getAndIncrement);
            window.bind(presenter);
            window.pictureView().setSize(352, 232);
            try {
                ArtActions actions = window.actions();
                assertFalse(actions.savePicture.isEnabled());
                presenter.start(List.of());
                executor.runAll();
                assertTrue(actions.savePicture.isEnabled());
                assertFalse(actions.back.isEnabled());
                assertFalse(actions.stop.isEnabled());
                assertNotNull(window.pictureView().picture());
                assertEquals(320, window.pictureView().picture().getWidth());
                assertTrue(window.statusText().startsWith("Flow, seed 500: 320 × 200 pixels"), window.statusText());
                assertEquals("Flow #500 - Art Studio", window.getTitle());
                assertFalse(window.progressBar().isVisible() && window.progressBar().isIndeterminate());

                SettingsPanel settings = window.settingsPanel();
                assertEquals("500", settings.seedField().getText());
                assertEquals(Aspect.SCREEN, settings.aspectBox().getSelectedItem());
                assertEquals(Palette.AUTUMN.id(), settings.paletteBox().getSelectedItem());
                assertEquals(Style.FLOW, window.styleList().getSelectedValue());

                // The settings change the recipe.
                settings.seedField().setText("42");
                settings.seedField().postActionEvent();
                assertEquals(42, presenter.recipe().seed());
                settings.seedField().setText("forty-two");
                settings.seedField().postActionEvent();
                assertEquals("42", settings.seedField().getText(), "a wrong seed is put back");
                settings.densitySlider().setValue(90);
                assertEquals(90, presenter.recipe().density());
                settings.paperBox().doClick();
                assertFalse(presenter.recipe().paper());
                settings.aspectBox().setSelectedItem(Aspect.SQUARE);
                assertEquals(Aspect.SQUARE, presenter.recipe().aspect());
                settings.paletteBox().setSelectedItem(Palette.NIGHT.id());
                assertEquals(Palette.NIGHT, presenter.recipe().palette());
                settings.paletteBox().setSelectedItem(Palette.DESKTOP);
                assertEquals(Palette.DESKTOP, presenter.recipe().palette().id());
                window.styleList().setSelectedValue(Style.WAVES, false);
                assertEquals(Style.WAVES, presenter.recipe().style());
                executor.runAll();
                assertEquals(200, window.pictureView().picture().getWidth(), "square");
                assertTrue(actions.back.isEnabled());

                // History and actions.
                assertEquals(5, window.historyStrip().buttons().size());
                window.historyStrip().buttons().getFirst().doClick();
                assertEquals(500, presenter.recipe().seed());
                assertEquals(Style.FLOW, window.styleList().getSelectedValue());
                actions.forward.actionPerformed(null);
                actions.newPicture.actionPerformed(null);
                executor.runAll();
                assertEquals(3, window.historyStrip().buttons().size(), "the pictures after the second are dropped");

                // Variations: a click on a cell continues from it.
                actions.variations.actionPerformed(null);
                executor.runAll();
                assertTrue(window.pictureView().showsVariations());
                List<eu.amadeos.apps.artstudio.domain.Recipe> around = Variations.around(presenter.recipe());
                Rectangle first = window.pictureView().cell(0);
                assertEquals(0, window.pictureView().cellAt(first.x + 2, first.y + 2));
                window.pictureView().dispatchEvent(new MouseEvent(window.pictureView(), MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), 0, first.x + 2, first.y + 2, 1, false, MouseEvent.BUTTON1));
                assertEquals(around.getFirst(), presenter.recipe());
                actions.escape.actionPerformed(null);
                assertFalse(window.pictureView().showsVariations());

                // Bars.
                presenter.setBarVisible(Bar.STYLES, false);
                assertFalse(preferences.isVisible(Bar.STYLES));
                presenter.setBarVisible(Bar.STYLES, true);

                // Export sizes follow the shape.
                List<MainWindow.ExportPreset> landscape = window.exportPresets(Aspect.SCREEN);
                assertTrue(landscape.stream().anyMatch(p -> p.width() == 4961 && p.height() == 3508));
                List<MainWindow.ExportPreset> portrait = window.exportPresets(Aspect.PORTRAIT);
                assertTrue(portrait.stream().anyMatch(p -> p.width() == 3508 && p.height() == 4961));
            } finally {
                window.dispose();
            }
        });
    }
}
