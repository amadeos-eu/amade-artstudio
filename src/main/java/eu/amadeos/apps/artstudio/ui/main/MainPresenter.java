package eu.amadeos.apps.artstudio.ui.main;

import com.google.inject.Inject;
import eu.amadeos.commons.app.Ui;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;
import eu.amadeos.apps.artstudio.domain.Aspect;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.domain.Variations;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences.Bar;
import eu.amadeos.apps.artstudio.repository.Backdrops;
import eu.amadeos.apps.artstudio.repository.PictureFiles;
import eu.amadeos.apps.artstudio.service.render.RenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Everything the window does: the current recipe and its picture, the history, the variations, files,
 * backdrop and export. Runs on the event dispatch thread; drawing and writing files happen in the background.
 */
public final class MainPresenter {
    private static final Logger LOG = LoggerFactory.getLogger(MainPresenter.class);
    /** Pictures kept in the history. */
    static final int HISTORY_SIZE = 40;
    /** The size of a history picture. */
    static final int THUMB_WIDTH = 96;
    static final int THUMB_HEIGHT = 60;
    static final int STYLE_THUMB_WIDTH = 64;
    static final int STYLE_THUMB_HEIGHT = 40;
    /** Space around the picture in the window. */
    static final int MARGIN = 16;

    private record Entry(Recipe recipe, BufferedImage thumbnail) {
    }

    private final MainView view;
    private final Messages msg;
    private final LocaleSettings locales;
    private final ArtStudioPreferences preferences;
    private final RenderService renders;
    private final PictureFiles files;
    private final Backdrops backdrops;
    private final Executor background;
    private final Executor ui;
    private final Supplier<DesktopTheme> theme;
    private final LongSupplier seeds;

    private Recipe recipe;
    private final List<Entry> history = new ArrayList<>();
    private int current = -1;
    private BufferedImage picture;
    private RenderService.Task task;
    private RenderService.Task thumbTask;
    private RenderService.Task variationTask;
    private RenderService.Task exportTask;
    private List<Recipe> variations;
    private BufferedImage[] variationPictures;
    private Palette thumbPalette;
    private int savesRunning;
    private boolean fullScreen;

    @Inject
    public MainPresenter(MainView view, Messages msg, LocaleSettings locales, ArtStudioPreferences preferences,
                         RenderService renders, PictureFiles files, Backdrops backdrops, ExecutorService background,
                         @Ui Executor ui) {
        this(view, msg, locales, preferences, renders, files, backdrops, background, ui, AmadeUI::theme,
                randomSeeds());
    }

    MainPresenter(MainView view, Messages msg, LocaleSettings locales, ArtStudioPreferences preferences,
                  RenderService renders, PictureFiles files, Backdrops backdrops, Executor background, Executor ui,
                  Supplier<DesktopTheme> theme, LongSupplier seeds) {
        this.view = view;
        this.msg = msg;
        this.locales = locales;
        this.preferences = preferences;
        this.renders = renders;
        this.files = files;
        this.backdrops = backdrops;
        this.background = background;
        this.ui = ui;
        this.theme = theme;
        this.seeds = seeds;
    }

    private static LongSupplier randomSeeds() {
        SplittableRandom random = new SplittableRandom();
        return () -> random.nextLong(Recipe.MAX_SEED + 1);
    }

    /** Opens the recipe or PNG named as first argument, else the last picture, else a new one. */
    public void start(List<String> arguments) {
        Recipe first = preferences.lastRecipe()
                .orElseGet(() -> Recipe.of(Style.FLOW, seeds.getAsLong(), Palette.AUTUMN));
        push(first);
        if (!arguments.isEmpty()) open(Path.of(arguments.getFirst()));
    }

    public Recipe recipe() {
        return recipe;
    }

    // ---------------------------------------------------------------- the picture

    /** The window has a new size: draw the picture to fit it. */
    public void canvasResized(int width, int height) {
        if (recipe == null) return;
        render();
        if (variations != null) renderVariations();
    }

    private void render() {
        if (task != null) task.cancel();
        Dimension size = pictureSize();
        Recipe drawn = recipe;
        view.showBusy(true);
        task = renders.render(drawn, size.width, size.height, new RenderService.Callback() {
            @Override
            public void done(BufferedImage result, Duration time) {
                task = null;
                picture = result;
                if (variations == null) view.showPicture(result);
                setThumbnail(drawn, result);
                view.showStatus(msg.format("status.drawn", msg.get(drawn.style().key()), drawn.seed(),
                        result.getWidth(), result.getHeight(), seconds(time)));
                busyIfNeeded();
                updateCommands();
            }

            @Override
            public void failed(Throwable error) {
                task = null;
                LOG.warn("Could not draw {}", drawn.toLine(), error);
                view.showStatus(msg.format("error.draw", describe(error)));
                busyIfNeeded();
                updateCommands();
            }
        });
        updateCommands();
    }

