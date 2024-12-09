# Batcher

## Running Locally

First, build it (from the base of the repo)
`sbt "project relation_embedder" ";stage"`

### As a JAR

Set the environment variable PIPELINE_DATE, e.g.

`export PIPELINE_DATE=2024-11-18`

You can pipe a bunch of Batches to CLIMain, thus:

`cat scripts/batches.txt | java weco.pipeline.relation_embedder.CLIMain`

Or, if you'd rather not have to set the classpath, SBT will have generated a script you can call,

`cat scripts/batches.txt | target/universal/stage/bin/cli-main`

### As a Lambda

You can run the Lambda version locally thus:

Build the appropriate Docker

`docker build --target lambda_rie -t lambda_relation_embedder .`

Run it with the port available

`docker run -p 9000:8080 lambda_relation_embedder`

You can now post JSON SQS messages to it. Because SQS-fed-by-SNS is so awkwardly verbose,
a convenience script will fill out the boilerplate for you. As with CLIMain, you can pipe some
paths to it.

`cat scripts/batches.txt | python scripts/post_to_rie.py`
