package eu.amadeos.apps.artstudio.repository;

import eu.amadeos.apps.artstudio.domain.Recipe;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

/**
 * Pictures as PNG with the recipe inside, in a text chunk ({@value #KEYWORD}). Art Studio opens such a PNG as
 * the recipe again, so a picture can always be drawn anew at another size; other programs just show the
 * picture. Plain recipes are text files ({@code .artrecipe}).
 */
public final class PictureFiles {
    /** The keyword of the PNG text chunk that holds the recipe. */
    public static final String KEYWORD = "amadeOS Art Studio recipe";
    public static final String RECIPE_EXTENSION = "artrecipe";
    private static final String PNG_FORMAT = "javax_imageio_png_1.0";

    /** Writes the picture as PNG with the recipe; the file is replaced only when everything is written. */
    public void writePng(Path path, BufferedImage picture, Recipe recipe) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(picture),
                    param);
            IIOMetadataNode text = new IIOMetadataNode("tEXt");
            IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
            entry.setAttribute("keyword", KEYWORD);
            entry.setAttribute("value", recipe.toLine());
            text.appendChild(entry);
            IIOMetadataNode software = new IIOMetadataNode("tEXtEntry");
            software.setAttribute("keyword", "Software");
            software.setAttribute("value", "amadeOS Art Studio");
            text.appendChild(software);
            IIOMetadataNode root = new IIOMetadataNode(PNG_FORMAT);
            root.appendChild(text);
            metadata.mergeTree(PNG_FORMAT, root);
            Path temporary = temporary(path);
            try {
                Files.deleteIfExists(temporary);
                try (ImageOutputStream out = ImageIO.createImageOutputStream(temporary.toFile())) {
                    if (out == null) throw new IOException("Cannot write " + temporary);
                    writer.setOutput(out);
                    writer.write(null, new IIOImage(picture, null, metadata), param);
                }
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } finally {
            writer.dispose();
        }
    }

    /** The recipe inside a PNG; empty if the PNG has none. */
    public Optional<Recipe> readRecipeFromPng(Path path) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
            if (in == null) throw new IOException("Cannot read " + path);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Not a picture: " + path.getFileName());
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, false);
                IIOMetadata metadata = reader.getImageMetadata(0);
                if (metadata == null || !java.util.Arrays.asList(metadata.getMetadataFormatNames()).contains(PNG_FORMAT)) {
                    return Optional.empty();
                }
                return find(metadata.getAsTree(PNG_FORMAT));
            } finally {
                reader.dispose();
            }
        }
    }

    private static Optional<Recipe> find(Node node) {
        if ("tEXtEntry".equals(node.getNodeName())) {
            NamedNodeMap attributes = node.getAttributes();
            Node keyword = attributes.getNamedItem("keyword");
            Node value = attributes.getNamedItem("value");
            if (keyword != null && value != null && KEYWORD.equals(keyword.getNodeValue())) {
                try {
                    return Optional.of(Recipe.parse(value.getNodeValue()));
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            Optional<Recipe> found = find(child);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    public void writeRecipe(Path path, Recipe recipe) throws IOException {
        Path temporary = temporary(path);
        try {
            Files.writeString(temporary, recipe.toText());
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * The recipe in a recipe file or in a PNG Art Studio wrote.
     *
     * @throws IOException if the file cannot be read or holds no recipe
     */
    public Recipe readRecipe(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return readRecipeFromPng(path).orElseThrow(() -> new IOException("No recipe in " + path.getFileName()));
        }
        if (Files.size(path) > 64 * 1024) throw new IOException("Not a recipe: " + path.getFileName());
        try {
            return Recipe.parse(Files.readString(path));
        } catch (IllegalArgumentException | java.nio.charset.MalformedInputException e) {
            throw new IOException("Not a recipe: " + path.getFileName() + " (" + e.getMessage() + ")", e);
        }
    }

    private static Path temporary(Path path) {
        return path.resolveSibling("." + path.getFileName() + ".part");
    }
}
