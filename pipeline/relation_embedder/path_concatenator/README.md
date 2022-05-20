# Path Concatenator

## What?

Unlike records from other systems which each store their full path to the root of their hierarchy,
records from Sierra only know their immediately adjacent parent/child relations.

This makes it impossible to construct a full hierarchical path just from the data in a single record (unless that
record is either the root or a child of the root).

When a Sierra record in a hierarchy is discovered, it uses the path_concatenator to complete its place in the hierarchy
if necessary.

See [RFC 046: Transitive hierarchies in Sierra](https://github.com/wellcomecollection/docs/tree/main/rfcs/046-transitive-sierra-hierarchies)
for more detail.

## How?

1. Given a record we've been asked to process, the first and last segments of the path
    * e.g. given a path, `root/branch/leaf` - it will use `root`, and `leaf`.
2. Search for records whose last segment matches the first segment of this record. This should match exactly one record; if not, do nothing.
    * e.g. search for records matching `*/root`
3. Replace the first segment in the record from step 1 with the collectionPath of the record in step 2.
    * e.g. this record is `d/e/f`, there exists `b/c/d`, the collectionPath for this record becomes `b/c/d/e/f`
4. Search for records whose first segment matches the last segment of this record.
    * search for records matching `leaf/*`
5. Replace the first segment in the records from step 4 with the collectionPath of the record we're updating
    * e.g. a path `leaf/1/2` would become `root/branch/leaf/1/2`
6. Notify downstream (batcher) of all changed paths.
7. Notify downstream (batcher) of the current path if it is unchanged.

This is a deliberately ordered list of steps, so that the current record *first* gets its own path embellished, *then*
goes on to embellish the paths of its children with that new path.  Updating the child paths with the current record's
path _before_ updating the current record's path would result in the child records having incomplete paths.

### Shortcuts

If the path of a parent does not contain `/`, step 3, above, can be skipped.
If the path of the current record at step 4, above does not contain `/`, skip to step 7.

## Scale

This is expected to run only on...

* those Sierra records that generate a collectionPath,
* of those, it will only be triggered when the path has a `/`
* of those, it will only change records when both a parent and a child have a path with `/`

As such, it is unlikely to run on many records at once, when it does, it is unlikely to 
modify many records at all.

At time of writing, this is only expected to modify records in the Fallaize Collection.
There are 1313 records that match a free-text search for Fallaize.  There are 3920 records
that may trigger this process (Sierra Works with `/` in the collectionPath)

Middle records in the Fallaize collection tend to be parents of around 10 child records.
e.g. https://wellcomecollection.org/works/u63yc4fs (aka `3303244i/3288731i`)

The largest subcollection contains 57 children
https://wellcomecollection.org/works/wzde8hdw (aka `3303244i/529472i`).

The largest set of children this can be expected to run on, given current data, is 57.
That is only if all of the children of `wzde8hdw` have been processed _before_ that record itself.


## In Pictures
How this fits into the pipeline.

### When all the steps above cause changes

```mermaid
sequenceDiagram
    participant Upstream Stage
    participant Upstream Database
    participant Path Concatenator
    participant Downstream Stage
    Upstream Stage->>Path Concatenator: Here is a path
    Path Concatenator->>Path Concatenator: Update works at and below that path
    Path Concatenator->>Upstream Database: Save Merged Work
    Path Concatenator->>Downstream Stage: Here are the paths I changed
```

### When none of the steps above cause changes

```mermaid
sequenceDiagram
    participant Upstream Stage
    participant Upstream Database
    participant Path Concatenator
    participant Downstream Stage
    Upstream Stage->>Path Concatenator: Here is a path
    Path Concatenator->>Path Concatenator: Nothing to do
    Path Concatenator->>Downstream Stage: Here is the path I got
```

### When the given path remains the same, but its children change

```mermaid
sequenceDiagram
    participant Upstream Stage
    participant Upstream Database
    participant Path Concatenator
    participant Downstream Stage
    Upstream Stage->>Path Concatenator: Here is a path
    Path Concatenator->>Path Concatenator: Update work below that path
    Path Concatenator->>Downstream Stage: Here is the path I got and all its child paths
```
