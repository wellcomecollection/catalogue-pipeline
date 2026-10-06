# Pipeline Elasticsearch API Key

Represents the API Key that grants permission to a pipeline step. This module generates it and stores it
in AWS Secrets Manager.

A pipeline step has upstream indices it can read from, and downstream indices it can write to.

The read side grants `read` by default; `read_privileges` widens that (for example adding `view_index_metadata`),
and a non-empty `cluster_privileges` adds a `cluster` entry to the read role descriptor, for monitoring keys.

Optionally, this module can also make the key available to the Catalogue account.