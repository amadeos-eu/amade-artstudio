package eu.amadeos.apps.artstudio.bootstrap;

import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;
import eu.amadeos.apps.artstudio.ui.main.MainPresenter;
import eu.amadeos.apps.artstudio.ui.main.MainWindow;

import java.util.List;

public final class AmadeArtStudioApplication {
    public static final AppInfo APP = new AppInfo("amade-artstudio", "Art Studio", "art-studio");

    private AmadeArtStudioApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeArtStudioApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    /** Opens the recipe or PNG named as first argument, if any. */
    public static void main(String[] args) {
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(args));
        }, new ArtStudioModule());
    }
}
