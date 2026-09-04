package com.uoc.ansi;

import java.awt.Color;

/**
 * ANSI palette for the light theme, whose console background is white.
 * Every colour clears the WCAG AA contrast ratio of 4.5:1 against it.
 */
public final class LightThemeAnsiColors implements IAnsiColors {
    private static final Color BLACK = new Color(0x71, 0x71, 0x71);
    private static final Color RED = new Color(0xb1, 0x4f, 0x43);
    private static final Color GREEN = new Color(0x3e, 0x84, 0x39);
    private static final Color YELLOW = new Color(0x76, 0x76, 0x1d);
    private static final Color BLUE = new Color(0x4a, 0x6e, 0xbd);
    private static final Color MAGENTA = new Color(0x99, 0x53, 0x97);
    private static final Color CYAN = new Color(0x21, 0x81, 0x81);
    private static final Color WHITE = new Color(0x45, 0x45, 0x45);
    private static final Color BRIGHT_BLACK = new Color(0x5d, 0x5d, 0x5d);
    private static final Color BRIGHT_RED = new Color(0x99, 0x3a, 0x2f);
    private static final Color BRIGHT_GREEN = new Color(0x28, 0x6f, 0x24);
    private static final Color BRIGHT_YELLOW = new Color(0x61, 0x62, 0x16);
    private static final Color BRIGHT_BLUE = new Color(0x37, 0x59, 0xa6);
    private static final Color BRIGHT_MAGENTA = new Color(0x84, 0x3e, 0x82);
    private static final Color BRIGHT_CYAN = new Color(0x1a, 0x6a, 0x6a);
    private static final Color BRIGHT_WHITE = new Color(0x00, 0x00, 0x00);

    @Override
    public Color black() {
        return BLACK;
    }

    @Override
    public Color red() {
        return RED;
    }

    @Override
    public Color green() {
        return GREEN;
    }

    @Override
    public Color yellow() {
        return YELLOW;
    }

    @Override
    public Color blue() {
        return BLUE;
    }

    @Override
    public Color magenta() {
        return MAGENTA;
    }

    @Override
    public Color cyan() {
        return CYAN;
    }

    @Override
    public Color white() {
        return WHITE;
    }

    @Override
    public Color brightBlack() {
        return BRIGHT_BLACK;
    }

    @Override
    public Color brightRed() {
        return BRIGHT_RED;
    }

    @Override
    public Color brightGreen() {
        return BRIGHT_GREEN;
    }

    @Override
    public Color brightYellow() {
        return BRIGHT_YELLOW;
    }

    @Override
    public Color brightBlue() {
        return BRIGHT_BLUE;
    }

    @Override
    public Color brightMagenta() {
        return BRIGHT_MAGENTA;
    }

    @Override
    public Color brightCyan() {
        return BRIGHT_CYAN;
    }

    @Override
    public Color brightWhite() {
        return BRIGHT_WHITE;
    }

    @Override
    public Color defaultColor() {
        return BRIGHT_WHITE;
    }
}
