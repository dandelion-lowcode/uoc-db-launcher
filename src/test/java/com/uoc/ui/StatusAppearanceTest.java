package com.uoc.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.uoc.docker.ServiceStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * The rules the palette has to keep, checked rather than trusted.
 *
 * <p>
 * Both themes are exercised, because the indicators are drawn from the theme's own
 * palette and a colour that reads well on one background can vanish on the other. The
 * backgrounds are read from the look and feel rather than written down here, so the
 * checks follow it if it ever changes.
 */
class StatusAppearanceTest {

    @org.junit.jupiter.api.BeforeAll
    static void installTheThemeThatHoldsOurColours() {
        ThemeForTests.install();
    }

    /** The ratio WCAG asks of a graphic, which is what a coloured dot is. */
    private static final double MINIMUM_CONTRAST = 3.0;

    /**
     * How far apart two colours have to be to count as different at all. Below about 2 a
     * difference is invisible; the pairs deliberately left close here sit near 8, which
     * is separable side by side, and each of those pairs means nearly the same thing.
     */
    private static final double MINIMUM_DISTANCE = 5.0;

    private static Color panelBackground(boolean dark) throws Exception {
        UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
        Color background = UIManager.getColor("Panel.background");
        assertThat(background).as("the look and feel must define a panel background").isNotNull();
        return background;
    }

    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = { false, true })
    void everyStatusIsVisibleAgainstThePanelItIsDrawnOn(boolean dark) throws Exception {
        Color background = panelBackground(dark);

        for (ServiceStatus status : ServiceStatus.values()) {
            assertThat(contrastRatio(StatusAppearance.colorFor(status), background))
                    .as("%s on the %s theme", status, dark ? "dark" : "light")
                    .isGreaterThanOrEqualTo(MINIMUM_CONTRAST);
        }
    }

    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = { false, true })
    void noTwoStatusesShareAColour(boolean dark) throws Exception {
        panelBackground(dark);

        Map<Color, ServiceStatus> seen = new HashMap<>();
        for (ServiceStatus status : ServiceStatus.values()) {
            ServiceStatus clash = seen.put(StatusAppearance.colorFor(status), status);
            assertThat(clash).as("%s and %s are the same colour", status, clash).isNull();
        }
    }

    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = { false, true })
    void noTwoStatusesAreSoCloseAsToBeTheSameColour(boolean dark) throws Exception {
        panelBackground(dark);

        for (ServiceStatus one : ServiceStatus.values()) {
            for (ServiceStatus other : ServiceStatus.values()) {
                if (one.ordinal() >= other.ordinal()) {
                    continue;
                }
                assertThat(distance(StatusAppearance.colorFor(one), StatusAppearance.colorFor(other)))
                        .as("%s and %s on the %s theme", one, other, dark ? "dark" : "light")
                        .isGreaterThanOrEqualTo(MINIMUM_DISTANCE);
            }
        }
    }

    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = { false, true })
    void everyStatusHasAColourWrittenDownInTheTheme(boolean dark) throws Exception {
        // The colours moved out of Java and into the look and feel, so the compiler no
        // longer checks that each status has one. This does: a status added to the enum
        // without a line added to both theme files fails here rather than being drawn in
        // nothing.
        panelBackground(dark);

        for (ServiceStatus status : ServiceStatus.values()) {
            assertThat(StatusAppearance.colorFor(status)).as("%s", status).isNotNull();
        }
    }

    @Test
    void aThemeWithoutOurColoursSaysSoRatherThanPaintingNothing() throws Exception {
        // Any look and feel that is not ours: the colours live in the theme now, so one
        // built without them has none, and an indicator that quietly disappears is worse
        // than one that says what is missing. The message names the key, because the only
        // way to arrive here is a line left out of the properties.
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        try {
            assertThat(catchThrowable(() -> StatusAppearance.colorFor(ServiceStatus.HEALTHY)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Status.healthy");
        } finally {
            ThemeForTests.reinstall();
        }
    }

    @Test
    void onlyTheWaitsThatEndByThemselvesPulse() {
        // A pulse says "wait, this is going somewhere". Restarting is movement without
        // arrival, so it must not pulse, and nothing settled or broken may either.
        for (ServiceStatus status : ServiceStatus.values()) {
            assertThat(StatusAppearance.pulsates(status))
                    .as("%s", status)
                    .isEqualTo(status.isWaiting());
        }
        assertThat(StatusAppearance.pulsates(ServiceStatus.RESTARTING)).isFalse();
        assertThat(StatusAppearance.pulsates(ServiceStatus.HEALTHY)).isFalse();
    }

    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = { false, true })
    void theButtonIsGreenToStartRedToStopAndGreyWhenThereIsNothingToPress(boolean dark)
            throws Exception {
        Color background = panelBackground(dark);

        Color start = StatusAppearance.actionColor(true, true);
        Color stop = StatusAppearance.actionColor(true, false);
        Color unavailable = StatusAppearance.actionColor(false, true);

        assertThat(start).isNotEqualTo(stop).isNotEqualTo(unavailable);
        assertThat(stop).isNotEqualTo(unavailable);
        // Green really is the greener of the two, whatever the theme does to it.
        assertThat(start.getGreen() - start.getRed())
                .isGreaterThan(stop.getGreen() - stop.getRed());
        for (Color color : new Color[] { start, stop, unavailable }) {
            assertThat(contrastRatio(color, background)).isGreaterThanOrEqualTo(MINIMUM_CONTRAST);
        }
    }

    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = { false, true })
    void anUnavailableButtonLooksTheSameWhicheverActionItWouldHaveOffered(boolean dark)
            throws Exception {
        panelBackground(dark);

        assertThat(StatusAppearance.actionColor(false, true))
                .isEqualTo(StatusAppearance.actionColor(false, false));
    }

    private static double contrastRatio(Color foreground, Color background) {
        double lighter = Math.max(luminance(foreground), luminance(background));
        double darker = Math.min(luminance(foreground), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        double[] channel = new double[3];
        int[] raw = { color.getRed(), color.getGreen(), color.getBlue() };
        for (int i = 0; i < 3; i++) {
            double value = raw[i] / 255.0;
            channel[i] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channel[0] + 0.7152 * channel[1] + 0.0722 * channel[2];
    }

    /** How far apart two colours look, rather than how far apart their numbers are. */
    private static double distance(Color one, Color other) {
        double[] a = lab(one);
        double[] b = lab(other);
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2)
                + Math.pow(a[2] - b[2], 2));
    }

    private static double[] lab(Color color) {
        double[] linear = new double[3];
        int[] raw = { color.getRed(), color.getGreen(), color.getBlue() };
        for (int i = 0; i < 3; i++) {
            double value = raw[i] / 255.0;
            linear[i] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        double[] xyz = {
                (linear[0] * 0.4124 + linear[1] * 0.3576 + linear[2] * 0.1805) / 0.95047,
                linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722,
                (linear[0] * 0.0193 + linear[1] * 0.1192 + linear[2] * 0.9505) / 1.08883 };
        for (int i = 0; i < 3; i++) {
            xyz[i] = xyz[i] > 0.008856 ? Math.cbrt(xyz[i]) : (7.787 * xyz[i] + 16.0 / 116);
        }
        return new double[] { 116 * xyz[1] - 16, 500 * (xyz[0] - xyz[1]), 200 * (xyz[1] - xyz[2]) };
    }
}
