# The images the course does not take as they come

Every service the launcher starts is defined in `src/main/resources/docker/docker-compose.yml`,
and most of them pull a published image straight from its own maintainers. The ones that
cannot are built here, published to GHCR by hand, and then pulled like any other.

| Directory | Image | Why it is not the stock one |
|---|---|---|
| `neo4j-twitter/` | `ghcr.io/dandelion-lowcode/uocdb-neo4j-twitter:1.0` | Carries the Twitter graph the course works on, 3.9M nodes and 5.5M relations, already expanded, along with the APOC and graph-algorithms plugins its queries call. |
| `arangodb-imdb/` | `ghcr.io/dandelion-lowcode/uocdb-arangodb-imdb:1.0` | Carries the IMDB graph the ArangoDB tutorial works on, in a database called `IMDB`, which is the name the module tells the student to pick. |

These build contexts lived on one person's Desktop until now, which meant the images
could only ever be rebuilt on that machine and the reasoning behind them was one disk
failure from being lost. They are here so that a second person can rebuild them.

Nothing in this directory is built by a student, by the application, or by CI. Each
image is built once, by hand, and pushed; each directory holds a README saying how, and
saying what it needs that the repository cannot carry. The compose file names a version,
never `latest`, so a student who starts the course six months from now gets the database
the exercises were written against.
