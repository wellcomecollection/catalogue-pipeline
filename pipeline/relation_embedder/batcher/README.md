# Batcher

## Running Locally

### As a Lambda

You can run the Lambda version locally from the repository root thus:

`./scripts/run_local.sh <PROJECT_ID> [<PIPELINE_DATE>] [--skip-build]`

You can now post JSON SQS messages to it. Because SQS-fed-by-SNS is so awkwardly verbose,
a convenience script will fill out the boilerplate for you. As with CLIMain, you can pipe some
paths to it, from scripts folder in this project directory:

`cat scripts/paths.txt | python scripts/post_to_rie.py`

### As a JAR

You can pipe a bunch of paths to CLIMain, thus:

`cat scripts/paths.txt | java weco.pipeline.batcher.CLIMain`

Or, if you'd rather not have to set the classpath, SBT will have generated a script you can call,

`cat scripts/paths.txt | target/universal/stage/bin/cli-main`
