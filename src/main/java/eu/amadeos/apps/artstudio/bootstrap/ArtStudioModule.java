package eu.amadeos.apps.artstudio.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.commons.app.Ui;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.process.CommandRunner;
import eu.amadeos.apps.artstudio.repository.ArtStudioPreferences;
import eu.amadeos.apps.artstudio.repository.Backdrops;
import eu.amadeos.apps.artstudio.repository.PictureFiles;
import eu.amadeos.apps.artstudio.service.art.ArtRenderer;
import eu.amadeos.apps.artstudio.service.render.RenderService;
import eu.amadeos.apps.artstudio.ui.main.MainView;
import eu.amadeos.apps.artstudio.ui.main.MainWindow;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * amade-artstudio bindings. The background executor, theme, language and the other shared services come
 * from {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class ArtStudioModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.artstudio.messages";

    @Override
    protected void configure() {
        bind(ArtStudioPreferences.class).in(Scopes.SINGLETON);
        bind(PictureFiles.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
        bind(MainView.class).to(MainWindow.class);
    }

    /**
     * Pictures are drawn on one platform thread per core, a little below normal priority so that the window
     * stays responsive; the nine variations are drawn side by side.
     */
    @Provides
    @Singleton
    RenderService renderService(@Ui Executor ui) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return new RenderService(new ArtRenderer(), Executors.newFixedThreadPool(cores, workerThreads()), ui);
    }

    static ThreadFactory workerThreads() {
        AtomicInteger count = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "art-worker-" + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
    }

    @Provides
    @Singleton
    Backdrops backdrops(XdgDirectories xdg) {
        return Backdrops.system(xdg, CommandRunner.system());
    }

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(ArtStudioModule.class, MESSAGES, locale);
    }
}
