package eu.amadeos.apps.artstudio.service.art;

/** Draws one style on a sketch whose paper is already laid. */
@FunctionalInterface
public interface StylePainter {
    void paint(Sketch s);
}
