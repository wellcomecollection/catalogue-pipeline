# Batcher

## Running Locally

First, build it (from the base of the repo)
`sbt "project batcher" ";stage"`

### As a JAR

You can pipe a bunch of paths to CLIMain, thus:

`cat scripts/paths.txt | java weco.pipeline.batcher.CLIMain`

Or, if you'd rather not have to set the classpath, SBT will have generated a script you can call,

`cat scripts/paths.txt | target/universal/stage/bin/cli-main`

### As a Lambda

You can run the Lambda version locally thus:

Build the appropriate Docker

`docker build --target lambda_rie -t lambda_batcher .`

Run it with the port available

`docker run -p 9000:8080 lambda_batcher`

You can now post JSON SQS messages to it. Because SQS-fed-by-SNS is so awkwardly verbose,
a convenience script will fill out the boilerplate for you. As with CLIMain, you can pipe some
paths to it.

`cat scripts/paths.txt | python scripts/post_to_rie.py`
