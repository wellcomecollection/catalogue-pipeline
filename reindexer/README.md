# reindexer

Normally, the transformation pipeline is triggered when we get an update from the source system/adapter:

<!--
  Generated from Mermaid config (https://mermaid-js.github.io/mermaid/#/ /
  https://mermaid.live)

  graph LR
      S[source system] \-\-> A
      A[adapter] \-\-> T(transformer)

  Note: the double hyphens have been escaped for this HTML comment;
  you'll need to remove them to render the diagram.
-->

<img src="./adapter.svg" alt="A flow chart showing the source system pointing to the adapter, and the adapter pointing to the transformer.">

The **reindexer** is a second trigger for the transformer: it lets us transform a record even when there hasn't been an update in the source system.

<!--
  Generated from Mermaid config (https://mermaid-js.github.io/mermaid/#/ /
  https://mermaid.live)

  graph LR
      S[source system] \-\-> A
      A[adapter] \-\-> T(transformer)
      R[reindexer] \-\-> T

  Note: the double hyphens have been escaped for this HTML comment;
  you'll need to remove them to render the diagram.
-->

<img src="./reindexer.svg" alt="The same flow chart as before, now with the reindexer being a second input to the transformer.">

This is useful if, say, we've modified the transformation logic and we want to reapply it to an existing record.

We also use it when we have a new pipeline we want to populate with data.



## Types of reindex

*   A **complete** reindex will re-send all the records for a given source system, e.g. all the Sierra records.
    We use it when we're populating a new pipeline.

*   A **partial** reindex will re-send a small number of records, e.g. 10 CALM records.
    It's useful if you're changing how the pipeline behaves and want to test it still works correctly.

*   A **specific** reindex will re-send records with a given set of IDs.



## Key components

*   The **reindex_worker** is the Scala service that sends notifications to the transformer asking it to re-transform a record.

    It's a standard SQS-backed service: it receives **reindex requests** (e.g. please do a partial reindex of CALM with 10 records), and sends the appropriate downstream notifications (e.g. 10 notifications for the 10 records).

*   The **start reindex script** (`start_reindex.py`) sends a reindex request.
    If you run the script without any arguments, it prompts you for what sort of reindex you want to run.



## Connecting a pipeline to the reindexer

By default, transformers aren't connected to a reindexer.

<!--
  Generated from Mermaid config (https://mermaid-js.github.io/mermaid/#/ /
  https://mermaid.live)

  graph LR
      R[reindexer] -.-> T1(existing pipeline/transformer)
      R[reindexer] -\-> T2(new pipeline/transformer)

  Note: the double hyphens have been escaped for this HTML comment;
  you'll need to remove them to render the diagram.
-->

When we create a new, empty pipeline, we connect the reindexer so we can do a complete reindex and populate the pipeline.
Once that reindex is done, we disconnect the pipeline.

<img src="./pipelines.svg" alt="A flow chart with a reindexer pointing to an existing pipeline/transformer, and a new pipeline/transformer. The arrow pointing to the existing transformer is dashed (disconnected), while the arrow to the new transformer is solid (connected).">

This is so that when we create the next pipeline and do a complete reindex into that, we don't re-send every record in the already existing pipeline.
This reduces unnecessary processing in that pipeline.

You can connect a pipeline to a reindexer, by modifying the [pipeline Terraform config](https://github.com/wellcomecollection/catalogue-pipeline/tree/main/pipeline/terraform).
Specifically, you set `reindexing_state.connect_reindex_topics = true` on the pipeline that you want the reindexer to update.
