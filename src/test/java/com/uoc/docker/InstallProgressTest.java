package com.uoc.docker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading what Compose prints while it fetches an image.
 *
 * <p>
 * The lines below are Compose's own, copied from a run rather than invented, because the
 * whole job here is to recognise which later line replaces which earlier one and that
 * depends entirely on how Compose words them.
 */
class InstallProgressTest {

    private final InstallProgress progress = new InstallProgress();

    @Test
    void nothingHasBeenSaidToBeginWith() {
        assertThat(progress.isEmpty()).isTrue();
        assertThat(progress.text()).isEmpty();
    }

    @Test
    void aLayerCountingUpReplacesItselfRatherThanPilingUp() {
        // The point of the whole class: a download of eight layers writes hundreds of
        // lines that differ only in a number, and shown one after another they scroll
        // past faster than they can be read.
        progress.accept(" eab79789b661 Downloading 1.2MB/45MB");
        progress.accept(" eab79789b661 Downloading 12MB/45MB");
        progress.accept(" eab79789b661 Download complete");

        assertThat(progress.text()).isEqualTo("eab79789b661 Download complete");
    }

    @Test
    void everyLayerKeepsItsOwnLine() {
        progress.accept(" eab79789b661 Pulling fs layer 0B");
        progress.accept(" 2ddbe66c4473 Pulling fs layer 0B");
        progress.accept(" eab79789b661 Downloading 1.2MB/45MB");

        assertThat(progress.text().lines()).containsExactly(
                "eab79789b661 Downloading 1.2MB/45MB",
                "2ddbe66c4473 Pulling fs layer 0B");
    }

    @Test
    void theOrderIsTheOrderTheWorkWasAnnouncedIn() {
        // Not the order of the latest news, or the lines would jump about as each layer
        // reports and the whole block would be unreadable.
        progress.accept(" aaaaaaaaaaaa Pulling fs layer 0B");
        progress.accept(" bbbbbbbbbbbb Pulling fs layer 0B");
        progress.accept(" cccccccccccc Pulling fs layer 0B");
        progress.accept(" aaaaaaaaaaaa Download complete");

        assertThat(progress.text().lines().toList())
                .startsWith("aaaaaaaaaaaa Download complete")
                .endsWith("cccccccccccc Pulling fs layer 0B");
    }

    @Test
    void theImageItselfKeepsOneLineThroughout() {
        progress.accept("Image mongo:8.3.8 Pulling");
        progress.accept(" Image mongo:8.3.8 Pulled");

        assertThat(progress.text()).isEqualTo("Image mongo:8.3.8 Pulled");
    }

    @Test
    void twoImagesAreNotConfusedForOneAnother() {
        progress.accept("Image mongo:8.3.8 Pulling");
        progress.accept("Image redis:8.10.1 Pulling");

        assertThat(progress.text().lines()).hasSize(2);
    }

    @Test
    void theContainerKeepsItsOwnLineToo() {
        progress.accept(" Container uocdb-mongo Creating");
        progress.accept(" Container uocdb-mongo Created");
        progress.accept(" Container uocdb-mongo Starting");

        assertThat(progress.text()).isEqualTo("Container uocdb-mongo Starting");
    }

    @Test
    void aWholeRunReadsAsOneLinePerThing() {
        // Taken from an actual install, hundreds of lines of which are left out here
        // exactly as they are left out on screen.
        for (String line : new String[] {
                "Image mongo:8.3.8 Pulling",
                " eab79789b661 Pulling fs layer 0B",
                " 2ddbe66c4473 Pulling fs layer 0B",
                " eab79789b661 Downloading 4.7MB/45MB",
                " 2ddbe66c4473 Downloading 1.1MB/12MB",
                " eab79789b661 Pull complete",
                " 2ddbe66c4473 Pull complete",
                "Image mongo:8.3.8 Pulled",
                " Container uocdb-mongo Creating",
                " Container uocdb-mongo Started" }) {
            progress.accept(line);
        }

        assertThat(progress.text().lines()).containsExactly(
                "Image mongo:8.3.8 Pulled",
                "eab79789b661 Pull complete",
                "2ddbe66c4473 Pull complete",
                "Container uocdb-mongo Started");
    }

    @Test
    void aLineNobodyRecognisesIsKeptWholeAndReplacesNothing() {
        // Compose is free to word things differently in a later version, and an error is
        // worded differently every time. Nothing here may swallow either.
        progress.accept("service:mongo:1 Error response from daemon: Conflict.");
        progress.accept("algo que nadie ha visto antes");

        assertThat(progress.text().lines()).containsExactly(
                "service:mongo:1 Error response from daemon: Conflict.",
                "algo que nadie ha visto antes");
    }

    @Test
    void blankLinesAndNothingAtAllAreIgnored() {
        progress.accept("");
        progress.accept("   ");
        progress.accept(null);

        assertThat(progress.isEmpty()).isTrue();
    }

    @Test
    void aWordThatMerelyLooksLikeALayerIsNotTakenForOne() {
        // Twelve hexadecimal characters and nothing else. "Downloading" is eleven letters
        // and some of them are not hexadecimal, but the rule has to be exact.
        progress.accept("abcdefabcdef Download complete");
        progress.accept("abcdefabcdefa Something else");

        assertThat(progress.text().lines()).hasSize(2);
    }
}
