# Batcher

The Batcher optimises the input to the Relation Embedder by grouping together Works that belong to the same Archive or Collection tree.   
As a result, the Relation Embedder can build larger parts of the tree from any given SQS event, reducing the read/write load on the denormalised index.

The batcher publishes List(Batches) where all the selectors have the same rootpath
```json
// Batch
{
    "rootPath":"(OCoLC)1567486",
    "selectors":[
        {"path":"(OCoLC)1567486/Vol_38_no_112_Jul_1832_18200888","type":"Descendents"},
        {"path":"(OCoLC)1567486/Vol_79_no_194_Jan_1853_18471078","type":"Descendents"},
        {"path":"(OCoLC)1567486/Vol_38_no_113_Oct_1832_18200898","type":"Descendents"},
        {"path":"(OCoLC)1567486","type":"Node"},
        {"path":"(OCoLC)1567486)","type":"Children"},
    ]
}
```

## Running Locally

### As a Lambda

You can run the Lambda version locally from the repository root thus:

`./scripts/run_local.sh relation_embedder/batcher [<PIPELINE_DATE>] [--skip-build]`

You can now post JSON SQS messages to it. Because SQS-fed-by-SNS is so awkwardly verbose,
a convenience script will fill out the boilerplate for you. As with CLIMain, you can pipe some
paths to it, from scripts folder in this project directory:

`cat scripts/paths.txt | python scripts/post_to_rie.py`

### As a JAR

You can pipe a bunch of paths to CLIMain, thus:

`cat scripts/paths.txt | java weco.pipeline.batcher.CLIMain`

Or, if you'd rather not have to set the classpath, SBT will have generated a script you can call,

`cat scripts/paths.txt | target/universal/stage/bin/cli-main`
