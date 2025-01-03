# Batcher

## Running Locally

First, build it (from the base of the repo)
`sbt "project relation_embedder" ";stage"`

### As a JAR

This stage needs read permissions on the upstream elasticsearch index.  
`scripts/cli.py` fetches the appropriate secrets and configures the environment
to provide that access.

You can pipe a bunch of Batches to cli.py, providing the pipeline date, thus:

`cat scripts/batches.txt | AWS_PROFILE=my-profile python scripts/cli.py 2024-11-18`

You must provide an accessible profile with access to the index secrets.

### As a Lambda

You can run the Lambda version locally thus:

`./scripts/run_local.sh <PIPELINE_DATE>`

You can now post JSON SQS messages to it. Because SQS-fed-by-SNS is so awkwardly verbose,
a convenience script will fill out the boilerplate for you. As with CLIMain, you can pipe some
paths to it.

`cat scripts/batches.txt | python scripts/post_to_rie.py`
