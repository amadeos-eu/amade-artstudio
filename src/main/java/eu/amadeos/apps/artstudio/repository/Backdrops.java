package eu.amadeos.apps.artstudio.repository;

import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes a picture the backdrop of the current workspace, the way Settings &gt; Backdrop does: the picture goes
 * to {@code ~/.local/share/amadeos/art-studio/backdrops/}, and {@code ~/.config/amadeos/wallpaper.conf} gets
 * {@code workspace.N.wallpaper}, {@code .mode} and {@code .color}. The window manager reads that file on every
 * workspace switch and, from amadeos-system with the backdrop watch, as soon as it changes.
 * <p>
 * Every backdrop gets a file name of its own, because the window manager keeps one picture per file name.
 * Pictures no workspace uses any more are deleted.
 */
public final class Backdrops {
    public static final String CONFIG = "wallpaper.conf";
    private static final Pattern CURRENT_DESKTOP = Pattern.compile("=\\s*(\\d+)");

    private final Path configFile;
    private final Path directory;
    private final IntSupplier currentWorkspace;

    /**
     * @param amadeosConfig    {@code ~/.config/amadeos}
     * @param directory        where the pictures go
     * @param currentWorkspace the current workspace from 0, or -1 if unknown
     */
    public Backdrops(Path amadeosConfig, Path directory, IntSupplier currentWorkspace) {
        this.configFile = amadeosConfig.resolve(CONFIG);
        this.directory = directory;
        this.currentWorkspace = currentWorkspace;
    }

    /** The real folders and the current workspace from the window manager ({@code _NET_CURRENT_DESKTOP}). */
    public static Backdrops system(XdgDirectories xdg, CommandRunner runner) {
        return new Backdrops(xdg.amadeosConfig(), xdg.dataHome().resolve("amadeos/art-studio/backdrops"),
                () -> currentWorkspace(runner));
    }

    /** The current workspace as {@code xprop} reads it from the root window; -1 if that fails. */
    static int currentWorkspace(CommandRunner runner) {
        try {
            return parseWorkspace(runner.output(List.of("xprop", "-root", "_NET_CURRENT_DESKTOP"),
                    Duration.ofSeconds(3)));
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** "_NET_CURRENT_DESKTOP(CARDINAL) = 2" gives 2; anything else -1. */
    static int parseWorkspace(String xpropOutput) {
        Matcher m = CURRENT_DESKTOP.matcher(xpropOutput == null ? "" : xpropOutput);
        if (!m.find()) return -1;
        try {
            int n = Integer.parseInt(m.group(1));
            return n >= 0 && n < 32 ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Stores the picture and makes it the backdrop.
     *
     * @return the workspace that got it (from 0), or -1 for the common backdrop of all workspaces without one
     *         of their own
     */
    public int apply(BufferedImage picture, Recipe recipe) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("backdrop-" + recipe.fileName() + "-" + System.currentTimeMillis() + ".png");
        new PictureFiles().writePng(file, picture, recipe);
        int workspace = currentWorkspace.getAsInt();
        Properties config = PropertiesFile.load(configFile);
        String prefix = workspace >= 0 ? "workspace." + (workspace + 1) + "." : "";
        if (workspace >= 0 && config.getProperty("wallpaper") == null) {
            // Like the Settings: the other workspaces keep what they show now, the amadeOS backdrop.
            config.setProperty("wallpaper", "/usr/share/backgrounds/amadeos/wallpaper.png");
            config.setProperty("mode", "FILL");
        }
        config.setProperty(prefix + "wallpaper", file.toString());
        config.setProperty(prefix + "mode", "FILL");
        config.setProperty(prefix + "color", Palette.hex(recipe.palette().paper()));
        Files.createDirectories(configFile.getParent());
        PropertiesFile.store(configFile, config, "amadeOS Desktop Wallpaper Settings");
        removeUnused(config);
        return workspace;
    }

    /** Deletes the pictures in the folder that no workspace shows any more. */
    private void removeUnused(Properties config) {
        Set<String> used = new java.util.HashSet<>();
        for (String key : config.stringPropertyNames()) {
            if (key.endsWith("wallpaper")) used.add(config.getProperty(key));
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "backdrop-*.png")) {
            for (Path f : files) {
                if (!used.contains(f.toString())) Files.deleteIfExists(f);
            }
        } catch (IOException e) {
            // an old picture more is no harm
        }
    }
}
