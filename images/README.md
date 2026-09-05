# The images the course does not take as they come

Every service the launcher starts is defined in `src/main/resources/docker/docker-compose.yml`,
and most of them pull a published image straight from its own maintainers. The ones that
cannot are built from the directories here.

| Directory | Image | State |
|---|---|---|
| `neo4j-twitter/` | `ghcr.io/dandelion-lowcode/uocdb-neo4j-twitter:1.0` | Published. Carries the Twitter graph the course works on, 3.9M nodes and 5.5M relations, already expanded, with the APOC and graph-algorithms plugins its queries call. |
| `arangodb-imdb/` | `ghcr.io/dandelion-lowcode/uocdb-arangodb-imdb:1.0` | Published. Carries the IMDB graph the ArangoDB tutorial works on, in a database called `IMDB`, the name the module tells the student to pick. |
| `riak/` | `ghcr.io/dandelion-lowcode/uocdb-riak:1.0` | **Not published yet.** Still built on the student's own machine. |
| `jupyter/` | `ghcr.io/dandelion-lowcode/uocdb-jupyter:1.0` | **Not published yet.** Still built on the student's own machine. |

These build contexts lived on one person's Desktop until recently, which meant the images
could only ever be rebuilt on that machine and the reasoning behind them was one disk
failure from being lost. They are here so that a second person can rebuild them.

Nothing in this directory is built by CI. Each image is built once, by hand, and pushed.
Each directory that needs one holds a README saying what it needs that the repository
cannot carry. The compose file names a version, never `latest`, so a student who starts
the course six months from now gets the database the exercises were written against.

`riak/` and `jupyter/` are the exception, and the next section is about closing it. The
last section is about a fifth image that has no directory here, because there is nothing
to build: Vertica, which the course takes from one person's Docker Hub account.

## The two that are still built on a student's machine

Every other service pulls a pinned image. These two are built the first time a student
starts them, which costs minutes on a first run, needs a network for a `pip install` of
two dozen pinned packages, and puts a build -- the one step here with a real failure
surface -- on the very machine this launcher exists to keep simple. Jupyter's local tag
is `uocdb-jupyter:latest`, which is also the one unpinned name in the whole setup.

Publishing them removes all of that. The work below is written out rather than done
because **the images do not exist yet**: a compose file naming an image nobody can pull
breaks the launcher for every student, and it fails at the moment they click a service
rather than at review time.

### Step 1: build and push

Log in first, with a token that has `write:packages`:

    docker login ghcr.io

then, from the root of the repository:

    docker build -t ghcr.io/dandelion-lowcode/uocdb-riak:1.0 images/riak
    docker push  ghcr.io/dandelion-lowcode/uocdb-riak:1.0

    docker build -t ghcr.io/dandelion-lowcode/uocdb-jupyter:1.0 images/jupyter
    docker push  ghcr.io/dandelion-lowcode/uocdb-jupyter:1.0

Both packages are private when GHCR first creates them, and a student pulls anonymously.
Set each one to public on its package page, or the switch below fails for everyone but
the account that pushed it.

Building on an Apple Silicon machine is the one thing to be careful about. Today a
student on one of those builds Jupyter locally and gets a native `arm64` image; a single
`amd64` image published from an Intel machine would put every one of them under
emulation for the rest of the course. Jupyter's base, `python:3.13-slim`, is published
for both, so build it for both:

    docker buildx build --platform linux/amd64,linux/arm64 \
        -t ghcr.io/dandelion-lowcode/uocdb-jupyter:1.0 --push images/jupyter

Riak's base, `kefirgames/riak:2.2.3`, is `amd64` only, so Riak cannot follow. Confirm
with `docker manifest inspect kefirgames/riak:2.2.3` before deciding, and if it is
indeed `amd64` only, give the service `platform: linux/amd64` the way `vertica` already
has one, so that Docker says so plainly instead of failing with an exec format error.

### Step 2: swap the compose file over

In `src/main/resources/docker/docker-compose.yml`, the `riak` service ends in

        # Built here rather than pulled, unlike every other service, only because nobody has
        # published this image yet. The directory named below is put beside this file when
        # the application is built, out of images/riak/, and images/README.md holds the
        # change that switches this to a pull, ready for the day there is something to pull.
        build: ./riak

which becomes

        # Pulled like every other service. Built by hand from images/riak/, which is also
        # where the Dockerfile explains what is wrong with the image Basho left behind.
        image: ghcr.io/dandelion-lowcode/uocdb-riak:1.0

The paragraph above that one, about Riak being covered in theory only, is still true and
stays where it is.

The `jupyter` service begins

      jupyter:
        # Built on the student's machine for the same reason as riak, and at a worse price: a
        # first run of several minutes and a pip install of two dozen packages that needs the
        # network to succeed, on the one machine this launcher exists to keep simple. The tag
        # below is also the only unpinned name here, because a locally built image has no
        # version to be given. images/README.md holds the change that ends both.
        build:
          context: ./jupyter
        image: uocdb-jupyter:latest

