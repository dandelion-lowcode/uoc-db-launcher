# arangodb-imdb

ArangoDB with the IMDB example graph already in it: 12,862 films, 50,134 people, 30
genres and 118,325 relations, in a database called `IMDB`, which is the name the module
`PID_00299297` tells the student to pick after logging in.

Published as `ghcr.io/dandelion-lowcode/uocdb-arangodb-imdb:1.0` and pulled by the
`arangodb` service in `src/main/resources/docker/docker-compose.yml`.

Unlike `neo4j-twitter`, this one **builds from a clean clone**: nothing is missing here.
The dataset is 6.8 MB and public, and the build downloads it from the `DUMP_URL` the
Dockerfile pins rather than keeping a copy under version control. The only thing a
rebuild needs is a network.

    docker build -t ghcr.io/dandelion-lowcode/uocdb-arangodb-imdb:<version> .
    docker push  ghcr.io/dandelion-lowcode/uocdb-arangodb-imdb:<version>

then point the `arangodb` service in `src/main/resources/docker/docker-compose.yml` at
the new tag.

## Why an image at all

The dataset only has to be restored once, and the alternative was shipping 43 MB of JSON
inside every download, including for the students who never open ArangoDB.

The restore itself happens the first time a container starts on an empty data directory,
because the base image will not let a build write into its own data directory. It takes
about three seconds, and only ever happens again if the volume is removed.

## Checking it worked

    docker run --rm -e ARANGO_ROOT_PASSWORD=rootpassword <image> &
    arangosh --server.database IMDB --server.password rootpassword \
             --javascript.execute-string 'print(db._query("RETURN LENGTH(imdb_vertices)").toArray())'

should print `63026`. If it prints nothing and `db._databases()` shows only `_system`,
the symbolic link at the end of the Dockerfile is missing: read the comment there.
