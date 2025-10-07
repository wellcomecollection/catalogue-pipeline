# Catalogue graph pipeline

Code and infrastructure for building the catalogue graph and populating the `works-indexed` and `concepts-indexed`
Elasticsearch indexes which power the catalogue API. The catalogue graph is a knowledge graph storing all Wellcome
catalogue works and concepts as well as external concepts and the relationships between them.

See the following RFCs for more context:

* [RFC 062: Wellcome Collection Graph overview and next steps](https://github.com/wellcomecollection/docs/tree/main/rfcs/062-knowledge-graph)
* [RFC 064: Graph data model](https://github.com/wellcomecollection/docs/tree/main/rfcs/064-graph-data-model/README.md)
* [RFC 066: Catalogue graph pipeline](https://github.com/wellcomecollection/docs/blob/main/rfcs/066-graph_pipeline/README.md)

## Pipeline orchestration

Pipeline execution is orchestrated using several state machines defined in AWS Step Functions. There are two main state
machines: `catalogue-pipeline-monthly` and `catalogue-pipeline-incremental`.

### Catalogue pipeline monthly

The monthly pipeline is responsible for downloading _source concepts_ from external sources (such as Library of
Congress, Wikidata, and MeSH), extracting relevant _entities_ (nodes and edges), and adding them to the catalogue graph.
Each node represents a source concept (e.g. a Library of Congress name), and each edge represents a relationship between
two nodes (e.g. a Wikidata concept A is _broader than_ a Wikidata concept B).

The `catalogue-pipeline-monthly` state machine consists of the following steps:

1. _Extractors_: Extract entities from all external sources by running `extractor` ECS tasks. Stream the results to CSV
   files stored in S3. Each task corresponds to a combination of source type and entity type (e.g. one task to extract
   LoC Concept nodes, and a separate one to extract LoC Concept edges). Individual tasks run in sequence (Wikidata API
   rate limits prevent us from running them in parallel).
2. _Bulk loaders_: Load extracted entities into the catalogue graph
   using [Neptune bulk loader](https://docs.aws.amazon.com/neptune/latest/userguide/bulk-load.html). Each CSV file
   outputted as part of the *Extractors* step is loaded separately using the `catalogue-graph-bulk-loader` state
   machine.
3. _Graph removers_: Remove unused nodes and edges from the catalogue graph by comparing entities bulk loaded as
   part of the current run to those loaded as part of the previous run.

The state machine is scheduled to run on a monthly basis, but can be triggered manually when needed.

### Catalogue pipeline incremental

The incremental pipeline populates final works and concepts indexes using data from the catalogue graph and from
the denormalised index (`works_denormalised`). The pipeline uses an incremental approach, only processing works
and concepts which changed within a given time window (as determined by the `state.mergedTime` timestamp in the
denormalised index).

The `catalogue-pipeline-incremental` state machine consists of the following steps:

1. _Open PIT_: Opens
   a [point in time](https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-open-point-in-time) (PIT)
   against the denormalised index. This PIT is used by all subsequent steps when reading data from the index to ensure
   consistency.
2. _Extractors_: Extract entities from the denormalised index. Stream the results to CSV files stored in S3. Individual
   extractor tasks run in parallel.
3. _Bulk loaders_: See [Catalogue pipeline monthly](#catalogue-pipeline-monthly) section above.
4. _Ingestors_: Index work and concept documents into final Elasticsearch indexes.

The state machine is scheduled to run every 15 minutes, processing the latest 15-minute window. For example,
the execution starting at 08:00:00 would process all denormalised work documents which were modified between 07:45:00
and 08:00:00.

## Running the pipeline manually

State machines can be triggered manually via
the [AWS console](https://eu-west-1.console.aws.amazon.com/states/home?region=eu-west-1#/statemachines).

Some state machines require JSON input. For example, the `catalogue-pipeline-incremental` requires input
in the following format:

```json
{
  "pipeline_date": "2025-08-14",
  "index_date": "dev",
  "window": {
    "start_time": "2025-10-07T06:00:00Z",
    "end_time": "2025-10-07T08:00:00Z"
  }
}
```

The `start_time` property is optional. If not specified, it will automatically be set to `<end_time> - 15 minutes`.

## Full reindex

When running a full reindex from source data (i.e. fully populating the denormalised index), the incremental pipeline
can keep running as usual, processing the latest 15-minute window every 15 minutes.

To reprocess all works or concepts *without* repopulating the denormalised index, run the relevant state machine or 
Lambda function in *full reindex* mode by leaving out the `window` property from the input. At the moment, some services
(e.g. `ingestor_loader` when processing concepts, or `ingestor_indexer` when processing works) only support full reindex
mode locally, since they are deployed as Lambda functions and processing all records would exceed the 15-minute
execution time limit.

## Service overview

The pipeline consists of several Lambda functions, all of which run from a single shared container image
(`unified_pipeline_lambda`) published to ECR; each function specifies its own module entrypoint via
`image_config.command` in Terraform.

Graph Lambda functions:
* `extractor`: Extracts a single entity type (nodes or edges) from a single source (e.g. LoC Names) and streams the
  transformed entities into the specified destination. To support longer execution times, the `extractor` is also
  available as an ECS task.
  Supported destinations include S3, SNS, and Neptune:
    * S3 is used when loading many entities in bulk via the Neptune bulk loader.
    * Neptune is used when loading a smaller number of entities directly into the cluster using openCypher queries.
    * SNS is used when loading entities using openCypher queries via the `indexer` Lambda function. This method was
      originally used for loading large numbers of entities into the cluster, but has since been superseded by the bulk
      load method and might be removed in the future.
* `bulk_loader`: Triggers a Neptune bulk load of a single S3 file created by the `extractor` service.
* `bulk_load_poller`: Checks the status of a bulk load job.
* `indexer`: Consumes openCypher queries from the SNS topic populated by the `extractor` Lambda function and runs them
  against the Neptune cluster. (There is an SQS queue between the SNS topic and the Lambda function and queries are
  consumed via an event source mapping). (This Lambda function is not in use at the moment.)
* `graph_remover`: Removes nodes and edges from the Neptune cluster. Nodes/edges are removed if they existed in a
  previous bulk load file but no longer exist in the latest one. Keeps an append-only log of deleted
  and added nodes/edges (with a retention period of one year) for debugging purposes.

Elasticsearch ingestor Lambda functions:
* `ingestor_loader`: Queries the denormalised index for all works which changed within a given time window,
  supplementing returned documents with data from the catalogue graph, creating final work or concept
  documents, and loading the results into parquet files in S3.
* `ingestor_indexer`: Consumes parquet files from S3, loading final documents into Elasticsearch.
* `ingestor_deletions`: Removes indexed concepts from Elasticsearch if corresponding 'Concept' nodes were removed from
  the catalogue graph. Uses the append-only log of deleted IDs created by the `graph_remover` to decide which
  documents to remove.

## Source code organisation

The `src` directory contains all Python source code for the graph pipeline (Python 3.13). Each Lambda's handler lives
in a module exposing a `lambda_handler` function (invoked by AWS) and often a `local_handler` for ad‑hoc local runs.

Because all Lambdas share one container image, anything added to `src` (and declared in `pyproject.toml`) becomes
immediately available to every function after the next image build. Terraform modules set:

```
package_type = "Image"
image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
image_config = { command = ["path.to.module.lambda_handler"] }
```

This simplifies dependency management and keeps runtime versions consistent. There are no `.zip` artifacts or
per-function `runtime`/`filename` settings anymore.

Subdirectories contain various modules and are shared by all Lambda functions.

* The `clients` directory contains the `LambdaNeptuneClient` and `LocalNeptuneClient` classes, both subclassing from
  `BaseNeptuneClient`. These classes are responsible for all communication with the Neptune client. This includes making
  openCypher API calls and triggering bulk loads.
* The `converters` directory contains classes for converting Pydantic models into a format expected by Neptune. This
  also includes converting various data types into a Neptune-compatible format. For example, lists are converted
  into a `||`-separated string (since Neptune does not support storing lists/arrays).
* The `models` directory contains Pydantic models for representing all node and edge types stored in the graph. Every
  entity extracted from a source must first be converted into one of these models before being loaded into the graph.
* The `query_builders` directory contains various utility functions for constructing openCypher queries (e.g. UNWIND
  queries) from a list of Pydantic entities.
* The `sources` directory contains classes for extracting entities from their source and streaming them for further
  processing. Each source class must implement the `stream_raw` method which must `yield` a single entity from the
  source.
* The `transformers` directory contains classes for transforming raw entities returned from the relevant source class
  into Pydantic models and streaming them to the desired destination. Each transformer class must subclass from the
  `BaseTransformer` class and implement an `transform_node` method (which accepts a single raw entity dictionary, and
  returns a single Pydantic model) and an `extract_edges` method (which also accepts a single raw entity dictionary, and
  yields a single Pydantic model). The BaseTransformer class implements a `stream_to_<destination>` method for each
  supported destination.

## Setting up the project

Run `uv sync` to install the project dependencies.

## Deployment

The pipeline deploys automatically on push to main via the
[catalogue-graph-ci](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/.github/workflows/catalogue-graph-ci.yml)
GitHub action. This builds & pushes the unified container image to ECR and updates all Lambda functions (Terraform
apply).

### Manual deployment

For ad‑hoc (non‑CI) deployment of the shared Lambda image:

```shell
# From catalogue_graph/
TAG=dev \
REPOSITORY_PREFIX=760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/ \
docker compose build unified_pipeline_lambda
docker compose push unified_pipeline_lambda

# (Optional) apply infrastructure changes
cd terraform
terraform plan
terraform apply
```

Extractor ECS image (separate service) can still be built/pushed similarly using the `extractor` target.

## Local execution

You can invoke handlers directly without building the container (fast iteration) or run inside the container for closer
parity.

Direct module invocation (bulk load status example):

```shell
AWS_PROFILE=platform-developer uv run bulk_load_poller.py --load-id=<some_id>
```

Extractor example:

```shell
AWS_PROFILE=platform-developer \
python3.13 extractor.py \
  --transformer-type=wikidata_linked_loc_concepts \
  --entity-type=nodes \
  --stream-destination=void \
  --sample-size=10
```

## Local Neptune experimentation

To run experimental Neptune queries locally, you can use the notebook in the `notebooks` directory. This notebook
connects to a Neptune instance running in the cloud and allows you to run openCypher queries against it.

The notebook uses utility functions from the graph pipeline project. To ensure these functions are accessible,
add the project to your PYTHONPATH or run Jupyter with uv:

```sh
uv run --with jupyter jupyter lab
```

## Running with local Elasticsearch

To run Elasticsearch locally, you can use `elasticsearch.docker-compose.yml` to start a local Elasticsearch instance.

`docker compose -f elasticsearch.docker-compose.yml up`

This will start Elasticsearch on `localhost:9200`, and Kibana on `localhost:5601`. Point local scripts or the container
at it via the normal env vars.

## Container entrypoints summary

| Lambda (logical name)    | image_config.command                                   |
|--------------------------|--------------------------------------------------------|
| bulk_loader              | bulk_loader.lambda_handler                             |
| bulk_load_poller         | bulk_load_poller.lambda_handler                        |
| graph_remover            | graph_remover.lambda_handler                           |
| graph_status_poller      | graph_status_poller.lambda_handler                     |
| graph_scaler             | graph_scaler.lambda_handler                            |
| indexer                  | indexer.lambda_handler                                 |
| ingestor_loader          | ingestor.steps.ingestor_loader.lambda_handler          |
| ingestor_loader_monitor  | ingestor.steps.ingestor_loader_monitor.lambda_handler  |
| ingestor_indexer         | ingestor.steps.ingestor_indexer.lambda_handler         |
| ingestor_indexer_monitor | ingestor.steps.ingestor_indexer_monitor.lambda_handler |
| ingestor_deletions       | ingestor.steps.ingestor_deletions.lambda_handler       |
| pit_opener               | pit_opener.lambda_handler                              |

All use the same ECR image tagged `:prod` in Terraform (promotion strategy can be revised later to use digests or staged
tags).
