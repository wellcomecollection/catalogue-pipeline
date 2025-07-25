"""
Ingestor subsystem for indexing catalogue concepts from Neptune to Elasticsearch.

This module contains the complete ingestor pipeline that:
1. Triggers ingestion by querying Neptune for concept counts
2. Loads concepts from Neptune to S3 in shards
3. Indexes concepts from S3 to Elasticsearch
4. Monitors each step and reports on the pipeline status
5. Handles deletions of removed concepts

Structure:
- steps/: All pipeline steps (processing + monitoring) as Lambda functions
- run_local.py: Script to run the entire pipeline locally for testing

The pipeline is orchestrated via AWS Step Functions and runs as multiple Lambda functions.
"""
