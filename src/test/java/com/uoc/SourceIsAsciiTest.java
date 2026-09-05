package com.uoc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every Java source holds nothing but printable ASCII.
 *
 * <p>
 * An accent or a typographic dash reads fine in the editor it was typed in and then
 * arrives as mojibake through a terminal, a diff, a build log or a machine whose default
 * encoding is not UTF-8. Written as {@code \\u00ed} it survives all of them and the
 * compiler puts the letter back.
 *
 * <p>
 * Control characters are checked too. A raw escape character is ASCII, so a check for
 * high code points alone would miss it, and it is exactly as unreadable in source: the
 * console tests are full of escape sequences, and every one of them is written out.
 *
 * <p>
 * Translations are the exception and are not looked at here: their accents are the text
 * the student reads, and a resource bundle reads them as UTF-8 by design.
 */
class SourceIsAsciiTest {

    private static final Path SOURCE = Path.of("src");

    /** Tab, newline and carriage return are the only control characters source may hold. */
    private static final String ALLOWED_CONTROL = "\t\n\r";

    private record Offender(Path file, int line, char character) {

        @Override
        public String toString() {
            return String.format("%s:%d holds U+%04X, write it as \\u%04x",
                    file, line, (int) character, (int) character);
        }
    }

    @Test
    void noJavaSourceHoldsAnythingButPrintableAscii() throws IOException {
        List<Offender> offenders = new ArrayList<>();

        try (Stream<Path> tree = Files.walk(SOURCE)) {
            for (Path file : tree.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int number = 0; number < lines.size(); number++) {
                    for (char character : lines.get(number).toCharArray()) {
                        if (isOutsidePrintableAscii(character)) {
                            offenders.add(new Offender(file, number + 1, character));
                        }
                    }
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    private static boolean isOutsidePrintableAscii(char character) {
        return character > 126 || (character < 32 && ALLOWED_CONTROL.indexOf(character) < 0);
    }
}
