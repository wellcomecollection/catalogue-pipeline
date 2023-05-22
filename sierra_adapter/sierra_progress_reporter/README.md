# sierra_progress_reporter

We harvest records from Sierra every 15 minutes, grabbing the last 30 minutes of updates.
When the Sierra reader is done, it writes a marker into S3 to say "I got these updates":

```
s3://wc-platform-adapters-sierra
  └── windows_bibs_complete
        ├── 2020-03-16T00-00-00__2020-03-16T00-30-00
        ├── 2020-03-16T00-15-00__2020-03-16T00-45-00
        └── 2020-03-16T00-30-00__2020-03-16T01-00-00
```

The Sierra progress reporter goes through the objects in this bucket, and consolidates overlapping markers them into a single marker, for example:

```
s3://wc-platform-adapters-sierra
  └── windows_bibs_complete
        └── 2020-03-16T00-00-00__2020-03-16T01-00-00
```

This gives us a crude reporting mechanism to see whether we missed any updates.

If it spots a gap in the windows, it sends a message to a Slack channel.

## Running locally

You can test the Lambda by running it locally.

Use the `run_lambda.sh` script, for example:

```console
$ AWS_PROFILE=platform-dev bash run_lambda.sh
```
