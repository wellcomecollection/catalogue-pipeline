# The queues are flooded
## What?

The pipeline is in "day-to-day" mode, and tens of thousands of messages are backed up on the queue.

## Background

The pipeline has two modes:

* batch/reindexing = when it expects lots of updates, and scales up resources and batching behaviour to match.
* one-by-one/day-to-day = when it expects a handful of updates, and scales down to save money. Most of the day-to-day 
updates are from people editing records by hand.

If a large number of messages are encountered when it is not expecting them, the pipeline can become overwhelmed.
This can block any Works making it through the pipeline.

## How can this happen?

### Reindexing on a day-to-day pipeline
If a scaled-down pipeline has been left connected to the reindex queue, and someone triggers a full reindex, this can 
happen.

### The CALM/Sierra Sync

There is an overnight process that syncs the CALM/Sierra (archive/library) catalogues. 
It updates every record, although normally this is updating the "last synced" field, 
so the transformer will filter out all the updates – we'll already have the transformed Work in the pipeline. 
This is several hundred thousand records.

If, however, there is a change to the transformer that means most of these Works will appear to have changed, 
then they will progress through the pipeline to the more expensive stages, causing the backlog.

## What to do?

There are limits on being able to scale up and down in any given day, so you may not be able to simply switch the 
pipeline into the other mode in order to fix it.  You may need to scrap the pipeline or flush the queues and start again.

If the number of messages is sufficiently large, then flushing the queues and setting up a full reindex may be the most 
efficient way to resolve the problem.

It's always a good idea to work out why it happened, in case the underlying cause returns as soon as you fix this 
particular flood.
