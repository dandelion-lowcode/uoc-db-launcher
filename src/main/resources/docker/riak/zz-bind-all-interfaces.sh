#!/usr/bin/env bash
# The image writes the listeners bound to the container's own address, which leaves them
# unreachable from inside the container itself. The query console runs curl in there, and
# the course notes address localhost, so both listeners are moved to every interface.
#
# The entrypoint runs these scripts in name order, so this one comes after the script that
# generates riak.conf and has the final word on it.
sed -i 's|^listener\.http\.internal = .*|listener.http.internal = 0.0.0.0:8098|' "$RIAK_CONF"
sed -i 's|^listener\.protobuf\.internal = .*|listener.protobuf.internal = 0.0.0.0:8087|' "$RIAK_CONF"
