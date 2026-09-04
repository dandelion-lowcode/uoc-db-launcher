# Riak for the course, on a base a decade newer than the one Basho left behind.
#
# basho/riak-kv has been untouched since Basho closed in 2017: Ubuntu 14.04, a Python
# process manager it does not need, 1.2 GB. This is the community continuation of the
# same Riak version the course material was written against, at a third of the size and
# with curl already in it, which is what the notes use and what the console runs.
FROM kefirgames/riak:2.2.3

COPY zz-bind-all-interfaces.sh /etc/riak/prestart.d/zz-bind-all-interfaces.sh
RUN chmod +x /etc/riak/prestart.d/zz-bind-all-interfaces.sh
