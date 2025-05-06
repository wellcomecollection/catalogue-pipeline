# Catalogue graph pipeline

Code and infrastructure for building the catalogue graph.

See the following RFCs for more context:

* [RFC 062: Wellcome Collection Graph overview and next steps](https://github.com/wellcomecollection/docs/tree/main/rfcs/062-knowledge-graph)
* [RFC 064: Graph data model](https://github.com/wellcomecollection/docs/tree/main/rfcs/064-graph-data-model/README.md)
* [RFC 066: Catalogue graph pipeline](https://github.com/wellcomecollection/docs/blob/main/rfcs/066-graph_pipeline/README.md)

## Architecture overview

The catalogue graph pipeline extracts concepts from various sources (e.g. LoC, MeSH) and stores them into the catalogue
graph database (running in Amazon Neptune). It consists of several Lambda functions:

* `extractor`: Extracts a single entity type (nodes or edges) from a single source (e.g. LoC Names) and streams the
  transformed entities into the specified destination. Supported destinations include S3, SNS, and Neptune:
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
  previous bulk load file but no longer exist in the latest one. The Lambda also keeps an append-only log of deleted
  and added nodes/edges (with a retention period of one year) for debugging purposes.
* `index_remover`: Removes indexed concepts from Elasticsearch if corresponding 'Concept' nodes were removed from the
  catalogue graph. The Lambda uses the append-only log of deleted IDs created by the `graph_remover` to decide which
  documents to remove.

Lambda function execution is orchestrated via AWS Step Functions (see `terraform` directory). Several state machines are
utilised for this purpose:

* `catalogue-graph-pipeline`: Represents the full pipeline, extracting all concepts and loading them into the cluster.
  Triggers the `catalogue-graph-extractors` state machine, followed by the `catalogue-graph-bulk_loaders` state machine.
* `catalogue-graph-extractors`: Invokes `extractor` Lambda function instances in parallel, one for each combination of
  source type and entity type (e.g. one for LoC Concept nodes, one for LoC Concept edges, etc.).
* `catalogue-graph-bulk-loaders`: Triggers `catalogue-graph-bulk-loader` state machine instances in sequence, one for
  each combination of transformer type and entity type.
* `catalogue-graph-bulk-loader`: Invokes a single `bulk_loader` Lambda function to start a bulk load job. Then
  repeatedly invokes the `bulk_load_poller` Lambda function to check the status of the job until it completes.
* `catalogue-graph-single-extract-load`: Not part of the full pipeline. Extracts and loads a single entity type by
  invoking the `extractor` Lambda function, followed by the `catalogue-graph-bulk-loader` state machine. Useful for
  updating the graph after a change in a single source/transformer without having to run the full pipeline.
* `catalogue-graph-ingestor`: Represents the Elasticsearch ingestor pipeline. Triggers
  the `catalogue-graph-ingestor-trigger` function, followed by the `catalogue-graph-ingestor-loader`
  and `catalogue-graph-ingestor-indexer` functions
  as [state map steps](https://docs.aws.amazon.com/step-functions/latest/dg/state-map.html), allowing for
  parallelisation of the ingestor process.

## Running the pipeline

The full pipeline can be triggered manually via
the [AWS console](https://eu-west-1.console.aws.amazon.com/states/home?region=eu-west-1#/statemachines/view/arn%3Aaws%3Astates%3Aeu-west-1%3A760097843905%3AstateMachine%3Acatalogue-graph-pipeline).

The `catalogue-graph-single-extract-load` pipeline can also be triggered via the console, requiring input in the
following format:

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

Run `./scripts/setup.sh` to install the project dependencies.

## Deployment

The pipeline does not currently have an automated deployment process in place. To deploy a new version of the source
code to all Lambda functions, run the `create_zip.sh` script (to create a new `build.zip` file), followed by
a `terraform apply` command (to upload the new zip file to all Lambda functions):

```sh
sh create_zip.sh
cd terraform
terraform apply
```

## Local execution

To run one of the Lambda functions locally, navigate to the `src` directory and then run the chosen function via the
command line. For example, to check the status of a bulk load job, run the following:

```shell
AWS_PROFILE=platform-developer python3.13 bulk_load_poller.py --load-id=<some_id>
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

To run experimental Neptune queries locally, create a new Python file in the `src` directory, create a local Neptune
client, and then run your queries. For example:

```python3
from utils.aws import get_neptune_client

neptune_client = get_neptune_client(True)

query = """
MATCH (n) RETURN count(*)
"""
result = neptune_client.run_open_cypher_query(query)
print(result)
```

## Running with local Elasticsearch

To run Elasticsearch locally, you can use `elasticsearch.docker-compose.yml` to start a local Elasticsearch instance.

`docker compose -f elasticsearch.docker-compose.yml up`

This will start Elasticsearch on `localhost:9200`, and Kibana on `localhost:5601`. Lambda functions can be configured to
use this Elasticsearch instance by setting the relevant environment variables when starting the Lambda function locally.

### AWS Graph Notebook

Additionally, it is possible to connect to the cluster
using [AWS graph notebook](https://github.com/aws/graph-notebook). The most straightforward option to do this locally is
using [JupyterLab](https://jupyter.org/). To make this work, you need to set this up in a different virtual environment
from the one in this project (this is because `graph-notebook` currently requires Python 3.9.x-3.10.14). Once you have
created a new environment with the correct Python version, install the following:

```
# install graph-notebook
pip install graph-notebook

# install jupyterlab
pip install "jupyterlab>=3,<4"

# aws graph-notebook comes with some example notebooks, this creates copies in the notebooks directory
python -m graph_notebook.notebooks.install --destination notebooks
```

Run the following command to open JupyterLab in your browser:

`python -m graph_notebook.start_jupyterlab --jupyter-dir notebooks`

To connect to the catalogue graph, add the following configuration into your Jupyter notebook:

```
%%graph_notebook_config
{
    "host": <value stored in AWS Secrets Manager under 'catalogue-graph/neptune-cluster-endpoint' in the platform account>,
    "neptune_service": "neptune-db",
    "port": 8182,
    "ssl": true,
    "proxy_port": 443,
    "proxy_host": "catalogue-graph.wellcomecollection.org",
    "auth_mode": "IAM",
    "aws_region": "eu-west-1",
    "load_from_s3_arn": ""
}  
```

To communicate with the cluster, the AWS_PROFILE environment variable first needs to be set like this in the same
Jupyter notebook:

```
%env AWS_PROFILE=platform-developer
```

You can find an [example notebook](notebooks/graph_exploration.ipynb) in the notebooks folder with openCypher queries to
explore the catalogue graph.