    /** The largest picture of the recipe's shape that fits into the window. */
    Dimension pictureSize() {
        Dimension canvas = view.canvasSize();
        int margin = fullScreen ? 0 : MARGIN;
        return recipe.aspect().fit(Math.max(16, canvas.width - 2 * margin), Math.max(16, canvas.height - 2 * margin));
    }

    private void setThumbnail(Recipe drawn, BufferedImage result) {
        if (current < 0 || !history.get(current).recipe().equals(drawn)) return;
        history.set(current, new Entry(drawn, scaled(result, THUMB_WIDTH, THUMB_HEIGHT)));
        showHistory();
    }

    static BufferedImage scaled(BufferedImage source, int maxWidth, int maxHeight) {
        double f = Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight());
        int w = Math.max(1, (int) Math.round(source.getWidth() * f));
        int h = Math.max(1, (int) Math.round(source.getHeight() * f));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // Halve in steps, so that fine lines do not vanish.
        BufferedImage step = source;
        while (step.getWidth() / 2 >= w && step.getHeight() / 2 >= h) {
            BufferedImage half = new BufferedImage(step.getWidth() / 2, step.getHeight() / 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D hg = half.createGraphics();
            hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            hg.drawImage(step, 0, 0, half.getWidth(), half.getHeight(), null);
            hg.dispose();
            step = half;
        }
        g.drawImage(step, 0, 0, w, h, null);
        g.dispose();
        return small;
    }

    /** The small pictures of the styles, in the current colours. */
    private void renderStyleThumbnails() {
        if (recipe.palette().equals(thumbPalette)) return;
        thumbPalette = recipe.palette();
        if (thumbTask != null) thumbTask.cancel();
        List<Recipe> all = new ArrayList<>();
        for (Style s : Style.values()) {
            all.add(new Recipe(s, 1000 + s.ordinal(), thumbPalette, 50, 50, 50, 50, false, Aspect.SCREEN));
        }
        thumbTask = renders.renderAll(all, STYLE_THUMB_WIDTH, STYLE_THUMB_HEIGHT, new RenderService.ManyCallback() {
            @Override
            public void done(int index, BufferedImage thumbnail) {
                view.showStyleThumbnail(Style.values()[index], thumbnail);
            }

            @Override
            public void allDone(Duration time) {
                thumbTask = null;
            }

            @Override
            public void failed(Throwable error) {
                thumbTask = null;
                LOG.warn("Could not draw the style pictures", error);
            }
        });
    }

    // ---------------------------------------------------------------- changing the recipe

    /** A new picture: a new seed and, unless the colours are kept, a palette by chance. */
    public void newPicture() {
        Recipe next = recipe.withSeed(seeds.getAsLong());
        if (!preferences.keepColors() && !recipe.palette().id().equals(Palette.CUSTOM)) {
            List<Palette> others = new ArrayList<>(Palette.BUILT_IN);
            others.remove(recipe.palette());
            next = next.withPalette(others.get((int) Math.floorMod(seeds.getAsLong(), (long) others.size())));
        }
        push(next);
    }

    /** Another seed, everything else stays. */
    public void randomSeed() {
        push(recipe.withSeed(seeds.getAsLong()));
    }

