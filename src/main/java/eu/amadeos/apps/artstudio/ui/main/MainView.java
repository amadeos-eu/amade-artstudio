package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences.Bar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** What {@link MainPresenter} needs from the window; tests replace it. */
public interface MainView {
    /** Which commands can run now; menus and toolbar follow. */
    record Commands(boolean drawing, boolean hasPicture, boolean canGoBack, boolean canGoForward,
                    boolean variations, boolean exporting, boolean fullScreen) {
        public static final Commands NONE = new Commands(false, false, false, false, false, false, false);
    }

    void showTitle(String title);

    /** Fills the style list, the settings and the menus with the recipe. */
    void showRecipe(Recipe recipe, boolean keepColors);

    /** The small picture of a style in the list. */
    void showStyleThumbnail(Style style, BufferedImage thumbnail);

    /** Shows the picture; the variations, if shown, give way to it. */
    void showPicture(BufferedImage picture);

    /**
     * Shows the grid of variations instead of the picture.
     *
     * @param pictures nine pictures, {@code null} where one is still being drawn
     */
    void showVariations(List<BufferedImage> pictures);

    /** The history below the picture: small pictures, the current one marked. */
    void showHistory(List<BufferedImage> thumbnails, int current);

    void showStatus(String text);

    /** The progress bar runs while something is drawn or saved. */
    void showBusy(boolean busy);

    void updateCommands(Commands commands);

    /** The size of the picture area in pixels. */
    Dimension canvasSize();

    /** The size of the screen the window is on, for backdrops. */
    Dimension screenSize();

    /** Asks for a recipe or a PNG from Art Studio to open; empty if cancelled. */
    Optional<Path> chooseOpenPath();

    /** Asks where to save the recipe; replacing a file is confirmed. Empty if cancelled. */
    Optional<Path> chooseRecipeSavePath(String suggestedName);

    /** Asks where to save a PNG; replacing a file is confirmed. Empty if cancelled. */
    Optional<Path> choosePictureSavePath(String suggestedName);

    /** Asks for the size of an exported picture; empty if cancelled. */
    Optional<Dimension> chooseExportSize(Dimension suggested, Aspect aspect);

    /** Asks for a colour; empty if cancelled. */
    Optional<Color> chooseColor(String title, Color initial);

    void copyToClipboard(BufferedImage picture);

    void copyToClipboard(String text);

    /** The text on the clipboard, if there is any. */
    Optional<String> clipboardText();

    /** Shows or hides a part of the window. */
    void showBar(Bar bar, boolean visible);

    /** Only the picture, over the whole screen; or back to the window. */
    void setFullScreen(boolean fullScreen);

    /** A yes/no question; true for yes. */
    boolean confirm(String message, String title);

    void showError(String message);

    void showInfo(String message);

    void showAbout();

    void dispose();
}
