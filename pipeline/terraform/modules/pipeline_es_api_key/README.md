# Pipeline Elasticsearch API Key

Represents the API Key that grants permission to a pipeline step. This module generates it and stores it
in AWS Secrets Manager.

A pipeline step has upstream indices it can read from, and downstream indices it can write to.

Optionally, this module can also make the key available to the Catalogue account.