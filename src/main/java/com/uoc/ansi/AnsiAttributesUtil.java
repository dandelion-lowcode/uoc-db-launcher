package com.uoc.ansi;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Utility functions to update a {@link MutableAttributeSet} instance using ANSI styles.
 */
public final class AnsiAttributesUtil {

    /**
     * Updates the styling on a {@link MutableAttributeSet} based on an ANSI Escape Code.
     *
     * @param escCode    is the {@link AnsiEscCode} defining the style to set, e.g. {@link AnsiEscCode#BOLD}.
     * @param ansiColors is the {@link IAnsiColors} that defines the foreground and background colors to use for the styling.
     */
    public static MutableAttributeSet updateAnsi(MutableAttributeSet attributes, AnsiEscCode escCode, IAnsiColors ansiColors) {

        var modifiedAttributes = new SimpleAttributeSet(attributes);

        switch (escCode) {
            case RESET:
                modifiedAttributes = new SimpleAttributeSet();
                StyleConstants.setFontFamily(modifiedAttributes, StyleConstants.getFontFamily(attributes));
                StyleConstants.setFontSize(modifiedAttributes, StyleConstants.getFontSize(attributes));
                break;

            case BOLD:
                StyleConstants.setBold(modifiedAttributes, true);
                break;

            case FAINT:

            case NOT_BOLD:

            case NORMAL:
                StyleConstants.setBold(modifiedAttributes, false);
                break;

            case ITALIC:
                StyleConstants.setItalic(modifiedAttributes, true);
                break;

            case UNDERLINE:
                StyleConstants.setUnderline(modifiedAttributes, true);
                break;

            case NOT_ITALIC:
                StyleConstants.setItalic(modifiedAttributes, false);
                break;

            case NOT_UNDERLINED:
                StyleConstants.setUnderline(modifiedAttributes, false);
                break;

            case BLACK:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.black());
                break;

            case RED:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.red());
                break;

            case GREEN:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.green());
                break;

            case YELLOW:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.yellow());
                break;

            case BLUE:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.blue());
                break;

            case MAGENTA:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.magenta());
                break;

            case CYAN:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.cyan());
                break;

            case WHITE:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.white());
                break;

            case BRIGHT_BLACK:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.brightBlack());
                break;

            case BRIGHT_RED:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.brightRed());
                break;

            case BRIGHT_GREEN:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.brightGreen());
                break;

            case BRIGHT_YELLOW:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.brightYellow());
                break;

            case BRIGHT_BLUE:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.brightBlue());
                break;

            case BRIGHT_MAGENTA:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.brightMagenta());
                break;

            case BRIGHT_CYAN:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.brightCyan());
                break;

            case BRIGHT_WHITE:
                StyleConstants.setForeground(modifiedAttributes, ansiColors.brightWhite());
                break;

            case DEFAULT:
                modifiedAttributes.removeAttribute(StyleConstants.Foreground);
                break;

            case BLACK_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.black());
                break;

            case RED_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.red());
                break;

            case GREEN_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.green());
                break;

            case YELLOW_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.yellow());
                break;

            case BLUE_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.blue());
                break;

            case MAGENTA_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.magenta());
                break;

            case CYAN_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.cyan());
                break;

            case WHITE_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.white());
                break;

            case BRIGHT_BLACK_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.brightBlack());
                break;

            case BRIGHT_RED_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.brightRed());
                break;

            case BRIGHT_GREEN_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.brightGreen());
                break;

            case BRIGHT_YELLOW_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.brightYellow());
                break;

            case BRIGHT_BLUE_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.brightBlue());
                break;

            case BRIGHT_MAGENTA_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.brightMagenta());
                break;

            case BRIGHT_CYAN_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.brightCyan());
                break;

            case BRIGHT_WHITE_BACKGROUND:
                StyleConstants.setBackground(modifiedAttributes, ansiColors.brightWhite());
                break;

            case DEFAULT_BACKGROUND:
                modifiedAttributes.removeAttribute(StyleConstants.Background);
                break;
        }
        return modifiedAttributes;
    }
}
