package com.uoc.ansi;

import java.awt.Color;

/**
 * ANSI palette for the dark theme, whose console background is a mid grey
 * (#46494b) rather than black. Every colour here clears the WCAG AA contrast
 * ratio of 4.5:1 against that background; the classic VGA palette does not,
 * because it assumes a black background.
 */
public final class DarkThemeAnsiColors implements IAnsiColors {

    private static final Color BLACK = new Color(0xbe, 0xbe, 0xbe);
    private static final Color RED = new Color(0xf9, 0xa4, 0x97);
    private static final Color GREEN = new Color(0x8b, 0xd3, 0x85);
    private static final Color YELLOW = new Color(0xc3, 0xc5, 0x5a);
    private static final Color BLUE = new Color(0xa0, 0xbe, 0xf9);
    private static final Color MAGENTA = new Color(0xeb, 0x9f, 0xe8);
    private static final Color CYAN = new Color(0x3e, 0xd6, 0xd6);
    private static final Color WHITE = new Color(0xf2, 0xf2, 0xf2);
    private static final Color BRIGHT_BLACK = new Color(0xd4, 0xd4, 0xd4);
    private static final Color BRIGHT_RED = new Color(0xfb, 0xc5, 0xbd);
    private static final Color BRIGHT_GREEN = new Color(0xa1, 0xea, 0x9b);
    private static final Color BRIGHT_YELLOW = new Color(0xda, 0xdc, 0x71);
    private static final Color BRIGHT_BLUE = new Color(0xc1, 0xd5, 0xfb);
    private static final Color BRIGHT_MAGENTA = new Color(0xfb, 0xbc, 0xf7);
    private static final Color BRIGHT_CYAN = new Color(0x4f, 0xef, 0xee);
    private static final Color BRIGHT_WHITE = new Color(0xff, 0xff, 0xff);

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
        return WHITE;
    }
}
