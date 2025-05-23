# Relation Embedder Subsystem

## What does it do?

Denormalises the relationships between records in a path hierarchy.

Only a subset of records goes through the Relation Embedder Subsystem: those that have a CollectionPath, ie. are part of a hierarchy or group of records that are connected as parts of the same Series or Archive Collection. 

See 
  - [Path Concatenator](./path_concatenator/README.md)
  - [Batcher](./batcher/README.md)
  - [Relation Embedder](./relation_embedder/README.md)
for more details.

As a result of running through the Relation Embedder, records in a hierarchy
will contain links to siblings, ancestors and children.

