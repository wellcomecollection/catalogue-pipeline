# Ingestor Subsystem

## Overview

The ingestor subsystem processes catalogue concepts from Neptune graph database to Elasticsearch search index. It consists of multiple Lambda functions that work together to extract, transform, and load concept data while providing comprehensive monitoring and reporting.

## Structure

```
src/ingestor/
├── __init__.py                    # Main module documentation
├── run_local.py                   # Local testing and development script
├── models/                        # Ingestor-specific data models
│   ├── __init__.py
│   ├── concept.py                 # Core concept models
│   ├── indexable.py               # Base indexable interfaces
│   ├── indexable_concept.py       # Elasticsearch-ready concept models
│   ├── related_concepts.py        # Related concept relationship models
│   └── display/                   # Display-specific models
│       └── identifier.py
├── transformers/                  # Elasticsearch transformation logic
│   ├── __init__.py
│   └── concepts_transformer.py    # Transforms Neptune concepts to ES format
└── steps/                         # All pipeline processing and monitoring steps
    ├── __init__.py
    ├── ingestor_trigger.py        # Queries Neptune, generates shard ranges
    ├── ingestor_loader.py         # Loads concepts from Neptune to S3
    ├── ingestor_indexer.py        # Indexes concepts from S3 to Elasticsearch
    ├── ingestor_deletions.py      # Removes deleted concepts from ES
    ├── ingestor_trigger_monitor.py    # Validates trigger output
   ├── ingestor_loader_monitor.py     # (Removed) legacy loader monitor step  
    ├── ingestor_indexer_monitor.py    # Tracks indexing success
    └── ingestor_reporter.py           # Generates final reports
```

## Architecture

The ingestor is designed as a serverless pipeline with each step implemented as an AWS Lambda function. All steps are packaged together in a single deployment unit while maintaining clear separation of responsibilities.

The ingestor module is self-contained with its own models and transformers:
- **Models** (`models/`) - Pydantic data models specific to the ingestor pipeline
- **Transformers** (`transformers/`) - Logic for transforming Neptune concepts to Elasticsearch format
- **Steps** (`steps/`) - Individual pipeline stages implemented as Lambda functions

Shared types (ConceptType, ConceptSource, WorkType) are imported from `shared.types` to maintain consistency across the codebase.

## Pipeline Flow

1. **Trigger** (`steps/ingestor_trigger.py`) - Queries Neptune for concept counts and creates shard ranges
2. **Trigger Monitor** (`steps/ingestor_trigger_monitor.py`) - Validates trigger output and safety checks
3. **Loader** (`steps/ingestor_loader.py`) - Loads concept data from Neptune to S3 in parallel shards
4. **Indexer** (`steps/ingestor_indexer.py`) - Indexes concept data from S3 to Elasticsearch using models and transformers
5. **Indexer Monitor** (`steps/ingestor_indexer_monitor.py`) - Tracks indexing success and builds reports
6. **Deletions** (`steps/ingestor_deletions.py`) - Removes deleted concepts from Elasticsearch
7. **Reporter** (`steps/ingestor_reporter.py`) - Generates final pipeline reports and sends to Slack

> **Note:** Loader monitoring is now handled within the loader step itself; the standalone monitor Lambda has been retired.

The pipeline uses ingestor-specific models (`models/indexable_concept.py`) and transformers (`transformers/concepts_transformer.py`) to convert Neptune graph data into Elasticsearch-ready documents.

## Development

### Local Testing
Use `run_local.py` to execute the entire pipeline locally for development and testing:

```bash
python src/ingestor/run_local.py --pipeline-date 2021-07-01 --index-date 2021-07-01 --job-id 123
```

#### Testing Against Local Elasticsearch
For development and testing, you can run the ingestor against a local Elasticsearch instance by:

1. Starting the local Elasticsearch service with docker-compose:
   ```bash
   docker-compose -f elasticsearch.docker-compose.yml up -d
   ```

2. Running the ingestor without specifying a pipeline date (this will target the local ES):
   ```bash
   python src/ingestor/run_local.py 
   ```

### Lambda Functions
Each step in the `steps/` directory corresponds to a deployed AWS Lambda function with handlers configured as:
- `ingestor.steps.ingestor_trigger.lambda_handler`
- `ingestor.steps.ingestor_loader.lambda_handler`
- `ingestor.steps.ingestor_indexer.lambda_handler`
- etc.

