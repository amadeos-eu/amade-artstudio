package eu.amadeos.apps.artstudio.service.render;

import eu.amadeos.apps.artstudio.domain.Recipe;
import eu.amadeos.apps.artstudio.service.art.ArtRenderer;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Draws pictures in the background and hands them to the user interface thread. A task that was cancelled
 * reports nothing, so a picture that is no longer wanted never replaces a newer one.
 */
public final class RenderService {
    private final ArtRenderer renderer;
    private final Executor background;
    private final Executor ui;

    public RenderService(ArtRenderer renderer, Executor background, Executor ui) {
        this.renderer = renderer;
        this.background = background;
        this.ui = ui;
    }

    /** What a task reports, on the user interface thread. */
    public interface Callback {
        void done(BufferedImage picture, Duration time);

        void failed(Throwable error);
    }

    /** What a task of several pictures reports, on the user interface thread. */
    public interface ManyCallback {
        /** Picture {@code index} is ready; the pictures come in any order. */
        void done(int index, BufferedImage picture);

        /** All are ready. */
        void allDone(Duration time);

        void failed(Throwable error);
    }

    /** A running drawing. */
    public static final class Task {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        /** Nothing more is reported; the drawing stops at its next check. */
        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    public Task render(Recipe recipe, int width, int height, Callback callback) {
        Task task = new Task();
        background.execute(() -> {
            if (task.isCancelled()) return;
            long start = System.nanoTime();
            try {
                BufferedImage picture = renderer.render(recipe, width, height, task::isCancelled);
                Duration time = Duration.ofNanos(System.nanoTime() - start);
                ui.execute(() -> {
                    if (!task.isCancelled()) callback.done(picture, time);
                });
            } catch (CancellationException e) {
                // nobody waits for it any more
            } catch (RuntimeException | OutOfMemoryError e) {
                ui.execute(() -> {
                    if (!task.isCancelled()) callback.failed(e);
                });
            }
        });
        return task;
    }

    /** Draws several pictures of the same size side by side, each as its own background job. */
    public Task renderAll(List<Recipe> recipes, int width, int height, ManyCallback callback) {
        Task task = new Task();
        long start = System.nanoTime();
        AtomicInteger left = new AtomicInteger(recipes.size());
        AtomicBoolean failed = new AtomicBoolean();
        for (int i = 0; i < recipes.size(); i++) {
            int index = i;
            Recipe recipe = recipes.get(i);
            background.execute(() -> {
                if (task.isCancelled() || failed.get()) return;
                try {
                    BufferedImage picture = renderer.render(recipe, width, height, task::isCancelled);
                    boolean last = left.decrementAndGet() == 0;
                    Duration time = Duration.ofNanos(System.nanoTime() - start);
                    ui.execute(() -> {
                        if (task.isCancelled()) return;
                        callback.done(index, picture);
                        if (last) callback.allDone(time);
                    });
                } catch (CancellationException e) {
                    // nobody waits for it any more
                } catch (RuntimeException | OutOfMemoryError e) {
                    if (failed.compareAndSet(false, true)) {
                        ui.execute(() -> {
                            if (!task.isCancelled()) callback.failed(e);
                        });
                    }
                }
            });
        }
        return task;
    }
}
