# The IMDB graph the ArangoDB tutorial works on, carried inside the image.
#
# Pinned to the same version the launcher's compose file used before this image existed,
# so the only difference a student sees is that the database is already there.
FROM arangodb:3.12.11

# ArangoDB's own example dataset: the imdb_vertices and imdb_edges collections and the
# named graph that joins them, which is what every figure and every query in the module
# refers to. The 2024 course setup downloaded this same file and unzipped it at the root
# of the filesystem, where nothing ever loaded it.
ARG DUMP_URL=https://github.com/arangodb/example-datasets/releases/download/imdb-graph-dump-rev2/imdb_graph_dump_rev2.zip

# The dump is put where the image's own entrypoint looks for one: every directory under
# dumps/ is restored into a database named after it, the first time the server starts on
# an empty data directory. So the directory is called IMDB, and that is the database the
# tutorial tells the student to pick after logging in.
#
# Restoring at build time instead would start faster, and is not possible: the base image
# declares /var/lib/arangodb3 a volume, and whatever a build writes inside a volume is
# thrown away at the end of that layer.
RUN wget -q -O /tmp/imdb.zip "$DUMP_URL" \
    && unzip -q /tmp/imdb.zip -d /tmp \
    && mkdir -p /docker-entrypoint-initdb.d/dumps \
    && mv /tmp/dump /docker-entrypoint-initdb.d/dumps/IMDB \
    && rm /tmp/imdb.zip

# Where that restore would otherwise land, and the reason this line exists.
#
# The entrypoint starts a temporary server for the restore before it has dropped the
# command name from its argument list, so it runs "arangod arangod ...". arangod reads a
# lone positional argument as the data directory, so that server -- and everything
# restored into it -- lives in /arangod; the real server then starts on
# /var/lib/arangodb3 and finds nothing. The log says the database was created, and it is
# gone by the time the container has finished starting.
#
# Passing --database.directory does not help, the positional wins. Pointing /arangod at
# the real data directory does, and costs one symbolic link.
RUN ln -s /var/lib/arangodb3 /arangod
