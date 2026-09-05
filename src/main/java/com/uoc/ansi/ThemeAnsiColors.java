package com.uoc.ansi;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * The console palette, read from the theme in use.
 *
 * <p>
 * There used to be two of these, one class per theme, each holding the colours as Java
 * constants. The values now live in {@code themes/FlatLightLaf.properties} and
 * {@code themes/FlatDarkLaf.properties}, which the look and feel merges into
 * {@link UIManager} exactly as it merges its own: switching theme reloads them with
 * everything else, and there is one place per theme where a colour is written down rather
 * than two systems that have to be kept in step.
 *
 * <p>
 * That leaves this class with nothing to hold. It is a reading of whatever theme is
 * installed at the moment it is asked, so a single instance stays correct across a theme
 * change.
 */
public final class ThemeAnsiColors implements IAnsiColors {

    /**
     * {@inheritDoc}
     *
     * <p>
     * A colour the theme does not define would paint nothing and leave a blank console,
     * which is a very quiet way to fail. It is refused loudly instead: the only way to
     * get here is a missing line in the properties, and that is a mistake to fix rather
     * than to work around.
     */
    @Override
    public Color of(AnsiColor colour) {
        Color color = UIManager.getColor(colour.themeKey());
        if (color == null) {
            throw new IllegalStateException("The theme defines no colour named "
                    + colour.themeKey() + ". Is themes/ registered as a defaults source?");
        }
        return color;
    }
}
