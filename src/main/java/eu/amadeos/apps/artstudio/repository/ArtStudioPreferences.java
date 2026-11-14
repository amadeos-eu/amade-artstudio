package eu.amadeos.apps.artstudio.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.apps.artstudio.domain.Recipe;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Settings of Art Studio in {@code ~/.config/amadeos/art-studio/art-studio.properties}: which parts the window
 * shows, whether new pictures keep their colours, the size of the last export and the last picture, which
 * comes back at the next start. Each change is written at once.
 */
public class ArtStudioPreferences {
    public static final String FILE = "art-studio.properties";
    public static final int DEFAULT_EXPORT_WIDTH = 3840;
    public static final int DEFAULT_EXPORT_HEIGHT = 2400;
    public static final int MAX_EXPORT_SIDE = 12_000;

    /** The parts of the window that can be hidden. */
    public enum Bar {
        TOOLBAR, STYLES, SETTINGS, HISTORY, STATUS_BAR;

        public String key() {
            return "show." + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private final Path file;
    private final Properties properties;

    @Inject
    public ArtStudioPreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public ArtStudioPreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public boolean isVisible(Bar bar) {
        return !"false".equals(properties.getProperty(bar.key()));
    }

    public void setVisible(Bar bar, boolean visible) {
        set(bar.key(), String.valueOf(visible));
    }

    /** Whether "New Picture" keeps the palette; off unless switched on, so new pictures bring new colours. */
    public boolean keepColors() {
        return "true".equals(properties.getProperty("new.keep-colors"));
    }

    public void setKeepColors(boolean keep) {
        set("new.keep-colors", String.valueOf(keep));
    }

    public int exportWidth() {
        return intValue("export.width", DEFAULT_EXPORT_WIDTH);
    }

    public int exportHeight() {
        return intValue("export.height", DEFAULT_EXPORT_HEIGHT);
    }

    public void setExportSize(int width, int height) {
        set("export.width", String.valueOf(width));
        set("export.height", String.valueOf(height));
    }

    /** The picture shown when the program ended; empty at the first start or if it cannot be read. */
    public Optional<Recipe> lastRecipe() {
        String line = properties.getProperty("last.recipe");
        if (line == null || line.isBlank()) return Optional.empty();
        try {
            return Optional.of(Recipe.parse(line));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void setLastRecipe(Recipe recipe) {
        set("last.recipe", recipe.toLine());
    }

    private int intValue(String key, int fallback) {
        try {
            int value = Integer.parseInt(properties.getProperty(key, "").strip());
            return value < 16 || value > MAX_EXPORT_SIDE ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Stores the value at once.
     *
     * @throws java.io.UncheckedIOException if the file cannot be written
     */
    private void set(String key, String value) {
        if (value.equals(properties.getProperty(key))) return;
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-artstudio");
    }
}
