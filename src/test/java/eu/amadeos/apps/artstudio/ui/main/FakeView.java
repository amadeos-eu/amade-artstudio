package eu.amadeos.apps.artstudio.ui.main;

import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences.Bar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

/** Records what the presenter shows and answers its questions from prepared values. */
class FakeView implements MainView {
    String title;
    Recipe recipe;
    boolean keepColors;
    final Map<Style, BufferedImage> styleThumbnails = new EnumMap<>(Style.class);
    BufferedImage picture;
    List<BufferedImage> variations;
    List<BufferedImage> history = List.of();
    int historyCurrent = -1;
    String status = "";
    boolean busy;
    Commands commands = Commands.NONE;
    Dimension canvas = new Dimension(232, 132);
    Dimension screen = new Dimension(320, 200);
    final Queue<Optional<Path>> paths = new ArrayDeque<>();
    Optional<Dimension> exportSize = Optional.empty();
    Dimension suggestedExportSize;
    Optional<Color> color = Optional.empty();
    BufferedImage copiedPicture;
    String copiedText;
    Optional<String> clipboard = Optional.empty();
    final Map<Bar, Boolean> bars = new EnumMap<>(Bar.class);
    boolean fullScreen;
    boolean confirmAnswer;
    int confirmations;
    final List<String> errors = new ArrayList<>();
    final List<String> infos = new ArrayList<>();
    final List<String> suggestedNames = new ArrayList<>();
    boolean aboutShown;
    boolean disposed;

    @Override
    public void showTitle(String title) {
        this.title = title;
    }

    @Override
    public void showRecipe(Recipe recipe, boolean keepColors) {
        this.recipe = recipe;
        this.keepColors = keepColors;
    }

    @Override
    public void showStyleThumbnail(Style style, BufferedImage thumbnail) {
        styleThumbnails.put(style, thumbnail);
    }

    @Override
    public void showPicture(BufferedImage picture) {
        this.picture = picture;
        this.variations = null;
    }

    @Override
    public void showVariations(List<BufferedImage> pictures) {
        this.variations = pictures;
    }

    @Override
    public void showHistory(List<BufferedImage> thumbnails, int current) {
        this.history = thumbnails;
        this.historyCurrent = current;
    }

    @Override
    public void showStatus(String text) {
        this.status = text;
    }

    @Override
    public void showBusy(boolean busy) {
        this.busy = busy;
    }

    @Override
    public void updateCommands(Commands commands) {
        this.commands = commands;
    }

    @Override
    public Dimension canvasSize() {
        return canvas;
    }

    @Override
    public Dimension screenSize() {
        return screen;
    }

    @Override
    public Optional<Path> chooseOpenPath() {
        return next();
    }

    @Override
    public Optional<Path> chooseRecipeSavePath(String suggestedName) {
        suggestedNames.add(suggestedName);
        return next();
    }

    @Override
    public Optional<Path> choosePictureSavePath(String suggestedName) {
        suggestedNames.add(suggestedName);
        return next();
    }

    private Optional<Path> next() {
        Optional<Path> p = paths.poll();
        return p == null ? Optional.empty() : p;
    }

    @Override
    public Optional<Dimension> chooseExportSize(Dimension suggested, Aspect aspect) {
        suggestedExportSize = suggested;
        return exportSize;
    }

    @Override
    public Optional<Color> chooseColor(String title, Color initial) {
        return color;
    }

    @Override
    public void copyToClipboard(BufferedImage picture) {
        copiedPicture = picture;
    }

    @Override
    public void copyToClipboard(String text) {
        copiedText = text;
    }

    @Override
    public Optional<String> clipboardText() {
        return clipboard;
    }

    @Override
    public void showBar(Bar bar, boolean visible) {
        bars.put(bar, visible);
    }

    @Override
    public void setFullScreen(boolean fullScreen) {
        this.fullScreen = fullScreen;
    }

    @Override
    public boolean confirm(String message, String title) {
        confirmations++;
        return confirmAnswer;
    }

    @Override
    public void showError(String message) {
        errors.add(message);
    }

    @Override
    public void showInfo(String message) {
        infos.add(message);
    }

    @Override
    public void showAbout() {
        aboutShown = true;
    }

    @Override
    public void dispose() {
        disposed = true;
    }
}