which becomes

      jupyter:
        # Built by hand from images/jupyter/: a Python base and one driver per database the
        # course uses, pinned in requirements.txt so that a notebook which ran last term
        # still runs. Pulled here so that no student ever waits for that build, or needs a
        # working network for the twenty-odd packages it installs.
        image: ghcr.io/dandelion-lowcode/uocdb-jupyter:1.0

Nothing else in either service changes. In particular **the notebooks stay bind-mounted**:

        volumes:
          - ${UOCDB_NOTEBOOKS:-../notebooks}:/home/jovyan/work/notebooks

They are the student's own work, they are unpacked next to the compose file and never
overwritten once they exist, and a student who spent an evening on them has to find them
again the next morning. Baking them into the image would replace that evening with the
version this image was built from, every time the service is recreated. The mount is the
one thing that must survive this change untouched.

### Step 3: undo what was keeping the build working

Both build contexts are copied into the bundled `docker/` tree by two `<resource>`
entries in `pom.xml`, which exist only because Compose builds from a directory beside the
compose file. Once nothing is built, nothing needs copying: delete the `images/riak` and
`images/jupyter` entries, leaving the plain `src/main/resources` one.

That makes two assertions in `src/test/java/com/uoc/docker/BundledFilesTest.java` false --
`everythingDockerNeedsEndsUpOnTheDisk` looks for `docker/riak/Dockerfile`,
`docker/jupyter/Dockerfile` and `docker/jupyter/requirements.txt`, and
`theShellScriptRiakNeedsIsUnpackedToo` looks for `docker/riak/zz-bind-all-interfaces.sh`.
Both are about files that will no longer be unpacked, because they will no longer be
needed on a student's disk at all. Delete the four assertions and, with it, the second
test whole.

### Step 4: check

    mvn -o test
    mvn -o process-resources
    docker compose -f target/classes/docker/docker-compose.yml config

The last one must show `image:` and no `build:` for either service, and must still show
the notebooks mount. Then start both from the launcher on a machine that has never
pulled them, which is the only test that proves a student can.

## Vertica, which hangs on one person's Docker Hub account

The `vertica` service pulls `josepmeseguer/vertica-ce:24.1.0-0`. That is one person's
Docker Hub account, and nobody here has any claim on it. If it is deleted, renamed or
emptied, the course loses a database, and loses it on the morning a student first clicks
the service rather than at a moment anyone chose.

**The image is not being changed.** That was decided deliberately: this pinned version is
the one the course material was written against, and its VMart schema is what the
exercises ask questions about. Moving to another publisher, or another version, is a
change to the material as much as to this file, and it should not be made in the hour the
original disappears. What follows is the insurance instead -- a copy of these exact bytes
under a name the course controls, ready to switch to on the day it is needed.

### Mirroring it

Copy the manifest across without a 2.7 GB round trip through this machine:

    docker login ghcr.io
    docker buildx imagetools create \
        --tag ghcr.io/dandelion-lowcode/uocdb-vertica:24.1.0-0 \
        josepmeseguer/vertica-ce:24.1.0-0

or, with the image already pulled, the plain three:

    docker pull josepmeseguer/vertica-ce:24.1.0-0
    docker tag  josepmeseguer/vertica-ce:24.1.0-0 ghcr.io/dandelion-lowcode/uocdb-vertica:24.1.0-0
    docker push ghcr.io/dandelion-lowcode/uocdb-vertica:24.1.0-0

Then make the GHCR package public, as with the others. Keep the tag exactly `24.1.0-0`:
the mirror is a copy, not a new version, and a tag that says anything else invites
somebody to believe the two differ.

The copy this machine holds is

    josepmeseguer/vertica-ce@sha256:9248dbc3ee429827f89a96758b88d7a286762d50918e2e4bbfcda2dd18cb1cd9

which is worth comparing against the registry before mirroring, and against the mirror
afterwards. A tag can be moved by whoever owns it; a digest cannot.

### Switching to it, if that day comes

In `src/main/resources/docker/docker-compose.yml`, the `vertica` service begins

      vertica:
        image: josepmeseguer/vertica-ce:24.1.0-0
        platform: linux/amd64

and only the first of those two lines changes:

      vertica:
        # A mirror of josepmeseguer/vertica-ce:24.1.0-0, the same bytes under a name the
        # course controls. The original was one person's Docker Hub account, and one
        # deletion away from taking a database of this course with it.
        image: ghcr.io/dandelion-lowcode/uocdb-vertica:24.1.0-0
        platform: linux/amd64

`platform: linux/amd64` stays. The image is amd64 only, and saying so is what makes
Docker tell an Apple Silicon student it is emulating rather than fail with an exec format
error. Nothing else about the service changes, because nothing else about the image does.
