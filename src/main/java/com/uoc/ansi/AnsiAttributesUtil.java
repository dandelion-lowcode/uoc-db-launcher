package com.uoc.ansi;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Utility functions to update a {@link MutableAttributeSet} instance using ANSI styles.
 */
public final class AnsiAttributesUtil {

    private AnsiAttributesUtil() {
    }

    /**
     * Updates the styling on a {@link MutableAttributeSet} based on an ANSI Escape Code.
     *
     * <p>
     * The thirty-two colour codes are handled together rather than one case each: every
     * one of them says which colour to paint and whether to paint it behind the text or
     * in it, and the code itself now answers both. What is left is the handful of styles
     * that are not colours at all.
     *
     * @param escCode    the {@link AnsiEscCode} defining the style to set, e.g.
     *                   {@link AnsiEscCode#BOLD}
     * @param ansiColors the palette the colour codes are painted from
     */
    public static MutableAttributeSet updateAnsi(MutableAttributeSet attributes,
            AnsiEscCode escCode, IAnsiColors ansiColors) {

        var modifiedAttributes = new SimpleAttributeSet(attributes);

        AnsiColor colour = escCode.colour();
        if (colour != null) {
            if (escCode.isBackground()) {
                StyleConstants.setBackground(modifiedAttributes, ansiColors.of(colour));
            } else {
                StyleConstants.setForeground(modifiedAttributes, ansiColors.of(colour));
            }
            return modifiedAttributes;
        }

        switch (escCode) {
            case RESET:
                // Everything goes, except how the console is drawn: the font and its size
                // belong to the console rather than to anything the text asked for.
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

            // These two take a colour away rather than setting one, which is why they are
            // not among the codes that name a palette colour.
            case DEFAULT:
                modifiedAttributes.removeAttribute(StyleConstants.Foreground);
                break;

            case DEFAULT_BACKGROUND:
                modifiedAttributes.removeAttribute(StyleConstants.Background);
                break;

            default:
                break;
        }
        return modifiedAttributes;
    }
}
