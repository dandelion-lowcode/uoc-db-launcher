package com.uoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Image;
import java.awt.image.MultiResolutionImage;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The icon the window is given, which is not the icon the window is drawn with.
 *
 * <p>
 * FlatSVGIcon's own {@code getImage} hands back a multi-resolution image: one object that
 * will produce whichever size is asked of it. The Windows side of AWT does not ask. It
 * takes a variant and reads a corner of it the size it wanted, so the taskbar showed the
 * top-left of the drawing at full size -- a U, an O, and the lid of the database -- while
 * every PNG rendered from the same file looked perfect.
 *
 * <p>
 * Nothing on the way to that is wrong in a way a test could see except this: what reaches
 * setIconImages has to be ordinary images, one per size.
 */
@DisplayName("the icon handed to the window")
class LauncherIconTest {

    @BeforeAll
    static void installTheThemeTheIconsAreDrawnUnder() {
        com.formdev.flatlaf.FlatLaf.registerCustomDefaultsSource("themes");
        com.formdev.flatlaf.FlatLightLaf.setup();
    }

    @Test
    void noneOfThemIsAMultiResolutionImage() {
        // The whole of the bug. A multi-resolution image is right for a component, which
        // asks politely, and wrong for a window icon, which does not.
        assertThat(Launcher.appIcons())
                .isNotEmpty()
                .allSatisfy(image -> assertThat(image)
                        .as("%dx%d", image.getWidth(null), image.getHeight(null))
                        .isNotInstanceOf(MultiResolutionImage.class));
    }

    @Test
    void thereIsOneForEverySizeWindowsAsksFor() {
        // The small end is the taskbar and the title bar, the large end Alt-Tab and the
        // task view. A size that is missing is a size Windows makes up by scaling.
        List<Integer> sizes = Launcher.appIcons().stream()
                .map(image -> image.getWidth(null)).toList();

        assertThat(sizes).contains(16, 24, 32, 48, 256).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void eachIsSquareAndTheSizeItClaims() {
        for (Image image : Launcher.appIcons()) {
            assertThat(image.getWidth(null))
                    .as("square")
                    .isEqualTo(image.getHeight(null));
        }
    }

    @Test
    void eachHasTheDrawingInItRatherThanEmptyPixels() {
        // Painting an icon onto a graphics that has no component behind it is allowed but
        // easy to get wrong, and an icon that came out blank would pass every check
        // above.
        for (Image image : Launcher.appIcons()) {
            java.awt.image.BufferedImage drawn = (java.awt.image.BufferedImage) image;
            java.util.Set<Integer> colours = new java.util.HashSet<>();
            for (int x = 0; x < drawn.getWidth(); x++) {
                for (int y = 0; y < drawn.getHeight(); y++) {
                    colours.add(drawn.getRGB(x, y));
                }
            }
            assertThat(colours)
                    .as("%dx%d has more than one colour in it", drawn.getWidth(), drawn.getHeight())
                    .hasSizeGreaterThan(1);
        }
    }
}
