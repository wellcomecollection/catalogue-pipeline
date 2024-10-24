# Batcher

## Running Locally

First, build it (from the base of the repo)
`sbt "project batcher" ";stage"`

### As a JAR

You can pipe a bunch of paths to CLIMain, thus:

`cat src/test/resources/paths.txt | java weco.pipeline.batcher.CLIMain`

### As a Lambda

You can run the Lambda version locally thus:

Build the appropriate Docker

`docker build --target lambda_rie -t lambda_batcher .`

Run it with the port available

`docker run -p 9000:8080 lambda_batcher`

You can now post JSON SQS messages to it, e.g.

```commandline
curl -X POST http://localhost:9000/2015-03-31/functions/function/invocations -d '{
  "Records": [
    {
      "messageId": "19dd0b57-b21e-4ac1-bd88-01bbb068cb78",
      "receiptHandle": "MessageReceiptHandle",
      "body": "\"A/B/C\"",
      "attributes": {
        "ApproximateReceiveCount": "1",
        "SentTimestamp": "1523232000000",
        "SenderId": "123456789012",
        "ApproximateFirstReceiveTimestamp": "1523232000001"
      },
      "messageAttributes": {},
      "md5OfBody": "{{{md5_of_body}}}",
      "eventSource": "aws:sqs",
      "eventSourceARN": "arn:aws:sqs:us-east-1:123456789012:MyQueue",
      "awsRegion": "us-east-1"
    },{
      "messageId": "19dd0b57-b21e-4ac1-bd88-01bbb068cb78",
      "receiptHandle": "MessageReceiptHandle",
      "body": "\"A/C/D\"",
      "attributes": {
        "ApproximateReceiveCount": "1",
        "SentTimestamp": "1523232000000",
        "SenderId": "123456789012",
        "ApproximateFirstReceiveTimestamp": "1523232000001"
      },
      "messageAttributes": {},
      "md5OfBody": "{{{md5_of_body}}}",
      "eventSource": "aws:sqs",
      "eventSourceARN": "arn:aws:sqs:us-east-1:123456789012:MyQueue",
      "awsRegion": "us-east-1"
    }

  ]
}'

```
