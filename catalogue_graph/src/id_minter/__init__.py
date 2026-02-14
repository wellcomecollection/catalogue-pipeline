"""
ID Minter service for assigning canonical identifiers to catalogue works.

This module is a Python port of the Scala id_minter service. It:
1. Receives a list of source identifiers and a job ID
2. Fetches the corresponding records from an upstream Elasticsearch index
3. Mints canonical identifiers via an RDS-backed identifier table
4. Stores the minted records in a downstream Elasticsearch index

Structure:
- config.py: Runtime configuration (RDS, Elasticsearch, downstream targets)
- database.py: Database connection and migration management
- models/: Pydantic event models (StepFunctionMintingRequest/Response)
- steps/: Lambda handler and CLI entry point
"""
