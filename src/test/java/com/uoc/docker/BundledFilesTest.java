package com.uoc.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unpacking is exercised against a temporary directory rather than the real one, so a
 * test run never touches a student's notebooks.
 *
 * <p>
 * The partitions that matter: a directory that has never been written to, one that
 * already holds the application's own files, and one that already holds a notebook
 * somebody has edited.
 */
class BundledFilesTest {

    @TempDir
    Path root;

    @Test
    void everythingDockerNeedsEndsUpOnTheDisk() throws IOException {
        new BundledFiles(root).install();

        assertThat(root.resolve("docker/docker-compose.yml")).isRegularFile();
        assertThat(root.resolve("docker/riak/Dockerfile")).isRegularFile();
        assertThat(root.resolve("docker/jupyter/Dockerfile")).isRegularFile();
        assertThat(root.resolve("docker/jupyter/requirements.txt")).isRegularFile();
    }

    @Test
    void theShellScriptRiakNeedsIsUnpackedToo() throws IOException {
        // It is copied into the image at build time; without it the service starts
        // listening only on its own loopback and answers nobody.
        new BundledFiles(root).install();

        assertThat(root.resolve("docker/riak/zz-bind-all-interfaces.sh")).isRegularFile();
    }

    @Test
    void theNotebooksLandBesideTheServicesRatherThanInsideThem() throws IOException {
        // The compose file mounts them as "../notebooks", relative to itself. Were they
        // unpacked anywhere else, Docker would silently create an empty directory and the
        // student would open Jupyter to find nothing in it.
        new BundledFiles(root).install();

        assertThat(root.resolve("notebooks")).isDirectory();
        assertThat(root.resolve("notebooks/demo-mongodb.ipynb")).isRegularFile();
    }

    @Test
    void installAnswersWhereTheComposeFileLanded() throws IOException {
        Path composeFile = new BundledFiles(root).install();

        assertThat(composeFile)
                .isAbsolute()
                .isRegularFile()
                .isEqualTo(root.resolve("docker/docker-compose.yml").toAbsolutePath());
    }

    @Test
    void serviceDefinitionsAreRewrittenSoAnUpdateTakesEffect() throws IOException {
        BundledFiles files = new BundledFiles(root);
        files.install();
        Path compose = root.resolve("docker/docker-compose.yml");
        Files.writeString(compose, "de una version anterior");

        files.install();

        assertThat(compose).content().isNotEqualTo("de una version anterior");
        assertThat(compose).content().contains("uocdb-mongo");
    }

    @Test
    void aNotebookTheStudentHasEditedIsNeverOverwritten() throws IOException {
        // The one thing this must never do. A student who spent an evening on the
        // MongoDB exercises should not lose it by opening the launcher again.
        BundledFiles files = new BundledFiles(root);
        files.install();
        Path notebook = root.resolve("notebooks/demo-mongodb.ipynb");
        Files.writeString(notebook, "el trabajo del estudiante");

        files.install();

        assertThat(notebook).hasContent("el trabajo del estudiante");
    }

    @Test
    void aNotebookTheStudentDeletedComesBack() throws IOException {
        BundledFiles files = new BundledFiles(root);
        files.install();
        Path notebook = root.resolve("notebooks/demo-redis.ipynb");
        Files.delete(notebook);

        files.install();

        assertThat(notebook).isRegularFile();
    }

    @Test
    void unpackingTwiceOverLeavesTheSameFiles() throws IOException {
        BundledFiles files = new BundledFiles(root);
        files.install();
        String composeAfterFirst = Files.readString(root.resolve("docker/docker-compose.yml"));

        files.install();

        assertThat(root.resolve("docker/docker-compose.yml")).hasContent(composeAfterFirst);
    }

    @Test
    void theComposeFileIsKnownBeforeAnythingIsUnpacked() {
        // The interface asks where things are while deciding what to show, which happens
        // before any service is started.
        assertThat(new BundledFiles(root).composeFile())
                .isAbsolute()
                .doesNotExist();
    }

    @Test
    void theUnpackedComposeFileIsSomethingDockerCanActuallyRead() throws IOException {
        Path composeFile = new BundledFiles(root).install();

        assertThat(Files.readString(composeFile))
                .contains("name: uocdb")
                // Relative to this file, which is why it has to land beside the notebooks.
                .contains("${UOCDB_NOTEBOOKS:-../notebooks}:/home/jovyan/work/notebooks");
    }
}
