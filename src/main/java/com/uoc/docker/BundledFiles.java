package com.uoc.docker;

import com.uoc.platform.UserDataDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Puts the files Docker needs onto the disk.
 *
 * <p>
 * Docker Compose reads a real file and builds images from real directories. Inside an
 * installed application those live in a jar, where nothing can open them by path, so they
 * are unpacked into the user's own data directory before anything is started.
 *
 * <p>
 * The two kinds of file here are treated differently on purpose. The service definitions
 * belong to the application and are rewritten every time it starts, so that an update
 * cannot leave a student running last version's definitions against this version's
 * launcher. The notebooks belong to the student and are only ever written when they are
 * missing: overwriting them would throw away the work they did last night, which is the
 * one thing this must never do.
 */
public final class BundledFiles {

    private static final String DOCKER = "docker";
    private static final String NOTEBOOKS = "notebooks";

    private final Path root;

    /** Unpacks into a given directory, which is what the tests use. */
    BundledFiles(Path root) {
        this.root = root;
    }

    /** Unpacks into the directory this system sets aside for the application. */
    public static BundledFiles inUserDataDirectory() {
        return new BundledFiles(UserDataDirectory.current());
    }

    /**
     * Unpacks everything and answers where the compose file landed.
     *
     * @return the absolute path of the compose file, ready to be passed to {@code -f}
     * @throws IOException if the files cannot be written, which leaves the application
     *                     with nothing to run and so is not something to paper over
     */
    public Path install() throws IOException {
        copyTree(DOCKER, root.resolve(DOCKER), true);
        copyTree(NOTEBOOKS, root.resolve(NOTEBOOKS), false);
        return composeFile();
    }

    /** Where the compose file is, whether or not it has been unpacked yet. */
    public Path composeFile() {
        return root.resolve(DOCKER).resolve(DockerCommand.COMPOSE_FILE_NAME).toAbsolutePath();
    }

    /**
     * Copies one directory of bundled resources onto the disk.
     *
     * @param resource the directory's name at the root of the classpath
     * @param target   where it should end up
     * @param replace  whether a file already on disk should be overwritten
     */
    private void copyTree(String resource, Path target, boolean replace) throws IOException {
        URI uri = locate(resource);
        // Resources are a directory on disk when the application runs from a build, and
        // entries in a zip when it runs from a jar. Only the second needs a file system
        // opening for it, and that one has to be closed again.
        if ("jar".equals(uri.getScheme())) {
            try (FileSystem jar = FileSystems.newFileSystem(uri, Map.of())) {
                copyFrom(jar.getPath("/" + resource), target, replace);
            }
        } else {
            copyFrom(Path.of(uri), target, replace);
        }
    }

    private URI locate(String resource) throws IOException {
        try {
            var url = BundledFiles.class.getClassLoader().getResource(resource);
            if (url == null) {
                throw new IOException("The application is missing its bundled " + resource
                        + " directory, so there is nothing to install.");
            }
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException("Cannot read the bundled " + resource + " directory.", e);
        }
    }

    private void copyFrom(Path source, Path target, boolean replace) throws IOException {
        try (Stream<Path> tree = Files.walk(source)) {
            // Sorted so that a directory is always created before what goes inside it.
            for (Path entry : tree.sorted(Comparator.naturalOrder()).toList()) {
                // The two paths come from different file systems when reading a jar, so
                // the relative part is rebuilt as text rather than resolved directly.
                Path destination = target.resolve(source.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else if (replace || Files.notExists(destination)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
