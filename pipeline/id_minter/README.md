# id_minter

Our source catalogues all use their own identifier schemes, which may look different or have overlapping values.
For example, when I refer to `b14561980`, do I mean the METS file or the Sierra record?

So that we can talk about things consistently, we have our own "canonical" IDs, like `vg7ev4w3` or `m3zwbfrd`.
These are distinct from anything in our catalogues, and have a 1:1 relationship with source identifiers.

The ID minter creates these canonical IDs, and adds them to works.

## How we choose canonical IDs

Canonical IDs are chosen for the following properties:

*   They should be **short**, ideally something that fits on a single post-it note
*   They should be **unambiguous**, so they don't use characters which can look ambiguous (e.g. letter `O` and numeral `0`)
*   They should be **URL safe**

There's a 1:1 mapping between canonical IDs and source identifiers.
This mapping is recorded in an RDS database.

## How it works

The ID minter gets Works as JSON objects, and within them are `sourceIdentifier` objects, e.g.

```
{
  ...,
  "state" : {
    "sourceIdentifier" : {
      "identifierType" : {
        "id" : "calm-record-id"
      },
      "ontologyType" : "Work",
      "value" : "4e9e4a90-4f1a-42c0-870b-054da38a8089"
    },
    ...
  }
}
```

A SourceIdentifier object has three fields: identifier type, value, and ontology type.

The latter is so we can identify different record types from the same source catalogue, e.g. `1000001` from Sierra could be a bib or an item.

The ID minter looks for all the SourceIdentifier objects within a blob of JSON, then looks up the corresponding canonical IDs in the RDS database.
If there isn't a canonical ID, it creates one and stores it in the database.

Then, it adds a `canonicalId` field with the ID to the JSON:

```diff
 {
   ...,
   "state" : {
     "sourceIdentifier" : {
       "identifierType" : {
         "id" : "calm-record-id"
       },
       "ontologyType" : "Work",
       "value" : "4e9e4a90-4f1a-42c0-870b-054da38a8089"
     },
+    "canonicalId" : "kcvqcsng",
     ...
   },
 }
```

It updates all SourceIdentifier objects in the JSON, even if they're deeply nested; e.g. it also adds canonical IDs to items and subjects.

## Running locally

### SQS Lambda Interface (Default)

You can run the Lambda version locally from the repository root thus:

`./scripts/run_local.sh <PROJECT_ID> [<PIPELINE_DATE>] [--skip-build]`

You can now post JSON SQS messages to it. Because SQS-fed-by-SNS is so awkwardly verbose,
a convenience script will fill out the boilerplate for you. As with CLIMain, you can pipe some
paths to it, from scripts folder in this project directory:

`cat scripts/source_ids.txt | python scripts/post_to_rie.py`

### Step Function Lambda Interface

The ID minter also provides a Step Function interface for direct invocation within AWS Step Functions.
This interface processes source identifiers directly without SQS messaging.

To run the Step Function Lambda locally:

```bash
# Build and run the Step Function Lambda
docker compose -f local.docker-compose.yml build stepfunction-lambda
docker compose -f local.docker-compose.yml run --rm --service-ports stepfunction-lambda
```

The Step Function Lambda will be available at `http://localhost:9001/2015-03-31/functions/function/invocations`

Example request payload:
```json
{
  "sourceIdentifiers": ["sierra-123456", "miro-789012"],
  "jobId": "step-function-job-001"
}
```

Example response:
```json
{
  "successes": ["sierra-123456", "miro-789012"],
  "failures": [],
  "jobId": "step-function-job-001"
}
```

For more details, see [Step Function Implementation Guide](./docs/stepfunction-guide.md).

## Connecting to the ID minter database

If you need to connect to the ID minter database, there are [some notes on how to do so](./connect_to_the_database.md).
