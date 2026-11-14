package eu.amadeos.apps.artstudio.service.render;

import eu.amadeos.apps.artstudio.QueueExecutor;
import eu.amadeos.apps.artstudio.domain.Palette;
import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.domain.Style;
import eu.amadeos.apps.artstudio.service.art.ArtRenderer;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderServiceTest {
    private final QueueExecutor executor = new QueueExecutor();
    private final RenderService service = new RenderService(new ArtRenderer(), executor, executor);
    private final List<String> events = new ArrayList<>();

    private final RenderService.Callback callback = new RenderService.Callback() {
        @Override
        public void done(BufferedImage picture, Duration time) {
            events.add("done " + picture.getWidth() + "x" + picture.getHeight());
        }

        @Override
        public void failed(Throwable error) {
            events.add("failed " + error.getClass().getSimpleName());
        }
    };

    private static Recipe grid() {
        return Recipe.of(Style.GRID, 1, Palette.PRIMARY);
    }

    @Test
    void reportsThePicture() {
        service.render(grid(), 40, 25, callback);
        assertTrue(events.isEmpty(), "nothing happens before the background runs");
        executor.runAll();
        assertEquals(List.of("done 40x25"), events);
    }

    @Test
    void aCancelledPictureIsNeverReported() {
        RenderService.Task task = service.render(grid(), 40, 25, callback);
        task.cancel();
        executor.runAll();
        assertTrue(events.isEmpty());

        // Cancelled after drawing, before the result reached the user interface thread.
        RenderService.Task late = service.render(grid(), 40, 25, callback);
        executor.runNext();
        late.cancel();
        executor.runAll();
        assertTrue(events.isEmpty());
    }

    @Test
    void failuresAreReported() {
        service.render(grid(), 0, 25, callback);
        executor.runAll();
        assertEquals(List.of("failed IllegalArgumentException"), events);
    }

    @Test
    void severalPictures() {
        List<Recipe> recipes = List.of(grid(), grid().withSeed(2), grid().withSeed(3));
        BufferedImage[] got = new BufferedImage[3];
        service.renderAll(recipes, 30, 20, new RenderService.ManyCallback() {
            @Override
            public void done(int index, BufferedImage picture) {
                got[index] = picture;
                events.add("one");
            }

            @Override
            public void allDone(Duration time) {
                events.add("all");
            }

            @Override
            public void failed(Throwable error) {
                events.add("failed");
            }
        });
        executor.runAll();
        assertEquals(List.of("one", "one", "one", "all"), events);
        for (BufferedImage p : got) assertNotNull(p);

        events.clear();
        RenderService.Task task = service.renderAll(recipes, 30, 20, new RenderService.ManyCallback() {
            @Override
            public void done(int index, BufferedImage picture) {
                events.add("one");
            }

            @Override
            public void allDone(Duration time) {
                events.add("all");
            }

            @Override
            public void failed(Throwable error) {
                events.add("failed");
            }
        });
        executor.runNext();
        task.cancel();
        executor.runAll();
        assertTrue(events.isEmpty());
    }

    @Test
    void severalPicturesReportOneFailure() {
        List<String> failures = new ArrayList<>();
        service.renderAll(List.of(grid(), grid()), 0, 0, new RenderService.ManyCallback() {
            @Override
            public void done(int index, BufferedImage picture) {
            }

            @Override
            public void allDone(Duration time) {
            }

            @Override
            public void failed(Throwable error) {
                failures.add(error.getMessage());
            }
        });
        executor.runAll();
        assertEquals(1, failures.size());
    }
}
