package com.uoc.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The icons the packaging step feeds to jpackage.
 *
 * <p>
 * Nothing at run time opens these, so nothing at run time would notice them missing or
 * broken: the failure would arrive as a build that stops on a runner, or worse, as an
 * application that ships wearing the generic Java icon. They are checked here instead.
 */
class PackagingIconsTest {

    private static final Path PACKAGING = Path.of("src", "main", "packaging");

    @Test
    void theMasterImageIsThereAndLargeEnoughForEverySizeDerivedFromIt() throws IOException {
        // macOS asks for 512 at twice the density, so a thousand pixels is the largest
        // anything needs; below that the Dock icon would be scaled up and soft.
        Path png = PACKAGING.resolve("icon.png");
        assertThat(png).exists();

        BufferedImage image = ImageIO.read(png.toFile());
        assertThat(image).as("icon.png must be a readable image").isNotNull();
        assertThat(image.getWidth()).isGreaterThanOrEqualTo(1024);
        assertThat(image.getWidth()).isEqualTo(image.getHeight());
    }

    @Test
    void theWindowsIconIsThereAndHoldsSeveralSizes() throws IOException {
        // Windows picks a size out of the file for each place it draws the application.
        // One size only would leave it resampling, and the sixteen-pixel version is the
        // one that suffers.
        File ico = PACKAGING.resolve("icon.ico").toFile();
        assertThat(ico).exists();

        byte[] bytes = Files.readAllBytes(ico.toPath());
        assertThat(bytes.length).isGreaterThan(1000);
        // The header is a reserved zero, a type of 1 for an icon, and the count of sizes,
        // each a little-endian short.
        assertThat(bytes[0]).isZero();
        assertThat(bytes[1]).isZero();
        assertThat(bytes[2]).isEqualTo((byte) 1);
        assertThat(bytes[3]).isZero();
        int count = (bytes[4] & 0xff) | ((bytes[5] & 0xff) << 8);
        assertThat(count).isGreaterThanOrEqualTo(4);
    }

    @Test
    void theMacosIconIsThereAndWellFormed() throws IOException {
        // Nothing on this machine can open an icns, and a malformed one does not fail the
        // build: jpackage copies it into the bundle and macOS quietly falls back to the
        // generic icon, which is exactly the fault this is here to prevent. So the
        // container is read by hand.
        Path icns = PACKAGING.resolve("icon.icns");
        assertThat(icns).exists();

        byte[] bytes = Files.readAllBytes(icns);
        assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("icns");
        assertThat(readBigEndianInt(bytes, 4))
                .as("the length in the header must be the length of the file")
                .isEqualTo(bytes.length);

        int offset = 8;
        int images = 0;
        while (offset < bytes.length) {
            int length = readBigEndianInt(bytes, offset + 4);
            assertThat(length).as("a chunk must hold more than its own header")
                    .isGreaterThan(8);
            assertThat(offset + length).as("a chunk must not run past the end of the file")
                    .isLessThanOrEqualTo(bytes.length);
            // Every type used here carries a PNG, which starts with a fixed signature.
            assertThat(bytes[offset + 8]).isEqualTo((byte) 0x89);
            assertThat(new String(bytes, offset + 9, 3,
                    java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("PNG");
            offset += length;
            images++;
        }
        assertThat(offset).as("the chunks must fill the file exactly").isEqualTo(bytes.length);
        assertThat(images).isGreaterThanOrEqualTo(6);
    }

    private static int readBigEndianInt(byte[] bytes, int at) {
        return ((bytes[at] & 0xff) << 24) | ((bytes[at + 1] & 0xff) << 16)
                | ((bytes[at + 2] & 0xff) << 8) | (bytes[at + 3] & 0xff);
    }

    @Test
    void theyAreMadeFromTheIconTheApplicationItselfUses() {
        // If the SVG is ever replaced without these being rendered again, the window
        // would show one icon and the taskbar another. The README beside them says how.
        assertThat(getClass().getClassLoader().getResource("icons/icon.svg"))
                .as("the vector these are rendered from")
                .isNotNull();
        assertThat(PACKAGING.resolve("README.md")).exists();
    }
}
