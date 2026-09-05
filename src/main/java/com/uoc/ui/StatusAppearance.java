package com.uoc.ui;

import java.awt.Color;
import java.util.Locale;

import javax.swing.UIManager;

import com.uoc.docker.ServiceStatus;

/**
 * How each status looks beside a service.
 *
 * <p>
 * Every status has a colour of its own, and every one of them is written down in the
 * theme rather than here: {@code themes/FlatLightLaf.properties} and its dark twin define
 * {@code Status.healthy} and the rest, and the look and feel merges them into
 * {@link UIManager} beside its own. Switching theme reloads them along with everything
 * else, and the console palette and these indicators are the same set of colours because
 * one file per theme says so.
 *
 * <p>
 * Twelve colours is more than can be told apart at a glance on a ten-pixel dot, so the
 * four pairs that sit closest were chosen to be pairs whose confusion costs nothing:
 * stopped against stopping, starting against loading, unhealthy against restarting, and
 * failed against error. Mistaking either member for the other still leaves the student
 * with the right idea, and the word beside the dot settles it.
 */
public final class StatusAppearance {

    private static final String STATUS_PREFIX = "Status.";
    private static final String ACTION_PREFIX = "Action.";

    private StatusAppearance() {
    }

    /**
     * A colour from the theme.
     *
     * <p>
     * A key the theme does not define would otherwise paint nothing, and an indicator
     * that quietly disappears is worse than one that says what is wrong. There is only
     * one way to get here -- a line missing from the properties -- and that is a mistake to
     * fix rather than to survive.
     */
    private static Color themeColor(String key) {
        Color color = UIManager.getColor(key);
        if (color == null) {
            throw new IllegalStateException("The theme defines no colour named " + key
                    + ". Is themes/ registered as a defaults source?");
        }
        return color;
    }

    /**
     * The colour of the dot beside a service.
     *
     * <p>
     * The key is the status's own name, so a status added to the enum without a colour
     * added to the theme fails at once rather than being drawn in nothing.
     */
    public static Color colorFor(ServiceStatus status) {
        return themeColor(STATUS_PREFIX + camelCase(status.name()));
    }

    /**
     * Whether the dot pulses.
     *
     * <p>
     * A pulse says "wait, this is going somewhere", so only a wait that ends by itself
     * pulses. Restarting deliberately does not: a container caught in a restart loop is
     * moving without arriving, and telling a student to wait for it is telling them to
     * wait for something that will never come.
     */
    public static boolean pulsates(ServiceStatus status) {
        return status.isWaiting();
    }

    /** The colour of the play or stop icon, and of both when there is nothing to press. */
    public static Color actionColor(boolean available, boolean starting) {
        if (!available) {
            // The same grey the dot uses for a stopped service: nothing to do just now.
            return themeColor(ACTION_PREFIX + "unavailable");
        }
        return themeColor(ACTION_PREFIX + (starting ? "start" : "stop"));
    }

    /**
     * The enum's SHOUTING_NAME as the theme spells it, so {@code OUT_OF_MEMORY} finds
     * {@code Status.outOfMemory}. Properties files read better in the case the rest of
     * the look and feel already uses.
     */
    private static String camelCase(String constant) {
        String[] words = constant.toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) {
            name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return name.toString();
    }
}
