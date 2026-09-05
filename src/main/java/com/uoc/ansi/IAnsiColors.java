package com.uoc.ansi;

import java.awt.Color;

/**
 * A console palette: which colour each of {@link AnsiColor} is painted in.
 *
 * <p>
 * This used to be eighteen methods, one per colour, which meant every palette repeated
 * the whole list and so did everything that walked it. One lookup taking the colour as an
 * argument says the same thing, and the names live in one place.
 */
@FunctionalInterface
public interface IAnsiColors {

    /**
     * @param colour which colour is wanted; never {@code null}
     * @return the colour to paint it in; never {@code null}
     */
    Color of(AnsiColor colour);
}