    /** The seed typed in; false (and nothing changes) if it is not a number from 0 to 999 999 999. */
    public boolean setSeed(String text) {
        long seed;
        try {
            seed = Long.parseLong(text.strip().replace(" ", "").replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            view.showStatus(msg.format("error.seed", Recipe.MAX_SEED));
            return false;
        }
        if (seed < 0 || seed > Recipe.MAX_SEED) {
            view.showStatus(msg.format("error.seed", Recipe.MAX_SEED));
            return false;
        }
        if (seed != recipe.seed()) push(recipe.withSeed(seed));
        return true;
    }

    public void selectStyle(Style style) {
        if (style != recipe.style()) push(recipe.withStyle(style));
    }

    public void setPalette(Palette palette) {
        if (!palette.equals(recipe.palette())) push(recipe.withPalette(palette));
    }

    /** The palette from the colours of the desktop theme. */
    public void useDesktopColors() {
        setPalette(desktopPalette(theme.get()));
    }

    /** The desktop theme's colours as a palette: backdrop and text as paper and ink, the rest as colours. */
    static Palette desktopPalette(DesktopTheme t) {
        List<Color> colors = new ArrayList<>();
        for (Color c : List.of(t.activeTitleBg(), t.selectionBackground(), t.highlight(), t.workspaceColor(0),
                t.workspaceColor(1), t.workspaceColor(2), t.workspaceColor(3), t.shadow())) {
            Color opaque = new Color(c.getRGB() & 0xFFFFFF);
            if (!colors.contains(opaque)) colors.add(opaque);
        }
        while (colors.size() < Palette.MIN_COLORS) colors.add(new Color(t.darkShadow().getRGB() & 0xFFFFFF));
        return new Palette(Palette.DESKTOP, new Color(t.base().getRGB() & 0xFFFFFF),
                new Color(t.darkShadow().getRGB() & 0xFFFFFF), colors.subList(0, Math.min(colors.size(), Palette.MAX_COLORS)));
    }

    /** Lets the user pick colour {@code index} (0 paper, 1 ink, then the colours). */
    public void editSwatch(int index) {
        List<Color> swatches = recipe.palette().swatches();
        if (index < 0 || index >= swatches.size()) return;
        String title = msg.get(index == 0 ? "color.paper" : index == 1 ? "color.ink" : "color.color");
        view.chooseColor(title, swatches.get(index)).ifPresent(c -> {
            if (!c.equals(swatches.get(index))) push(recipe.withPalette(recipe.palette().withSwatch(index, c)));
        });
    }

    public void setDensity(int value) {
        change(recipe.withDensity(value));
    }

    public void setScale(int value) {
        change(recipe.withScale(value));
    }

    public void setChaos(int value) {
        change(recipe.withChaos(value));
    }

    public void setLine(int value) {
        change(recipe.withLine(value));
    }

    public void setPaper(boolean paper) {
        change(recipe.withPaper(paper));
    }

    public void setAspect(Aspect aspect) {
        change(recipe.withAspect(aspect));
    }

    public void setKeepColors(boolean keep) {
        remember(() -> preferences.setKeepColors(keep));
    }

    public boolean keepColors() {
        return preferences.keepColors();
    }

    /** A new picture in the history; the pictures after the current one are dropped. */
    private void push(Recipe next) {
        while (history.size() > current + 1) history.removeLast();
        history.add(new Entry(next, null));
        while (history.size() > HISTORY_SIZE) history.removeFirst();
        current = history.size() - 1;
        apply(next);
    }

    /** A slider moved: the current picture changes, the history gets no new entry. */
    private void change(Recipe next) {
        if (next.equals(recipe)) return;
        Entry old = history.get(current);
        history.set(current, new Entry(next, old.thumbnail()));
        apply(next);
    }

    private void apply(Recipe next) {
        recipe = next;
        view.showRecipe(next, preferences.keepColors());
        updateTitle();
        showHistory();
        remember(() -> preferences.setLastRecipe(next));
        renderStyleThumbnails();
        render();
        if (variations != null) renderVariations();
    }

    private void showHistory() {
        List<BufferedImage> thumbs = new ArrayList<>(history.size());
        for (Entry e : history) thumbs.add(e.thumbnail());
        view.showHistory(thumbs, current);
    }

    // ---------------------------------------------------------------- history

    public void goBack() {
        goToHistory(current - 1);
    }

    public void goForward() {
        goToHistory(current + 1);
    }

    public void goToHistory(int index) {
        if (index < 0 || index >= history.size() || index == current) return;
        current = index;
        apply(history.get(index).recipe());
    }

    // ---------------------------------------------------------------- variations

    public void toggleVariations() {
        if (variations != null) {
            hideVariations();
        } else {
            renderVariations();
        }
    }

    public boolean variationsShown() {
        return variations != null;
    }

    private void hideVariations() {
        if (variationTask != null) variationTask.cancel();
        variationTask = null;
        variations = null;
        variationPictures = null;
        if (picture != null) view.showPicture(picture);
        busyIfNeeded();
        updateCommands();
    }

    private void renderVariations() {
        if (variationTask != null) variationTask.cancel();
        variations = Variations.around(recipe);
        variationPictures = new BufferedImage[Variations.COUNT];
        view.showVariations(Arrays.asList(variationPictures.clone()));
        Dimension canvas = view.canvasSize();
        Dimension cell = recipe.aspect().fit(Math.max(16, (canvas.width - 4 * MARGIN) / 3),
                Math.max(16, (canvas.height - 4 * MARGIN) / 3));
        view.showBusy(true);
        BufferedImage[] target = variationPictures;
        variationTask = renders.renderAll(variations, cell.width, cell.height, new RenderService.ManyCallback() {
            @Override
            public void done(int index, BufferedImage result) {
                target[index] = result;
                if (variationPictures == target) view.showVariations(Arrays.asList(target.clone()));
            }

            @Override
            public void allDone(Duration time) {
                variationTask = null;
                view.showStatus(msg.format("status.variations", msg.get(recipe.style().key()), seconds(time)));
                busyIfNeeded();
                updateCommands();
            }

            @Override
            public void failed(Throwable error) {
                variationTask = null;
                LOG.warn("Could not draw the variations", error);
                view.showStatus(msg.format("error.draw", describe(error)));
                busyIfNeeded();
            }
        });
        updateCommands();
    }

    /** Continues from variation {@code index}: it becomes the picture, with new variations around it. */
    public void pickVariation(int index) {
        if (variations == null || index < 0 || index >= variations.size()) return;
        Recipe picked = variations.get(index);
        if (index == Variations.MIDDLE || picked.equals(recipe)) return;
        push(picked);
    }

    // ---------------------------------------------------------------- files

    public void openFile() {
        view.chooseOpenPath().ifPresent(this::open);
    }

    public void open(Path path) {
        view.showBusy(true);
        background.execute(() -> {
            try {
                Recipe opened = files.readRecipe(path);
                ui.execute(() -> {
                    push(opened);
                    view.showStatus(msg.format("status.opened", path.getFileName()));
                });
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not open {}", path, e);
                ui.execute(() -> {
                    busyIfNeeded();
                    view.showError(msg.format("error.open", path.getFileName(), describe(e)));
                });
            }
        });
    }

    public void saveRecipe() {
        view.chooseRecipeSavePath(recipe.fileName() + "." + PictureFiles.RECIPE_EXTENSION).ifPresent(path -> {
            Recipe saved = recipe;
            write(path, () -> files.writeRecipe(path, saved));
        });
    }

    /** Saves the picture as shown, with the recipe inside. */
    public void savePicture() {
        if (picture == null) return;
        BufferedImage saved = picture;
        Recipe savedRecipe = recipe;
        view.choosePictureSavePath(recipe.fileName() + ".png")
                .ifPresent(path -> write(path, () -> files.writePng(path, saved, savedRecipe)));
    }

    private interface Write {
        void run() throws IOException;
    }

    private void write(Path path, Write write) {
        savesRunning++;
        view.showBusy(true);
        background.execute(() -> {
            try {
                write.run();
                ui.execute(() -> {
                    savesRunning--;
                    busyIfNeeded();
                    view.showStatus(msg.format("status.saved", path.getFileName()));
                });
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not save {}", path, e);
                ui.execute(() -> {
                    savesRunning--;
                    busyIfNeeded();
                    view.showError(msg.format("error.save", path.getFileName(), describe(e)));
                });
            }
        });
    }

    /** Draws the picture at a size of the user's choice and saves it as PNG. */
    public void exportPicture() {
        if (exportTask != null) return;
        Dimension suggested = new Dimension(preferences.exportWidth(), preferences.exportHeight());
        Optional<Dimension> chosen = view.chooseExportSize(suggested, recipe.aspect());
        if (chosen.isEmpty()) return;
        Dimension size = chosen.get();
        remember(() -> preferences.setExportSize(size.width, size.height));
        Optional<Path> target = view.choosePictureSavePath(recipe.fileName() + "-" + size.width + "x" + size.height
                + ".png");
        if (target.isEmpty()) return;
        Path path = target.get();
        Recipe exported = recipe;
        view.showStatus(msg.format("status.exporting", size.width, size.height));
        view.showBusy(true);
        exportTask = renders.render(exported, size.width, size.height, new RenderService.Callback() {
            @Override
            public void done(BufferedImage result, Duration time) {
                exportTask = null;
                updateCommands();
                write(path, () -> files.writePng(path, result, exported));
            }

            @Override
            public void failed(Throwable error) {
                exportTask = null;
                busyIfNeeded();
                updateCommands();
                LOG.warn("Could not export", error);
                view.showError(msg.format("error.export", describe(error)));
            }
        });
        updateCommands();
    }

    /** Stops a running export. */
    public void stop() {
        if (exportTask == null) return;
        exportTask.cancel();
        exportTask = null;
        view.showStatus(msg.get("status.stopped"));
        busyIfNeeded();
        updateCommands();
    }

    /** Draws the picture at the size of the screen and makes it the backdrop of the current workspace. */
    public void setAsBackdrop() {
        Dimension screen = view.screenSize();
        Recipe drawn = recipe;
        view.showBusy(true);
        savesRunning++;
        renders.render(drawn, screen.width, screen.height, new RenderService.Callback() {
            @Override
            public void done(BufferedImage result, Duration time) {
                background.execute(() -> {
                    try {
                        int workspace = backdrops.apply(result, drawn);
                        ui.execute(() -> {
                            savesRunning--;
                            busyIfNeeded();
                            view.showStatus(workspace >= 0 ? msg.format("status.backdrop.workspace", workspace + 1)
                                    : msg.get("status.backdrop.all"));
                        });
                    } catch (IOException | RuntimeException e) {
                        LOG.warn("Could not set the backdrop", e);
                        ui.execute(() -> {
                            savesRunning--;
                            busyIfNeeded();
                            view.showError(msg.format("error.backdrop", describe(e)));
                        });
                    }
                });
            }

            @Override
            public void failed(Throwable error) {
                savesRunning--;
                busyIfNeeded();
                view.showError(msg.format("error.backdrop", describe(error)));
            }
        });
    }

    public void copyPicture() {
        if (picture != null) {
            view.copyToClipboard(picture);
            view.showStatus(msg.get("status.copied-picture"));
        }
    }

    public void copyRecipe() {
        view.copyToClipboard(recipe.toLine());
        view.showStatus(msg.get("status.copied-recipe"));
    }

    /** A recipe from the clipboard, e.g. copied from another Art Studio or from a mail. */
    public void pasteRecipe() {
        Optional<String> text = view.clipboardText();
        try {
            push(Recipe.parse(text.orElse("")));
            view.showStatus(msg.get("status.pasted"));
        } catch (IllegalArgumentException e) {
            view.showStatus(msg.get("error.paste"));
        }
    }

    // ---------------------------------------------------------------- window

    public void toggleFullScreen() {
        setFullScreen(!fullScreen);
    }

    public void setFullScreen(boolean on) {
        if (on == fullScreen) return;
        fullScreen = on;
        view.setFullScreen(on);
        updateCommands();
    }

    /** Escape: leaves full screen, else the variations, else stops an export. */
    public void escape() {
        if (fullScreen) setFullScreen(false);
        else if (variations != null) hideVariations();
        else stop();
    }

    public boolean isBarVisible(Bar bar) {
        return preferences.isVisible(bar);
    }

    public void setBarVisible(Bar bar, boolean visible) {
        view.showBar(bar, visible);
        remember(() -> preferences.setVisible(bar, visible));
    }

    /** The colour theme changed: the desktop palette follows it. */
    public void themeChanged() {
        if (recipe != null && recipe.palette().id().equals(Palette.DESKTOP)) {
            Palette now = desktopPalette(theme.get());
            if (!now.equals(recipe.palette())) change(recipe.withPalette(now));
        }
    }

    public void setLanguage(Optional<Language> language) {
        try {
            locales.setApplicationLanguage(language);
        } catch (IOException e) {
            LOG.warn("Could not save the language", e);
            view.showError(msg.format("error.save-language", e.getMessage()));
            return;
        }
        if (locales.restartRequired()) {
            view.showInfo(Messages.commons(msg.locale()).format("language.restart", "amade-artstudio"));
        }
    }

    public void about() {
        view.showAbout();
    }

    /** Closes the window; a running export is confirmed first. */
    public void exit() {
        if (exportTask != null && !view.confirm(msg.get("dialog.exit-export.message"),
                msg.get("dialog.exit-export.title"))) {
            return;
        }
        for (RenderService.Task t : new RenderService.Task[]{task, thumbTask, variationTask, exportTask}) {
            if (t != null) t.cancel();
        }
        task = null;
        thumbTask = null;
        variationTask = null;
        exportTask = null;
        view.dispose();
    }

    // ---------------------------------------------------------------- helpers

    private void updateTitle() {
        view.showTitle(msg.format("window.title.picture", msg.get(recipe.style().key()), recipe.seed()));
    }

    private void busyIfNeeded() {
        view.showBusy(task != null || variationTask != null || exportTask != null || savesRunning > 0);
    }

    private void updateCommands() {
        view.updateCommands(new MainView.Commands(task != null, picture != null, current > 0,
                current < history.size() - 1, variations != null, exportTask != null, fullScreen));
    }

    /** Stores a choice; a full disk is only logged, the choice still applies until the program ends. */
    private void remember(Runnable store) {
        try {
            store.run();
        } catch (UncheckedIOException e) {
            LOG.warn("Could not save the preferences", e);
        }
    }

    private String seconds(Duration time) {
        return msg.format("time.seconds", time.toMillis() / 1000.0);
    }

    private String describe(Throwable e) {
        Throwable cause = e instanceof UncheckedIOException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof OutOfMemoryError) return msg.get("error.memory");
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
