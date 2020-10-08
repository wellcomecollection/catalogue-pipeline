# snapshots

Services for creating, recording and reporting on Catalogue API snapshots.

## Overview

Contains:

- `snapshot_scheduler`: a lambda triggered by CloudWatch, publishes messages to SNS describing required snapshots.
- `snapshot_generator`: an ECS service which polls SQS, produces a snapshot of works from the Catalogue ES index using their display model.
- `snapshot_recorder`: a lambda triggered by SNS, recording metadata in a reporting cluster Elasticsearch index.
- `snapshot_reporter`: a lambda triggered by CloudWatch, reports on daily snapshots in team Slack, will provide notification on failure.

## Architecture

![Architecture diagram for catalogue snapshots](architecture.png)

## Deployment

### Steps

The snapshot_generator deploys alongside the Catalogue API (as specified in [`.wellcome_project`](../.wellcome_project)), in order that it remains in sync with the current display model. You will need to use the [`weco-deploy`](https://github.com/wellcomecollection/weco-deploy) tool.

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
