package eu.amadeos.apps.artstudio.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtStudioModuleTest {
    @Test
    void appInfo() {
        assertEquals("art-studio", AmadeArtStudioApplication.APP.configName());
        assertEquals("amade-artstudio", AmadeArtStudioApplication.APP.id());
        assertEquals("dev", AmadeArtStudioApplication.version());
    }

    @Test
    void workersDoNotKeepTheProgramAlive() {
        Thread worker = ArtStudioModule.workerThreads().newThread(() -> { });
        assertTrue(worker.isDaemon());
        assertTrue(worker.getName().startsWith("art-worker-"));
        assertTrue(worker.getPriority() < Thread.NORM_PRIORITY);
    }
}
