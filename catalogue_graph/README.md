# Catalogue graph pipeline

Code and infrastructure for building the catalogue graph and populating the Elasticsearch index which powers theme
pages.

See the following RFCs for more context:

* [RFC 062: Wellcome Collection Graph overview and next steps](https://github.com/wellcomecollection/docs/tree/main/rfcs/062-knowledge-graph)
* [RFC 064: Graph data model](https://github.com/wellcomecollection/docs/tree/main/rfcs/064-graph-data-model/README.md)
* [RFC 066: Catalogue graph pipeline](https://github.com/wellcomecollection/docs/blob/main/rfcs/066-graph_pipeline/README.md)

## Architecture overview

The catalogue graph pipeline extracts concepts from various sources (e.g. LoC, MeSH) and stores them into the catalogue
graph database (running in Amazon Neptune). It consists of several Lambda functions:

* `extractor`: Extracts a single entity type (nodes or edges) from a single source (e.g. LoC Names) and streams the
  transformed entities into the specified destination. To support longer execution times, the `extractor` is also
  available as an ECS task.
  Supported destinations include S3, SNS, and Neptune:
    * S3 is used when loading many entities in bulk via the Neptune bulk loader.
    * Neptune is used when loading a smaller number of entities directly into the cluster using openCypher queries.
    * SNS is used when loading entities using openCypher queries via the `indexer` Lambda function. This method was
      originally used for loading large numbers of entities into the cluster, but has since been superseded by the bulk
      load method and might be removed in the future.
* `bulk_loader`: Triggers a Neptune bulk load of a single S3 file created by the `extractor` Lambda function.
* `bulk_load_poller`: Checks the status of a bulk load job.
* `indexer`: Consumes openCypher queries from the SNS topic populated by the `extractor` Lambda function and runs them
  against the Neptune cluster. (There is an SQS queue between the SNS topic and the Lambda function and queries are
  consumed via an event source mapping). (This Lambda function is not in use at the moment.)
* Elasticsearch "Ingestor" Lambda functions:
    * `ingestor_trigger`: Queries the graph database for catalogue originated concepts and returns a count of the
      results.
    * `ingestor_loader`: Queries the graph database for a subset of catalogue originated concepts and loads them into
      S3 as a parquet file.
    * `ingestor_indexer`: Consumes the parquet file from S3 and loads the data into Elasticsearch for retrieval by the
      Concepts API.
* `graph_remover`: Removes nodes and edges from the Neptune cluster. Nodes/edges are removed if they existed in a
  previous bulk load file but no longer exist in the latest one. Keeps an append-only log of deleted
  and added nodes/edges (with a retention period of one year) for debugging purposes.
* `ingestor_deletions`: Removes indexed concepts from Elasticsearch if corresponding 'Concept' nodes were removed from the
  catalogue graph. Uses the append-only log of deleted IDs created by the `graph_remover` to decide which
  documents to remove.

Lambda function/ECS task execution is orchestrated via AWS Step Functions (see `terraform` directory). Several state
machines are utilised for this purpose:

* `concepts-pipeline_daily`: Represents the core concepts pipeline, extracting WC catalogue works and concepts,
  loading them into the graph, and indexing them into Elasticsearch. Scheduled to run daily.
* `concepts-pipeline_monthly`: Extracts source concepts (from Wikidata, MeSH, and LoC) and loads them into the
  Neptune cluster. Scheduled to run monthly.
* `catalogue-graph-pipeline`: Extracts all graph entities from their source and loads them into the Neptune cluster.
  Triggers the `catalogue-graph-extractors` state machine, followed by the `catalogue-graph-bulk-loaders` state
  machine.
* `catalogue-graph-extractors`: Runs `extractor` ECS tasks in sequence, one for each combination of
  source type and entity type (e.g. one for LoC Concept nodes, one for LoC Concept edges, etc.). (Note that individual
  extractors cannot run in parallel due to Wikidata API rate limits.)
* `catalogue-graph-bulk-loaders`: Triggers `catalogue-graph-bulk-loader` state machine instances in sequence, one for
  each combination of transformer type and entity type.
* `catalogue-graph-bulk-loader`: Invokes a single `bulk_loader` Lambda function to start a bulk load job. Then
  repeatedly invokes the `bulk_load_poller` Lambda function to check the status of the job until it completes.
* `catalogue-graph-single-extract-load`: Not part of the full pipeline. Extracts and loads a single entity type by
  running the `extractor` ECS task, followed by the `catalogue-graph-bulk-loader` state machine. Useful for
  updating the graph after a change in a single source/transformer without having to run the full pipeline.
* `catalogue-graph-ingestor`: Represents the Elasticsearch ingestor pipeline. Triggers
  the `catalogue-graph-ingestor-trigger` function, followed by the `catalogue-graph-ingestor-loader`
  and `catalogue-graph-ingestor-indexer` functions
  as [state map steps](https://docs.aws.amazon.com/step-functions/latest/dg/state-map.html), allowing for
  parallelisation of the ingestor process.

## Running the pipeline

All state machines can be triggered manually via
the [AWS console](https://eu-west-1.console.aws.amazon.com/states/home?region=eu-west-1#/statemachines).

Some state machines require JSON input. For example, the `catalogue-graph-single-extract-load` requires input
in the following format:

```json
{
  "transformer_type": "loc_concepts",
  "entity_type": "nodes",
  "sample_size": null
}
```

## Source code organisation

The `src` directory contains all Python source code for the graph pipeline. (In production, we use Python 3.13.)

The root of the directory contains a Python file for each Lambda function in the pipeline. Each file has
a `lambda_handler` function (used when running in production) and a `local_handler` function (used when running
locally).

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

The pipeline deploys automatically on push to main via
the [catalogue-graph-ci](https://github.com/wellcomecollection/catalogue-pipeline/blob/main/.github/workflows/catalogue-graph-ci.yml)
GitHub action. This action deploys the latest code to all Lambda functions and publishes a new image to ECR.

### Manual deployment

For manual deployment:

* **Lambda functions**: Use the local deployment script from the repository root:
  ```shell
  ./scripts/local/deplot_python_lambda.sh catalogue_graph <function-name>
  ```

* **Extractor container**: Use docker compose commands from the catalogue_graph directory:
  ```shell
  # Build the container
  TAG=dev \
  REPOSITORY_PREFIX=760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/ \
  PYTHON_IMAGE_VERSION=3.13-slim \
  docker compose build extractor
  
  # Push to ECR (requires appropriate AWS credentials)
  TAG=dev \
  REPOSITORY_PREFIX=760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/ \
  PYTHON_IMAGE_VERSION=3.13-slim \
  docker compose push extractor
  ```



## Local execution

To run one of the Lambda functions locally, navigate to the `src` directory and then run the chosen function via the
command line. For example, to check the status of a bulk load job, run the following:

```shell
AWS_PROFILE=platform-developer uv run bulk_load_poller.py --load-id=<some_id>
```

To run an extractor, use the following:

```shell
S3_BULK_LOAD_BUCKET_NAME=wellcomecollection-neptune-graph-loader \
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

This will start Elasticsearch on `localhost:9200`, and Kibana on `localhost:5601`. Lambda functions can be configured to
use this Elasticsearch instance by setting the relevant environment variables when starting the Lambda function locally.
