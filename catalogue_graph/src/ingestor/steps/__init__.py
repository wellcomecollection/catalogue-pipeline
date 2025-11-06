"""
Pipeline steps that process data and monitor progress.

Core Processing Steps:
- trigger: Initiates the pipeline and manages job distribution
- loader: Loads concept data from Neptune into working storage and emits pipeline reports
- indexer: Indexes processed concepts into Elasticsearch and emits pipeline reports
- deletions: Handles deletion of outdated concepts
"""
