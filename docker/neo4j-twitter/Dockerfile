# The Twitter graph the course works on, built into the image.
#
# Pinned to 3.5.3: the dump was written by that branch and the plugins beside it are
# built for it. Neither moves forward without rebuilding the dataset.
#
# The graph travels as a dump rather than as an expanded database, because the dump holds
# the same four million nodes in a fifth of the space. It is expanded here, at build time,
# so a student never waits for it.

# Expanding the dump needs the tool but not what it leaves behind, so it happens in a
# stage of its own and only the resulting database is kept.
FROM neo4j:3.5.3 AS loader
COPY twitter.dump /tmp/twitter.dump
# The target has to exist first: in 3.5 the --force flag means "overwrite what is there"
# and the load refuses to run when there is nothing to overwrite.
RUN mkdir -p /data/databases/graph.db && neo4j-admin load --from=/tmp/twitter.dump --database=graph.db --force

# The data is copied in rather than mounted from the project directory so that this
# service behaves like every other one here: whatever a student does to it, taking the
# container down and bringing it up again gives them the dataset back. A student
# practising deletions on a graph of four million nodes should not be able to destroy it
# for good, and there is no undo for a bind mount.
FROM neo4j:3.5.3

# Ownership is set as the files are copied. A separate "RUN chown -R" would rewrite every
# file into a second layer, and the database would travel in the image twice.
COPY --chown=neo4j:neo4j conf/ /conf/
COPY --chown=neo4j:neo4j plugins/ /var/lib/neo4j/plugins/
COPY --from=loader --chown=neo4j:neo4j /data/ /data/
