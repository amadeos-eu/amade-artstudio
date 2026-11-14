# amade-artstudio – Architecture

Generative art studio of amadeOS. It uses Java 25, Swing, Guice and [amade-commons](https://github.com/amadeos-eu/amade-commons), is built with Gradle and licensed under the EUPL-1.2. The structure follows amade-fractals.

Packaging:
- a Debian package
- an MSI and a portable app image for Windows, built with jpackage

Languages: English, German, French, Spanish and Italian. The language follows the amadeOS desktop.

## Key decisions

- **A picture is its recipe.** `Recipe` holds style, seed, palette, density, scale, chaos, line, paper and aspect. The renderer is a pure function of the recipe and the size, so the history, the variations, files and the clipboard only keep recipes. A recipe is a few lines of text (`toText`, `.artrecipe`) or one line (`toLine`, clipboard), and is also stored in every PNG Art Studio writes (a `tEXt` chunk), so a saved picture can be opened and changed again.
- **The same composition at every size.** Every length is measured in units of `min(width, height) / 600`, and every count (lines, circles, stones) depends on the shape of the picture, never on its pixels. The random numbers are drawn in the same order at every size. A poster at A2 is therefore the picture on the screen, only sharper (see `ArtRendererTest.largerPicturesKeepTheComposition`).
- **Chance that repeats.** Each style draws from a `SplittableRandom` seeded with the seed and the style, and from a `Noise` field (gradient noise, own implementation) seeded the same way. There is no global or time-dependent state.
- **One picture per thread, many pictures at once.** A picture is drawn on one worker thread; the variations (nine) and the style pictures (27) go to the worker pool together, one per core. Drawing checks a cancel flag regularly, so a newer change stops the old picture quickly.
- **Plain Java2D.** All styles paint with `Graphics2D` (antialiasing, `BasicStroke`, alpha) into a `TYPE_INT_RGB` image. Some styles work on pixels directly: the mosaic (nearest of some hundred points, sorted into buckets), the field styles (attractor, reaction patterns, marble, contour lines, night sky), the sculpture (a ray marched through a distance field) and the paper grain, a small change to every pixel. Pixel loops run row by row on all cores (`Sketch.eachRow`); they never use the random numbers, so the picture stays the same.
- **Fields on grids of their own size.** The attractor counts its points on a grid of at most 2.5 million cells, the reaction patterns run 5000 steps on a grid about 240 cells wide; both are scaled up smoothly. This keeps large exports fast and the picture the same at every size.
- **New styles go at the end.** The random numbers of a style are seeded with its place in `Style`, so new styles are appended and grouped for display with `Style.group()` and `Style.shown()`; old recipes keep their pictures.
- **Backdrops through the window manager's file.** *Set as Backdrop* writes a PNG with a new name into `~/.local/share/amadeos/art-studio/backdrops` (the window manager caches pictures by file name) and sets `workspace.N.wallpaper` in `~/.config/amadeos/wallpaper.conf` for the current workspace, read with `xprop -root _NET_CURRENT_DESKTOP`. All other keys of the file stay as they are, and pictures no longer named in it are deleted. Without a known workspace, the common backdrop changes.
- **Testable without a display.** The presenter, the recipe and the renderer have no Swing code. Executors are injected, so tests run drawing and continuations in a deterministic queue (`QueueExecutor`).

## Packages

| Package | Contents |
|---|---|
| `bootstrap` | `AmadeArtStudioApplication` (start through `AmadeApplication`), `ArtStudioModule` (Guice; worker pool, `RenderService`, texts, backdrops) |
| `domain` | `Style` (the 27 styles and their four groups), `Palette` (paper, ink and two to eight colours; six built in, own colours, desktop colours), `Aspect` (16:10, 16:9, square, paper portrait and landscape), `Recipe` (text form and parsing), `Variations` (nine relatives of a recipe) |
| `service.art` | `ArtRenderer` (size check, painter per style, paper grain, cancelling), `Sketch` (what a painter gets: image, graphics, random numbers, noise, units, sliders as 0..1), `LineStyles`, `AreaStyles`, `MosaicStyle`, `FieldStyles` (attractor, reaction patterns, marble, contour lines, night sky), `PatternStyles` (wheel curves, labyrinth, growth, triangles, halftone), `SolidStyles` (cubes, spheres, wireframe, tube knot, sculpture), `Noise` |
| `service.render` | `RenderService` (one picture or many in the background, results on the EDT, cancelled tasks never report) |
| `repository` | `PictureFiles` (PNG with recipe, `.artrecipe`, atomic writes), `Backdrops` (`wallpaper.conf` and the backdrop pictures), `ArtStudioPreferences` (`~/.config/amadeos/art-studio/art-studio.properties`: bars, keep colours, export size, last recipe) |
| `ui.main` | `MainPresenter` (all commands and the history, no Swing), `MainView`, `MainWindow`, `MainMenuBar`, `MainToolBar`, `StyleList`, `SettingsPanel` (seed, shape, colours, sliders), `HistoryStrip`, `ArtActions` (one action per command for menus and toolbar) |
| `ui.view` | `PictureView` (the picture with a shadow on the desk colour, or the three-by-three grid of variations) |
| `ui.icons` | `ArtGlyph` (the toolbar glyphs only Art Studio needs; `ToolIcon`, `StandardGlyph` and `ToolButtons` come from amade-commons) |

## Flows

- **Start**
  1. The window opens.
  2. The presenter takes the recipe named on the command line, else the last recipe, else a new *Flow* picture with a random seed.
- **Change**
  1. A new picture, a new seed, style or palette, a pasted or opened recipe and a picked variation add an entry to the history (40 entries; the entries after the current one are dropped).
  2. Sliders, paper and shape change the current entry, so that dragging a slider does not fill the history.
  3. Every change shows the recipe in the panels, stores it as the last recipe, and draws the picture for the size of the window (and the variations, if shown).
- **Drawing**
  1. `RenderService.render` runs on the worker pool; the result reaches the presenter through the `@Ui` executor (bound by amade-commons) and is dropped if the task was cancelled meanwhile.
  2. A smaller copy goes into the history.
  3. The style pictures in the list are drawn again only when the palette changes.
- **Export**
  1. Size dialog (screen, Full HD, QHD, 4K, A4 to A2 at 300 dpi in the shape of the picture, square, or custom), then the file chooser.
  2. Drawing in the background, then an atomic write with the recipe inside. Exports are limited to 50 megapixels and 12 000 pixels a side. A running export is confirmed before quitting.
- **Full screen** works as in amade-fractals: F11 hides menus and bars, Escape leaves full screen first, then the variations, then stops an export.
- **Theme and language**
  - Colours come from `AmadeUI` and follow Settings; the *Desktop Colours* palette follows a theme change.
  - Texts are in `messages*.properties`, checked by `TranslationCheck`, including a test against clashing mnemonics.

## Dependencies

- Guice, amade-commons, SLF4J/Logback.
- Everything else comes from the JDK: Java2D, ImageIO, Swing.

## Planned

- SVG export of the line styles
- Slowly moving pictures as a screen saver
- Text to picture with a local AI model, as an optional package
