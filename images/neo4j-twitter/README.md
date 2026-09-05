# neo4j-twitter

The graph the course works on: about 3.9 million nodes and 5.5 million relationships,
already loaded. Published as `ghcr.io/dandelion-lowcode/uocdb-neo4j-twitter:1.0` and
pulled by the `neo4j-twitter` service in `src/main/resources/docker/docker-compose.yml`
like any other image.

## What is not in this directory

**This image cannot be built from a clean clone**, by design. Three of the files the
Dockerfile copies are missing here, and a rebuild has to put all three back first:

All three are kept together on the `twitter-1.0` release of
`dandelion-lowcode/uocdb-datasets`, a private repository, and one command puts them back:

    gh release download twitter-1.0 --repo dandelion-lowcode/uocdb-datasets \
       --dir images/neo4j-twitter

which lands the jars beside the dump rather than in `plugins/`; move them in.

| Missing file | Size | Where else it comes from |
|---|---|---|
| `twitter.dump` | 336 MB | Nowhere public: it is the course's own export, and it holds a real social graph, which is why the repository holding it is private. Before that release existed, the only copy was on the machine that built the image. |
| `plugins/apoc-3.5.0.9-all.jar` | 15 MB | `neo4j-contrib/neo4j-apoc-procedures` on GitHub, release `3.5.0.9`, the `-all` asset. |
| `plugins/graph-algorithms-algo-3.5.3.4.jar` | 1.4 MB | `neo4j-contrib/neo4j-graph-algorithms` on GitHub, release `3.5.3.4`. |

The dump is out because GitHub refuses any single file over 100 MB, so it cannot live
here at all. Git LFS would take it, but the free allowance is a gigabyte of transfer a
month and one build would spend a third of it. The jars are out because they are
third-party binaries upstream already publishes, and this repository ignores every jar
regardless. The `.gitignore` beside this file keeps all three from being added by
accident: a 336 MB file is far easier to commit than to take back out of a history.

The dump the published image was built from is the one with this checksum, worth
confirming before a rebuild if it arrived by any route other than a copy:

    sha256  2f24dfabb7db0e86b92bd66a739ed0e79b11dc096507c58189486eccf9a65eac
    bytes   352253628

`conf/neo4j.conf`, which is here, and the two plugins, which are not, belong together:
the configuration whitelists `apoc.*` and `algo.*`, which is exactly what those jars
provide, and both are built for the 3.5 branch the Dockerfile pins. None of the three
moves forward on its own.

None of this is a problem in practice, because no student builds this. The image is
built once, by hand, on a machine that has all three files, and published to a registry;
the compose file then pulls it exactly like it pulls MongoDB or Redis.

## Rebuilding it

With the three missing files put back in this directory, by the command above or by
hand:

    docker build -t ghcr.io/dandelion-lowcode/uocdb-neo4j-twitter:<version> .
    docker push  ghcr.io/dandelion-lowcode/uocdb-neo4j-twitter:<version>

then point the `neo4j-twitter` service in `src/main/resources/docker/docker-compose.yml`
at the new tag.
