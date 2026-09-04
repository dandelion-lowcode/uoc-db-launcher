# neo4j-twitter

The graph the course works on: about 3.9 million nodes and 5.5 million relationships.

**This image cannot be built from a clean clone**, by design. Two of its inputs are not
in the repository:

- `twitter.dump`, 336 MB. GitHub refuses any single file over 100 MB, so it cannot live
  here at all. Git LFS would take it, but the free allowance is a gigabyte of transfer a
  month and one build would spend a third of it.
- `plugins/apoc-3.5.0.9-all.jar`, a third-party binary that is downloaded rather than
  kept under version control.

Neither is a problem in practice, because no student builds this. The image is built
once, by hand, from a machine that has both files, and published to a registry;
`docker/docker-compose.yml` then pulls it exactly like it pulls MongoDB or Redis.

To rebuild it after changing the dataset, with both files present in this directory:

    docker build -t <registry>/uocdb-neo4j-twitter:<version> .
    docker push  <registry>/uocdb-neo4j-twitter:<version>

then point the `neo4j-twitter` service in `docker/docker-compose.yml` at the new tag.
