"""
Pipeline steps that process data and monitor progress.

Core Processing Steps:
- trigger: Initiates the pipeline and manages job distribution
- loader: Loads concept data from Neptune into working storage
- indexer: Indexes processed concepts into Elasticsearch
- deletions: Handles deletion of outdated concepts

Monitoring Steps:
- trigger_monitor: Validates trigger step output and safety checks
- indexer_monitor: Tracks indexing success counts and builds reports
- reporter: Generates final pipeline reports and sends to Slack
"""
