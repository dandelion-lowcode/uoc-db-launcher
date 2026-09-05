# UOC DB Launcher

The databases a UOC NoSQL course works on, each one started with a tick in a menu and
queried from a console beside it. It replaces the twelve-gigabyte virtual machine the
course used to hand out: a student installs Docker Desktop, unzips this, and clicks.

Eleven services: MongoDB, Cassandra, Neo4j, a second Neo4j carrying the Twitter graph,
Redis, Riak, CockroachDB, Vertica, ArangoDB with the IMDB graph, Elasticsearch, and a
Jupyter notebook server. Each console speaks the language that database is driven with --
`mongosh`, `cqlsh`, Cypher, `redis-cli`, HTTP requests, SQL, AQL -- and an empty one shows
a worked example of it.

## Running it while working on it

    mvn compile exec:java

Docker Desktop has to be running; nothing else is needed. Services are started from the
**Services** menu and their images are downloaded the first time, which the trace box
reports as it happens.

## The tests

    mvn test                        # everything that needs no containers, about a minute
    mvn test -Dexcluded.groups=     # every test, driving real containers

The second one starts each database in turn and asks it the questions the consoles
promise, so it needs a Docker daemon and pulls several gigabytes the first time. Both run
in CI: the first on every push, the second on a tag or on request.

## Releasing

Tag it. `.github/workflows/package.yml` builds a self-contained application for Windows,
Intel macOS, Apple Silicon macOS and Linux, and attaches the four archives to the release.

    git tag -a v1.2.0 -m "..."
    git push origin v1.2.0

The version comes from the tag, not from the POM. Nothing is signed, because the course
has no certificate, so the archives are plain directories rather than installers.

jpackage cannot cross-build, which is why there is one runner per system. Java itself
travels inside each archive, cut down to the modules the application actually uses, so a
student needs no JDK.

## What lives where

    src/main/java/com/uoc/
        docker/          Talking to Docker: the compose commands, and the state machine
                         that decides what a service is doing from what Docker reports
        docker/client/   One class per console: the command each database is queried with
        ui/              The window: the tabs, the consoles, the services panel
        ansi/            Colouring what a client prints, from the theme's own palette
        i18n/            Catalan, Spanish and English, switchable while running
        platform/        The three questions the operating system answers differently
    src/main/resources/
        docker/          docker-compose.yml, the one definition of every service
        i18n/            The translations, and the worked example each console shows
        themes/          The FlatLaf palettes, ANSI colours included
        notebooks/       What Jupyter opens
        icons/           One per service, plus the application's own
    src/main/packaging/  The application icon in the formats jpackage demands
    images/              One directory per image the course builds for itself, whether it
                         is published or, in Riak's and Jupyter's case, not yet

## The prepared images

Two services do not use a published image as it comes. Both are built by hand from a
build context in `images/`, pushed to GHCR, and then pulled by `docker-compose.yml` like
any other image:

| Image | What it carries | Built from |
|---|---|---|
| `ghcr.io/dandelion-lowcode/uocdb-neo4j-twitter:1.0` | 3.9M nodes, 5.5M relations | `images/neo4j-twitter/`, which needs a 336 MB dump and two plugin jars that no repository can hold |
| `ghcr.io/dandelion-lowcode/uocdb-arangodb-imdb:1.0` | The IMDB graph, in a database called `IMDB` | `images/arangodb-imdb/`, which downloads the dataset during the build |

Only the ArangoDB one rebuilds from a clean clone. The Neo4j one needs three files put
back beside its Dockerfile first, and its README says which they are and where a rebuild
gets them; the ArangoDB one says why its Dockerfile ends in a symbolic link that looks
pointless and is not. Neither is built by a student or by CI: the compose file names a
published version, and that is all a student's machine ever sees.

Riak and Jupyter are the two the compose file still builds, on the student's own machine,
from `images/riak/` and `images/jupyter/`. They are meant to be published like the other
two, and `images/README.md` holds that change written out step by step -- the builds, the
compose lines, and what else has to move with them -- waiting only on somebody with
access to push the images. Until they exist, the `build:` entries stay: a compose file
naming an image nobody can pull leaves every student unable to start the service.

Because Compose builds from a directory beside the compose file, and the compose file is
unpacked out of the jar, `pom.xml` copies those two contexts into the bundled `docker/`
tree at build time. That is the alternative to keeping a second copy of each Dockerfile
under `src/main/resources`, and it comes out when they are published.

## Where a student's things are kept

The application unpacks the compose file and the notebooks into the user's own data
directory, because an installed copy sits somewhere it may not write to. Which directory
that is, and how the system's dark mode and its notion of a menu shortcut are found, is
all in `com.uoc.platform`. Preferences -- theme, language, zoom, console font, and which
services a student had open -- go through `java.util.prefs`.
