# catalogue\snapshots

Services for creating, recording and reporting on Catalogue API snapshots.

## Overview

Contains:

- snapshot_generator: inspect the production Catalogue Elasticsearch index and produce a snapshot of works using their display model.
- snapshot_scheduler: a lambda to schedule snapshots (triggers the snapshot generator). Triggered daily by CloudWatch events.
- snapshot_recorder: notified by SNS -> SQS from the snapshot_generator about completed snapshots, recording metadata in a reporting cluster Elasticsearch index.
- snapshot_reporter: reports on daily snapshots in team Slack, will provide notification on failure. Triggered daily by CloudWatch events.

## Architecture

![](architecture.png)

## Deployment

### Steps

The snapshot_generator is deployed alongside the Catalogue API (as specified in `.wellcome_project`), in order that it remains in sync with the current display model. You will need to use the `weco-deploy` tool.

```
weco-deploy --project-id catalogue_api deploy --environment-id prod
```

The snapshot_recorder, snapshot_scheduler & snapshot_reporter lambdas should be published as required using the relevant `make` commands. A terraform apply is then required to update those lambdas in AWS.

For example:

```
make snapshot_reporter-publish
cd snapshots/terraform
terraform apply
```
